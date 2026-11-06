# AutoScaleLab - AutoScaler (Java Spring Boot)

L'AutoScaler surveille le CPU du service Docker Swarm `autoscalelab_app` dans
Prometheus et ajuste automatiquement son nombre de réplicas.

- **CPU > 75 %** → +1 réplica (SCALE UP)
- **CPU < 30 %** → −1 réplica (SCALE DOWN)
- **Entre les deux** → aucune action
- Toujours entre **1 et 5 réplicas**, avec un **cooldown de 60 s** entre deux changements.

Par défaut, il fonctionne en **mode simulation** : aucune commande Docker n'est exécutée.

## Architecture

```
            toutes les 15 s
┌──────────────────────────────┐
│     AutoscalerScheduler      │  boucle @Scheduled (fixedDelay)
└──────────────┬───────────────┘
               ▼
┌──────────────────────────────┐   PromQL    ┌────────────┐   scrape   ┌──────────────────┐
│        ScalingService        │────────────▶│ Prometheus │◀───────────│ docker_exporter  │
│ 1. lire le CPU               │ MetricsSvc  │   :9090    │            │      :8000       │
│ 2. lire les réplicas réels   │             └────────────┘            └──────────────────┘
│ 3. décider (+1 / −1 / rien)  │
│ 4. vérifier le cooldown      │   SwarmClient
│ 5. appliquer                 │────────────────┐
└──────────────────────────────┘                ▼
                               ┌─────────────────────────────────────┐
                               │ SimulatedSwarmClient (par défaut)    │ réplicas en mémoire
                               │ DockerSwarmClient (profil "swarm")   │ docker service inspect / scale
                               └─────────────────────────────────────┘
```

| Package | Rôle |
|---|---|
| `scheduler` | `AutoscalerScheduler` : lance un cycle toutes les 15 s |
| `service` | `ScalingService` (décision), `MetricsService` (Prometheus), `ScalingDecision` (résultat d'un cycle) |
| `client` | `SwarmClient` (interface), `SimulatedSwarmClient`, `DockerSwarmClient`, `CommandRunner` (commande système avec délai maximum) |
| `config` | `AutoscalerProperties` (paramètres vérifiés au démarrage), activation du scheduler, horloge |
| `controller` | `AutoscalerController` : API REST |

### Règles de sécurité

- **Métrique indisponible ou invalide** (Prometheus arrêté, résultat vide, `NaN`, valeur négative,
  plusieurs séries) → `NO ACTION`, aucun changement.
- **Réplicas illisibles** (Docker arrêté, Swarm inactif, service absent) → `NO ACTION`.
- **Échec du scaling** → signalé, et le cooldown ne démarre pas.
- **Réplicas hors limites** (ex. 8 mis à la main) → ramenés dans la plage, en respectant le cooldown.
- Les routes **GET ne modifient jamais** les réplicas.
- Une configuration incohérente (ex. `cpu-low` ≥ `cpu-high`, `min` > `max`) **empêche le démarrage**.

## Paramètres (`src/main/resources/application.properties`)

| Propriété | Défaut | Description |
|---|---|---|
| `server.port` | `8085` | Port de l'API (8080 est réservé à Nginx / Swarm) |
| `prometheus.url` | `http://localhost:9090` | Adresse de Prometheus |
| `prometheus.connect-timeout` / `read-timeout` | `2s` / `5s` | Délais d'appel à Prometheus |
| `autoscaler.service-name` | `autoscalelab_app` | Service Swarm surveillé (stack `autoscalelab` + service `app`) |
| `autoscaler.cpu-high` / `cpu-low` | `75` / `30` | Seuils en % (strictement supérieur / inférieur) |
| `autoscaler.min-replicas` / `max-replicas` | `1` / `5` | Limites |
| `autoscaler.cooldown` | `60s` | Délai minimum entre deux changements |
| `autoscaler.check-interval` | `15s` | Intervalle entre deux cycles |
| `autoscaler.initial-delay` | `10s` | Délai avant le premier cycle |
| `autoscaler.scheduler.enabled` | `true` | Active la boucle automatique |
| `autoscaler.cpu-query` | `avg(docker_container_cpu_percent{service="${autoscaler.service-name}"})` | Requête PromQL, doit renvoyer **une seule série** |
| `autoscaler.swarm.simulation` | `true` | `true` = simulation, `false` = Docker réel (profil `swarm`) |
| `autoscaler.docker.command` | `docker` | Commande Docker |
| `autoscaler.docker.timeout-seconds` | `10` | Délai maximum d'une commande Docker |
| `autoscaler.initial-replicas` | `1` | Réplicas de départ du mode simulation uniquement |

Tous les paramètres peuvent être surchargés au lancement, par exemple `--autoscaler.cooldown=20s`.

### Métrique CPU attendue

La requête par défaut utilise la métrique publiée par l'exporteur Docker de l'équipe monitoring
(`monitoring/docker_exporter.py`). Elle doit fournir **une ligne par conteneur du service**, avec
l'étiquette `service` :

```
docker_container_cpu_percent{service="autoscalelab_app",container="autoscalelab_app.1.abc"} 82.4
docker_container_cpu_percent{service="autoscalelab_app",container="autoscalelab_app.2.def"} 64.0
```

`avg(...)` calcule alors la moyenne de tous les réplicas. Si l'exporteur est arrêté, le
résultat est vide et l'AutoScaler reste en `NO ACTION`.

> Avec `docker stats`, 100 % correspond à un cœur. Une limite `cpus: "1.0"` par réplica
> dans `swarm/docker-stack.yml` garde la valeur entre 0 et 100 % et donne leur sens aux seuils.

## API REST

| Méthode | Route | Rôle |
|---|---|---|
| `GET` | `/api/status` | Mode, configuration, dernière décision (lecture seule) |
| `GET` | `/api/scaling` | Dernière décision (lecture seule) |
| `POST` | `/api/scaling/check` | Lance un cycle immédiatement (respecte seuils, limites et cooldown) |
| `GET` | `/api/prometheus` | Test de connexion à Prometheus (`503` s'il est injoignable) |

Exemple de décision :

```json
{
  "timestamp": "2026-11-06T11:34:42Z",
  "action": "SCALE_UP",
  "cpu": 90.0,
  "replicasBefore": 1,
  "replicasAfter": 2,
  "reason": "cpu=90 % > 75 %, réplicas 1 → 2",
  "error": false
}
```

## Lancement

Prérequis : Java 21 ou plus. Commandes PowerShell, depuis le dossier `autoscaler/`.

### Mode simulation (par défaut)

```powershell
.\mvnw.cmd spring-boot:run
```

Aucune commande Docker n'est exécutée et les réplicas sont simulés en mémoire. Le log affiche
`Mode SIMULATION actif`, puis une décision toutes les 15 s.

Pour tester sans l'exporteur, on peut demander à Prometheus une valeur fixe :

```powershell
# CPU simulé à 90 % : SCALE UP jusqu'à 5
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--autoscaler.cpu-query=vector(90)"

# CPU simulé à 10 %, en partant de 5 réplicas : SCALE DOWN jusqu'à 1
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--autoscaler.cpu-query=vector(10) --autoscaler.initial-replicas=5"
```

### Mode réel (profil `swarm`)

Prérequis :

1. Docker Desktop démarré.
2. Swarm initialisé : `bash swarm/init-swarm.sh`
3. Stack déployée : `bash swarm/deploy-stack.sh`. Le service `autoscalelab_app` doit exister.
4. Exporteur et Prometheus démarrés (voir `monitoring/README.md`).
5. L'AutoScaler tourne sur le nœud manager Swarm, avec la commande `docker` dans le PATH.

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=swarm"
```

Configuration de la démonstration (3 réplicas maximum, délai Docker allongé pour un PC chargé) :

```powershell
.\mvnw.cmd -B spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=swarm --autoscaler.max-replicas=3 --autoscaler.docker.timeout-seconds=20"
```

Le profil `swarm` (`application-swarm.properties`) ne change qu'un paramètre :
`autoscaler.swarm.simulation=false`. Sans ce profil, l'AutoScaler reste en simulation.

Commandes exécutées en mode réel :

```
docker service inspect autoscalelab_app --format {{.Spec.Mode.Replicated.Replicas}}
docker service scale autoscalelab_app=N --detach
```

> Relancer `deploy-stack.sh` remet le service à 1 réplica. L'AutoScaler le voit au cycle suivant,
> car il lit le nombre réel de réplicas à chaque cycle.

### Vérifier

```powershell
Invoke-RestMethod http://localhost:8085/api/status
Invoke-RestMethod http://localhost:8085/api/scaling
Invoke-RestMethod -Method Post http://localhost:8085/api/scaling/check
```

Exemples de logs :

```
SCALE UP   : cpu=82,4 % > 75 %, réplicas 2 → 3
NO ACTION  : cooldown actif (encore 45 s), changement ignoré : cpu=90 % > 75 %, réplicas 3 → 4
NO ACTION  : cpu=90 % > 75 % mais limite max atteinte (5 réplicas)
SCALE DOWN : cpu=12 % < 30 %, réplicas 3 → 2
NO ACTION  : métrique CPU indisponible (Prometheus injoignable ...) – aucun changement
```

## Tests

```powershell
.\mvnw.cmd test
```

72 tests. Ils ne lancent aucune commande Docker et n'utilisent pas de Prometheus réel : ils
s'appuient sur de faux clients, une horloge manipulable, et la boucle automatique est
désactivée pendant les tests.

## Ports du projet

| Composant | Port |
|---|---|
| Application Nginx (Swarm) | 8080 |
| AutoScaler | 8085 |
| Exporteur Docker | 8000 |
| Prometheus | 9090 |
