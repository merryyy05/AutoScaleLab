"""Tests de l'exporteur, sans Docker (les commandes sont simulées).

Lancement depuis la racine du dépôt :
    python -m unittest discover -s monitoring -v
"""

import json
import subprocess
import unittest
from types import SimpleNamespace

import docker_exporter as exporter

SERVICE = "autoscalelab_app"


def stats_line(name, cpu, mem="1.50%"):
    return json.dumps({"ID": name[-4:], "Name": name, "CPUPerc": cpu, "MemPerc": mem})


class FakeDocker:
    """Remplace subprocess.run : enregistre les commandes et renvoie des sorties préparées."""

    def __init__(self, ps="", stats="", stats_error_for=None):
        self.ps = ps
        self.stats = stats
        self.stats_error_for = stats_error_for or set()
        self.calls = []

    def __call__(self, command, **kwargs):
        self.calls.append(command)
        args = command[1:]
        if args[0] == "ps":
            return SimpleNamespace(stdout=self.ps)
        if args[0] == "stats":
            ids = args[4:]
            if any(i in self.stats_error_for for i in ids):
                raise subprocess.CalledProcessError(1, command, stderr="No such container")
            lines = [l for l in self.stats.splitlines() if any(json.loads(l)["ID"] == i for i in ids)]
            return SimpleNamespace(stdout="\n".join(lines) + "\n")
        raise AssertionError(f"commande inattendue : {command}")


class ListContainersTest(unittest.TestCase):

    def test_filtre_sur_l_etiquette_swarm_du_service(self):
        docker = FakeDocker(ps="aaaa\nbbbb\n")

        self.assertEqual(exporter.list_containers(SERVICE, docker), ["aaaa", "bbbb"])
        self.assertEqual(docker.calls[0][1:], [
            "ps", "-q", "--filter", "label=com.docker.swarm.service.name=autoscalelab_app"])


class CollectTest(unittest.TestCase):

    def test_une_mesure_par_replica(self):
        docker = FakeDocker(
            ps="r1aa\nr2bb\n",
            stats="\n".join([
                stats_line("autoscalelab_app.1.xyzr1aa", "82.40%"),
                stats_line("autoscalelab_app.2.xyzr2bb", "64.00%"),
            ]))

        stats = exporter.collect(SERVICE, docker)

        self.assertEqual([s["container"] for s in stats],
                         ["autoscalelab_app.1.xyzr1aa", "autoscalelab_app.2.xyzr2bb"])
        self.assertEqual([s["cpu"] for s in stats], [82.4, 64.0])
        self.assertEqual(docker.calls[1][1:4], ["stats", "--no-stream", "--format"])

    def test_aucun_conteneur_aucun_appel_stats(self):
        docker = FakeDocker(ps="")

        self.assertEqual(exporter.collect(SERVICE, docker), [])
        self.assertEqual(len(docker.calls), 1)

    def test_un_replica_disparu_n_empeche_pas_les_autres_mesures(self):
        docker = FakeDocker(
            ps="r1aa\nr2bb\n",
            stats=stats_line("autoscalelab_app.1.xyzr1aa", "40.00%"),
            stats_error_for={"r2bb"})

        stats = exporter.collect(SERVICE, docker)

        self.assertEqual([s["container"] for s in stats], ["autoscalelab_app.1.xyzr1aa"])

    def test_docker_en_erreur_est_propage(self):
        def failing(command, **kwargs):
            raise subprocess.CalledProcessError(1, command, stderr="not a swarm manager")

        with self.assertRaises(subprocess.CalledProcessError):
            exporter.collect(SERVICE, failing)


class ParseTest(unittest.TestCase):

    def test_pourcentages(self):
        self.assertEqual(exporter.parse_percent("12.34%"), 12.34)
        self.assertEqual(exporter.parse_percent("103.5%"), 103.5)
        self.assertIsNone(exporter.parse_percent("--"))
        self.assertIsNone(exporter.parse_percent(""))
        self.assertIsNone(exporter.parse_percent(None))

    def test_conteneur_sans_mesure_cpu_ignore(self):
        output = "\n".join([
            stats_line("autoscalelab_app.1.a", "--", "--"),
            stats_line("autoscalelab_app.2.b", "5.00%"),
        ])
        stats = exporter.parse_stats(output)

        self.assertEqual([s["container"] for s in stats], ["autoscalelab_app.2.b"])

    def test_json_invalide_leve_une_erreur(self):
        with self.assertRaises(ValueError):
            exporter.parse_stats("pas du json")


class RenderTest(unittest.TestCase):

    def test_format_prometheus_avec_etiquettes(self):
        text = exporter.render(SERVICE, [
            {"container": "autoscalelab_app.1.a", "cpu": 82.4, "memory": 1.5},
            {"container": "autoscalelab_app.2.b", "cpu": 64.0, "memory": None},
        ])

        self.assertIn('docker_container_cpu_percent{service="autoscalelab_app",'
                      'container="autoscalelab_app.1.a"} 82.4', text)
        self.assertIn('docker_container_cpu_percent{service="autoscalelab_app",'
                      'container="autoscalelab_app.2.b"} 64.0', text)
        self.assertIn('docker_container_memory_percent{service="autoscalelab_app",'
                      'container="autoscalelab_app.1.a"} 1.5', text)
        self.assertNotIn('container="autoscalelab_app.2.b"} None', text)
        self.assertIn('autoscalelab_exporter_containers{service="autoscalelab_app"} 2', text)
        self.assertTrue(text.endswith("\n"))

    def test_chaque_famille_est_regroupee(self):
        text = exporter.render(SERVICE, [
            {"container": "a", "cpu": 1.0, "memory": 1.0},
            {"container": "b", "cpu": 2.0, "memory": 2.0},
        ])
        families = [l.split("{")[0] for l in text.splitlines() if not l.startswith("#")]

        self.assertEqual(families, [
            "docker_container_cpu_percent", "docker_container_cpu_percent",
            "docker_container_memory_percent", "docker_container_memory_percent",
            "autoscalelab_exporter_containers"])

    def test_aucun_replica(self):
        text = exporter.render(SERVICE, [])

        self.assertNotIn("docker_container_cpu_percent{", text)
        self.assertIn('autoscalelab_exporter_containers{service="autoscalelab_app"} 0', text)

    def test_les_guillemets_sont_echappes(self):
        text = exporter.render('svc"x', [{"container": 'c"1', "cpu": 1.0, "memory": None}])

        self.assertIn('service="svc\\"x",container="c\\"1"', text)


if __name__ == "__main__":
    unittest.main()
