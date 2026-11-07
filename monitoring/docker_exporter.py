"""Exporteur Prometheus des métriques Docker des réplicas d'un service Swarm.

Pour chaque conteneur du service (repéré par l'étiquette Swarm
com.docker.swarm.service.name), publie :

    docker_container_cpu_percent{service="...",container="..."}     CPU (100 = 1 coeur)
    docker_container_memory_percent{service="...",container="..."}  mémoire (% de la limite)
    autoscalelab_exporter_containers{service="..."}                 nombre de réplicas mesurés

Unité du CPU : celle de `docker stats` (100 % = un coeur entier). Avec une
limite d'un coeur par réplica (swarm/docker-stack.yml), 100 % = réplica saturé.

Lancement (depuis la racine du dépôt) :
    python monitoring/docker_exporter.py

Variables d'environnement facultatives :
    EXPORTER_SERVICE  service Swarm surveillé   (défaut : autoscalelab_app)
    EXPORTER_PORT     port HTTP                 (défaut : 8000)
    DOCKER_COMMAND    commande Docker           (défaut : docker)
    DOCKER_TIMEOUT    délai max d'une commande  (défaut : 10 secondes)
"""

import json
import os
import subprocess
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SERVICE = os.environ.get("EXPORTER_SERVICE", "autoscalelab_app")
PORT = int(os.environ.get("EXPORTER_PORT", "8000"))
DOCKER = os.environ.get("DOCKER_COMMAND", "docker")
TIMEOUT = float(os.environ.get("DOCKER_TIMEOUT", "10"))

SWARM_SERVICE_LABEL = "com.docker.swarm.service.name"
STATS_FORMAT = "{{json .}}"


def run_docker(args, run=subprocess.run):
    """Exécute une commande Docker et retourne sa sortie standard."""
    result = run(
        [DOCKER, *args],
        capture_output=True,
        text=True,
        check=True,
        timeout=TIMEOUT,
    )
    return result.stdout


def list_containers(service, run=subprocess.run):
    """Identifiants des conteneurs en cours d'exécution du service Swarm."""
    output = run_docker(
        ["ps", "-q", "--filter", f"label={SWARM_SERVICE_LABEL}={service}"], run
    )
    return output.split()


def parse_percent(value):
    """'12.34%' -> 12.34 ; '--' ou vide -> None."""
    text = (value or "").strip().rstrip("%").strip()
    if text in ("", "--"):
        return None
    return float(text)


def parse_stats(output):
    """Lit la sortie de `docker stats --format '{{json .}}'` (une ligne JSON par conteneur)."""
    stats = []
    for line in output.splitlines():
        line = line.strip()
        if not line:
            continue
        data = json.loads(line)
        cpu = parse_percent(data.get("CPUPerc"))
        if cpu is None:
            continue  # conteneur en cours de démarrage : pas encore de mesure
        stats.append({
            "container": data.get("Name") or data.get("ID", "inconnu"),
            "cpu": cpu,
            "memory": parse_percent(data.get("MemPerc")),
        })
    return stats


def collect(service, run=subprocess.run):
    """Mesure tous les réplicas du service."""
    ids = list_containers(service, run)
    if not ids:
        return []
    try:
        return parse_stats(run_docker(["stats", "--no-stream", "--format", STATS_FORMAT, *ids], run))
    except subprocess.CalledProcessError:
        # Un réplica a pu disparaître entre `docker ps` et `docker stats`
        # (scale down) : on mesure alors les conteneurs un par un.
        stats = []
        for container_id in ids:
            try:
                stats += parse_stats(run_docker(
                    ["stats", "--no-stream", "--format", STATS_FORMAT, container_id], run))
            except subprocess.CalledProcessError:
                continue
        return stats


def _escape(value):
    return str(value).replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")


def render(service, stats):
    """Format texte Prometheus (chaque famille de métriques est regroupée)."""
    svc = _escape(service)
    lines = [
        "# HELP docker_container_cpu_percent CPU du conteneur, 100 = 1 coeur (docker stats).",
        "# TYPE docker_container_cpu_percent gauge",
    ]
    for s in stats:
        labels = f'service="{svc}",container="{_escape(s["container"])}"'
        lines.append(f"docker_container_cpu_percent{{{labels}}} {s['cpu']}")

    lines += [
        "# HELP docker_container_memory_percent Memoire du conteneur en % (docker stats).",
        "# TYPE docker_container_memory_percent gauge",
    ]
    for s in stats:
        if s["memory"] is not None:
            labels = f'service="{svc}",container="{_escape(s["container"])}"'
            lines.append(f"docker_container_memory_percent{{{labels}}} {s['memory']}")

    lines += [
        "# HELP autoscalelab_exporter_containers Nombre de conteneurs mesures pour le service.",
        "# TYPE autoscalelab_exporter_containers gauge",
        f'autoscalelab_exporter_containers{{service="{svc}"}} {len(stats)}',
    ]
    return "\n".join(lines) + "\n"


class MetricsHandler(BaseHTTPRequestHandler):

    def do_GET(self):
        if self.path.split("?")[0] != "/metrics":
            self.send_error(404)
            return

        try:
            body = render(SERVICE, collect(SERVICE)).encode()
        except FileNotFoundError:
            self.send_error(500, f"Commande '{DOCKER}' introuvable")
            return
        except subprocess.TimeoutExpired:
            self.send_error(500, "Delai depasse pour la commande Docker")
            return
        except (subprocess.CalledProcessError, ValueError) as e:
            # Docker arrêté, Swarm inactif, sortie illisible... Prometheus
            # marque alors la cible DOWN et l'AutoScaler ne fait rien.
            self.send_error(500, str(e))
            return

        self.send_response(200)
        self.send_header("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
        self.end_headers()
        self.wfile.write(body)


def main():
    server = ThreadingHTTPServer(("0.0.0.0", PORT), MetricsHandler)
    print(f"Exporteur disponible sur http://localhost:{PORT}/metrics (service : {SERVICE})")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    sys.exit(main())
