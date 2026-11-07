# AutoScaleLab — Monitoring et Autoscaling avec Docker Swarm

## 1. Présentation

AutoScaleLab est un projet DevOps qui **ajuste automatiquement le nombre de
réplicas** d'une application Docker Swarm en fonction de leur charge CPU réelle.

Une application Nginx tourne dans Docker Swarm. Un exporteur mesure le CPU de
chaque réplica, Prometheus stocke ces mesures, et un AutoScaler Spring Boot
décide toutes les 15 secondes d'ajouter ou de retirer un réplica. k6 génère la
charge qui déclenche le scaling.

## 2. Architecture

```
            k6 (charge HTTP)
                  │
                  ▼  http://localhost:8080
 ┌─────────────────────────────────────┐
 │ Docker Swarm : service              │◀──────────── docker service scale ────────┐
 │ autoscalelab_app (Nginx, 1 à 3      │                                           │
 │ réplicas, 1 cœur max chacun)        │                                           │
 └──────────────────┬──────────────────┘                                           │
                    │ docker stats                                                 │
                    ▼                                                              │
 ┌─────────────────────────────────────┐  scrape 15 s  ┌──────────────┐  PromQL   ┌┴──────────────────┐
 │ monitoring/docker_exporter.py :8000 │◀──────────────│ Prometheus   │◀──────────│ AutoScaler Java   │
 │ CPU / mémoire de chaque réplica     │               │ :9090        │           │ :8085, cycle 15 s │
 └─────────────────────────────────────┘               └──────────────┘           └───────────────────┘
```

Règles de l'AutoScaler (`autoscaler/README.md`) :

| Condition | Action |
|---|---|
| CPU moyen des réplicas **> 75 %** | +1 réplica (SCALE UP) |
| CPU moyen **< 30 %** | −1 réplica (SCALE DOWN) |
| Entre 30 et 75 % | Aucune action |
| Limites | 1 réplica minimum, 3 pour la démonstration (5 par défaut) |
| Cooldown | 60 s minimum entre deux changements |
| Métrique ou Docker indisponible | Aucune action (sécurité) |

Le CPU est mesuré en % d'un cœur, et chaque réplica est limité à 1 cœur :
100 % = réplica saturé. Les seuils 75 % / 30 % sont donc des pourcentages de
la capacité d'un réplica. L'écart entre les deux seuils (rapport 2,5) évite
les oscillations : après un SCALE UP de 1 à 2 réplicas, le CPU moyen est
environ divisé par 2 et ne repasse pas sous 30 %.

## 3. Composants et équipe

| Dossier | Contenu | Responsable |
|---|---|---|
| `autoscaler/` | AutoScaler Spring Boot (Java 21) : décision, cooldown, API REST, client Docker Swarm | Membre 1 — AutoScaler |
| `swarm/` | Stack Swarm (`docker-stack.yml`), scripts d'initialisation et de déploiement | Membre 2 — Docker Swarm |
| `monitoring/` | Exporteur Python, configuration Prometheus | Membre 3 — Monitoring et Load Testing |
| `load-testing/` | Scénarios k6 | Membre 3 |

Chaque dossier contient son propre README détaillé.

## 4. Ports

| Port | Service |
|---|---|
| 8080 | Nginx (service Swarm `autoscalelab_app`) — cible de k6 |
| 8085 | API de l'AutoScaler |
| 8000 | Exporteur Docker (`/metrics`) |
| 9090 | Prometheus |
| 8082 | cAdvisor (facultatif, non utilisé par l'AutoScaler) |

## 5. Lancer la démonstration (Windows, PowerShell)

Prérequis : Docker Desktop, Java 21 ou plus, Python 3.10 ou plus, k6.
Sur un PC de 8 Go, fermer les applications lourdes (navigateur, IDE) et
désactiver la mise en veille pendant la démonstration.

Toutes les commandes partent de la racine du projet.

**1. Swarm et application** (Git Bash, une seule fois)

```bash
bash swarm/init-swarm.sh
bash swarm/deploy-stack.sh
```

**2. Exporteur** (terminal 1)

```powershell
python -u monitoring\docker_exporter.py
```

**3. Prometheus**

```powershell
docker run -d --name autoscalelab-prometheus -p 9090:9090 -v "${PWD}\monitoring\prometheus.yml:/etc/prometheus/prometheus.yml:ro" prom/prometheus
```

Vérifier sur http://localhost:9090 (*Status > Target health*) que
`docker-exporter` est `UP`.

**4. AutoScaler en mode réel, 3 réplicas maximum** (terminal 2)

```powershell
cd autoscaler
.\mvnw.cmd -B spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=swarm --autoscaler.max-replicas=3 --autoscaler.docker.timeout-seconds=20"
```

Sans le profil `swarm`, l'AutoScaler reste en **mode simulation** (aucune
commande Docker). Vérifier : `Invoke-RestMethod http://localhost:8085/api/status`
doit indiquer `mode : DOCKER` et `maxReplicas : 3`.

**5. Charge** (terminal 3)

```powershell
k6 run load-testing/ramp-test.js
```

**6. Observer**

```powershell
docker service ls                                   # 1/1 → 2/2 → 3/3 → 2/2 → 1/1
Invoke-RestMethod http://localhost:8085/api/scaling # dernière décision
```

Ou dans Prometheus : `avg(docker_container_cpu_percent{service="autoscalelab_app"})`.

Arrêt : Ctrl+C dans les terminaux de l'exporteur et de l'AutoScaler.

## 6. Tests

| Partie | Commande | Résultat |
|---|---|---|
| AutoScaler (JUnit, sans Docker) | `cd autoscaler` puis `.\mvnw.cmd test` | 72 tests, 0 échec |
| Exporteur (unittest, sans Docker) | `python -m unittest discover -s monitoring -v` | 12 tests, 0 échec |
| Configuration Prometheus | `promtool check config` (voir `monitoring/README.md`) | SUCCESS |
| Scripts k6 | `k6 inspect load-testing/ramp-test.js` | OK |

Aucun test automatique n'exécute de commande Docker réelle.

## 7. Résultats de la démonstration réelle (6 novembre 2026)

Conditions : PC Windows 11, 8 cœurs, 8 Go de RAM, Docker Desktop, un nœud
Swarm. AutoScaler en mode réel (profil `swarm`), 3 réplicas maximum, seuils
75 % / 30 % et cooldown de 60 s **par défaut, sans aucun ajustement**.
Charge : `k6 run load-testing/ramp-test.js` (jusqu'à 200 VUs, 7 min 30 s).

**Décisions réelles de l'AutoScaler** (logs Java, commandes `docker service scale` exécutées) :

| Heure | Décision | CPU moyen mesuré | Réplicas |
|---|---|---|---|
| 22:45:13 | Début de la charge k6 | — | 1 |
| 22:45:35 | **SCALE UP** | 100,7 % | 1 → 2 |
| 22:45:50 – 22:46:21 | NO ACTION (cooldown actif) | 91 – 100 % | 2 |
| 22:46:37 | **SCALE UP** | 101,2 % | 2 → 3 |
| 22:46:52 – 22:52:38 | NO ACTION (limite max atteinte) | 68 – 106 % | 3 |
| 22:52:50 | Fin de la charge k6 | — | 3 |
| 22:53:04 | **SCALE DOWN** | 0 % | 3 → 2 |
| 22:54:05 | **SCALE DOWN** | 0 % | 2 → 1 |
| 22:54:21 | NO ACTION (limite min atteinte) | 0 % | 1 |

Vérifications :

- `docker service ls` : 1/1 → 2/2 → 3/3 → 2/2 → 1/1. Le service n'a **jamais dépassé 3 réplicas**.
- Les deux changements successifs sont espacés de 61 s : le cooldown de 60 s est respecté.
- Aucun avertissement ni erreur dans les logs de l'AutoScaler pendant la démonstration.
- Retour au minimum environ 1 min 15 s après la fin de la charge.

**Résultats k6** :

| Mesure | Valeur |
|---|---|
| Requêtes | 2 312 142 (≈ 5 138 req/s) |
| Échecs | 22, soit 0,00 % (seuil < 5 % respecté) |
| Temps de réponse moyen / p95 | 30,9 ms / 82,6 ms |

**Effet du scaling** : lors d'un test précédent avec 1 seul réplica (AutoScaler en
simulation), la même charge plafonnait à environ 1 620 req/s avec un temps
moyen de 98 ms. Avec 3 réplicas, le débit est environ 3 fois plus élevé et le
temps de réponse moyen 3 fois plus court.

**CPU moyen des réplicas sous charge** (Prometheus) : 1 réplica ≈ 100 %,
2 réplicas ≈ 98 %, 3 réplicas ≈ 84 % (le PC est alors utilisé à 100 %,
car k6 tourne sur la même machine). RAM libre minimale pendant le test : 603 Mo.

## 8. Limites connues

- Sous Docker Desktop, cAdvisor ne donne pas de métriques fiables par
  conteneur : l'exporteur Python (`docker stats`) est la source utilisée.
- Démonstration sur un seul nœud Swarm : les réplicas partagent les cœurs du
  PC, et k6 tourne sur la même machine.
- Le scaling est volontairement lent (1 réplica par minute au plus) pour
  rester stable ; c'est le rôle du cooldown.

## 9. Branches

Le travail de chaque membre est sur `feature/autoscaler`, `feature/swarm`,
`feature/monitoring`, `feature/load-testing` et `feature/documentation`.
La branche `integration/final` les réunit, avec les corrections
d'intégration, avant la Pull Request vers `main`.
