#!/usr/bin/env bash

set -euo pipefail

echo "======================================"
echo " AutoScaleLab - Docker Swarm Init"
echo "======================================"

SWARM_STATE=$(docker info --format '{{.Swarm.LocalNodeState}}')

if [ "$SWARM_STATE" = "active" ]; then
    echo "Docker Swarm est deja actif."
else
    echo "Initialisation de Docker Swarm..."
    docker swarm init
fi

echo
echo "Nodes disponibles :"
docker node ls