import json
import subprocess
from http.server import BaseHTTPRequestHandler, HTTPServer


class MetricsHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.split("?")[0] != "/metrics":
            self.send_error(404)
            return

        try:
            result = subprocess.run(
                [
                    "docker", "stats", "autoscalelab-test-app",
                    "--no-stream", "--format", "{{json .}}"
                ],
                capture_output=True,
                text=True,
                check=True,
                timeout=10
            )

            stats = json.loads(result.stdout.strip())
            cpu = float(stats["CPUPerc"].replace("%", ""))
            memory = float(stats["MemPerc"].replace("%", ""))

            metrics = (
                f"docker_container_cpu_percent {cpu}\n"
                f"docker_container_memory_percent {memory}\n"
            )

            self.send_response(200)
            self.send_header("Content-Type", "text/plain; version=0.0.4")
            self.end_headers()
            self.wfile.write(metrics.encode())

        except Exception as e:
            self.send_error(500, str(e))


server = HTTPServer(("0.0.0.0", 8000), MetricsHandler)
print("Exporteur disponible sur http://localhost:8000/metrics")
server.serve_forever()
