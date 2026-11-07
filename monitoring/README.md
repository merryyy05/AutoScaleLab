# AutoScaleLab - Monitoring

Ce dossier contient la chaîne de métriques utilisée par l'AutoScaler :

```
réplicas Swarm ──docker stats──► docker_exporter.py (:8000) ──scrape 15 s──► Prometheus (:9090) ──PromQL──► AutoScaler (:8085)
```

| Fichier | Rôle |
|---|---|
| `docker_exporter.py` | Exporteur Python : CPU et mémoire de chaque réplica du service Swarm |
| `test_docker_exporter.py` | Tests unitaires de l'exporteur (sans Docker) |
| `prometheus.yml` | Configuration de Prometheus |

## Prérequis

- Docker Desktop démarré, Swarm actif et stack `autoscalelab` déployée (voir `swarm/README.md`)
- Python 3.10 ou plus (aucune bibliothèque externe)

## 1. Exporteur Docker

L'exporteur trouve les conteneurs du service grâce à l'étiquette Swarm
`com.docker.swarm.service.name=autoscalelab_app`, puis lit `docker stats`.
Il suit donc automatiquement les réplicas ajoutés ou supprimés.

Lancement depuis la racine du projet (PowerShell ou Git Bash) :

```powershell
python -u monitoring\docker_exporter.py
```

Variables d'environnement facultatives :

| Variable | Défaut | Rôle |
|---|---|---|
| `EXPORTER_SERVICE` | `autoscalelab_app` | Service Swarm surveillé |
| `EXPORTER_PORT` | `8000` | Port HTTP |
| `DOCKER_COMMAND` | `docker` | Commande Docker |
| `DOCKER_TIMEOUT` | `10` | Délai max d'une commande Docker (s) |

Vérification : `curl.exe -s http://localhost:8000/metrics`

```
docker_container_cpu_percent{service="autoscalelab_app",container="autoscalelab_app.1.xxx"} 82.4
docker_container_memory_percent{service="autoscalelab_app",container="autoscalelab_app.1.xxx"} 0.22
autoscalelab_exporter_containers{service="autoscalelab_app"} 1
```

Le CPU est celui de `docker stats` : **100 = 1 cœur**. Comme chaque réplica
est limité à 1 cœur, 100 % signifie « réplica saturé » (de petits
dépassements, jusqu'à ~120 %, sont normaux sous forte charge).

En cas d'erreur Docker, l'exporteur répond HTTP 500 : Prometheus marque la
cible DOWN et l'AutoScaler ne fait aucun changement.

Tests (sans Docker) :

```powershell
python -m unittest discover -s monitoring -v
```

## 2. Prometheus

Depuis la racine du projet, **en PowerShell** :

```powershell
docker run -d --name autoscalelab-prometheus -p 9090:9090 -v "${PWD}\monitoring\prometheus.yml:/etc/prometheus/prometheus.yml:ro" prom/prometheus
```

(En invite `cmd`, remplacer `${PWD}` par `%cd%`.)

Cibles configurées (`prometheus.yml`, scrape toutes les 15 s) :

| Job | Cible | Utilité |
|---|---|---|
| `prometheus` | `localhost:9090` | Prometheus lui-même |
| `docker-exporter` | `host.docker.internal:8000` | **Métriques utilisées par l'AutoScaler** (délai de scrape 10 s) |
| `cadvisor` | `host.docker.internal:8082` | Facultatif, non utilisé par l'AutoScaler |

Vérifier : http://localhost:9090 → *Status > Target health* : `docker-exporter` doit être `UP`
(`cadvisor` reste `DOWN` s'il n'est pas lancé, ce n'est pas bloquant).

Requête utilisée par l'AutoScaler (moyenne des réplicas, une seule série) :

```promql
avg(docker_container_cpu_percent{service="autoscalelab_app"})
```

Vérifier la configuration sans démarrer Prometheus :

```powershell
docker run --rm --entrypoint promtool -v "${PWD}\monitoring\prometheus.yml:/prometheus.yml:ro" prom/prometheus check config /prometheus.yml
```

## 3. cAdvisor (facultatif)

Le port 8080 est réservé à Nginx (Swarm) : cAdvisor est publié sur **8082**.

```powershell
docker run -d --name autoscalelab-cadvisor -p 8082:8080 -v /:/rootfs:ro -v /var/run:/var/run:ro -v /sys:/sys:ro -v /var/lib/docker/:/var/lib/docker:ro -v /dev/disk/:/dev/disk:ro gcr.io/cadvisor/cadvisor:v0.49.1
```

Sous Docker Desktop, cAdvisor ne donne pas de métriques fiables par
conteneur : c'est pourquoi l'exporteur Python est la source utilisée.
