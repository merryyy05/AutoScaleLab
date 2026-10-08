# AutoScaleLab - Monitoring

Ce dossier contient la configuration de monitoring du projet AutoScaleLab avec Prometheus.

## Objectif

Prometheus collecte les métriques nécessaires à la surveillance du système.

À terme, ces métriques seront utilisées par le moteur AutoScaleLab pour prendre des décisions de SCALE OUT ou SCALE IN.

## Prérequis

- Docker installé
- Docker Desktop démarré

Vérifier Docker :

docker --version

docker info

## Configuration

Le fichier `prometheus.yml` contient la configuration de Prometheus.

Actuellement, Prometheus collecte ses propres métriques toutes les 15 secondes.

## Lancer Prometheus

Depuis la racine du projet sous Windows :

docker run -d --name autoscalelab-prometheus -p 9090:9090 -v "%cd%\monitoring\prometheus.yml:/etc/prometheus/prometheus.yml:ro" prom/prometheus

## Vérifier le conteneur

docker ps

Le conteneur `autoscalelab-prometheus` doit être en cours d'exécution.

## Accéder à Prometheus

Ouvrir dans le navigateur :

http://localhost:9090

Puis consulter :

Status > Target health

La cible Prometheus doit avoir l'état `UP`.

## Requêtes PromQL testées

Vérification de la disponibilité :

up

Vérification des requêtes HTTP :

prometheus_http_requests_total

## État actuel

La première configuration Prometheus fonctionne correctement.

La cible `localhost:9090` est `UP` et les premières requêtes PromQL ont été testées avec succès.

## Prochaine étape

La configuration sera ensuite complétée afin de collecter les métriques du service conteneurisé utilisé par AutoScaleLab, notamment les métriques nécessaires à la politique d'auto-scaling.