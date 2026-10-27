#!/usr/bin/env bash

set -euo pipefail

STACK_NAME="autoscalelab"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "======================================"
echo " AutoScaleLab - Deploy Stack"
echo "======================================"

docker stack deploy \
  -c "$SCRIPT_DIR/docker-stack.yml" \
  "$STACK_NAME"

echo
echo "Services deployes :"
docker stack services "$STACK_NAME"