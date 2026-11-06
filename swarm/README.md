# AutoScaleLab - Docker Swarm

## Objectif

Cette partie déploie l'application de démonstration (Nginx) avec Docker Swarm.

Docker Swarm est utilisé pour :

- déployer le service ;
- gérer les réplicas ;
- effectuer le Scale Out et le Scale In demandés par l'AutoScaler ;
- maintenir le service disponible (redémarrage en cas d'échec).

## Prérequis

- Docker Desktop démarré
- Git Bash ou WSL pour les scripts `.sh`

## Initialiser Docker Swarm

```bash
bash swarm/init-swarm.sh
```

Le script ne fait rien si Swarm est déjà actif, puis affiche les nœuds.

## Déployer la stack

```bash
bash swarm/deploy-stack.sh
```

La stack s'appelle `autoscalelab` et contient le service `app` :
Docker le nomme donc **`autoscalelab_app`**. C'est ce nom qu'utilisent
l'exporteur (`monitoring/docker_exporter.py`) et l'AutoScaler.

## Configuration (`docker-stack.yml`)

| Paramètre | Valeur | Rôle |
|---|---|---|
| Image | `nginx:alpine` | Application de démonstration |
| Port | `8080` → `80` | Cible des tests k6 : http://localhost:8080 |
| Réplicas de départ | 1 | Ensuite gérés par l'AutoScaler (1 à 3 pour la démo) |
| Limite CPU | `1.0` cœur par réplica | 100 % dans `docker stats` = réplica saturé |
| Redémarrage | `on-failure`, délai 5 s | Disponibilité |
| Mise à jour | 1 réplica à la fois, délai 5 s | Mise à jour progressive |
| Réseau | `autoscalelab-network` (overlay) | Réseau de la stack |

La limite d'un cœur donne un sens clair aux seuils de l'AutoScaler
(75 % / 30 % de la capacité d'un réplica). Si elle change, il faut adapter
`autoscaler.cpu-high` et `autoscaler.cpu-low`.

## Commandes utiles

```bash
docker service ls                                 # état du service (ex. 1/1)
docker service ps autoscalelab_app                # réplicas et leur état
docker service inspect autoscalelab_app --format '{{.Spec.Mode.Replicated.Replicas}}'
docker service scale autoscalelab_app=2           # scaling manuel (à éviter pendant la démo)
docker stack rm autoscalelab                      # supprimer la stack
```

Pendant la démonstration, le scaling est fait automatiquement par
l'AutoScaler (`autoscaler/`), en mode réel avec le profil `swarm`.
