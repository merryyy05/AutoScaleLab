package com.autoscalelab.autoscaler.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests de DockerSwarmClient avec un faux CommandRunner :
 * aucune commande Docker n'est réellement exécutée.
 */
class DockerSwarmClientTest {

    /** Faux CommandRunner : enregistre les commandes et renvoie une réponse préparée. */
    static class FakeCommandRunner extends CommandRunner {
        final List<List<String>> commands = new ArrayList<>();
        final List<Duration> timeouts = new ArrayList<>();
        CommandResult nextResult = new CommandResult(0, "", "");
        RuntimeException nextError;

        @Override
        public CommandResult run(List<String> command, Duration timeout) {
            commands.add(command);
            timeouts.add(timeout);
            if (nextError != null) {
                throw nextError;
            }
            return nextResult;
        }
    }

    private FakeCommandRunner runner;
    private DockerSwarmClient client;

    @BeforeEach
    void setUp() {
        runner = new FakeCommandRunner();
        client = new DockerSwarmClient(runner, "docker", 10);
    }

    @Test
    void getReplicasLitLeNombreRetourneParDocker() {
        runner.nextResult = new CommandResult(0, "3\n", "");

        assertThat(client.getReplicas("autoscalelab_app")).isEqualTo(3);
        assertThat(runner.commands).containsExactly(List.of(
                "docker", "service", "inspect", "autoscalelab_app",
                "--format", "{{.Spec.Mode.Replicated.Replicas}}"));
        assertThat(runner.timeouts).containsExactly(Duration.ofSeconds(10));
    }

    @Test
    void getReplicasRefuseUneReponseNonNumerique() {
        runner.nextResult = new CommandResult(0, "<no value>\n", "");

        assertThatThrownBy(() -> client.getReplicas("autoscalelab_app"))
                .isInstanceOf(SwarmException.class)
                .hasMessageContaining("Réponse inattendue");
    }

    @Test
    void getReplicasSignaleSwarmNonInitialise() {
        // Message réel obtenu sur le PC (Swarm inactif)
        runner.nextResult = new CommandResult(1, "",
                "Error response from daemon: This node is not a swarm manager. "
                + "Use \"docker swarm init\" or \"docker swarm join\" to connect this node to swarm and try again.");

        assertThatThrownBy(() -> client.getReplicas("autoscalelab_app"))
                .isInstanceOf(SwarmException.class)
                .hasMessageContaining("pas initialisé");
    }

    @Test
    void getReplicasSignaleServiceIntrouvable() {
        runner.nextResult = new CommandResult(1, "",
                "Error response from daemon: service autoscalelab_app not found");

        assertThatThrownBy(() -> client.getReplicas("autoscalelab_app"))
                .isInstanceOf(SwarmException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    void getReplicasSignaleDockerDesktopArrete() {
        runner.nextResult = new CommandResult(1, "",
                "error during connect: open //./pipe/dockerDesktopLinuxEngine: "
                + "The system cannot find the file specified.");

        assertThatThrownBy(() -> client.getReplicas("autoscalelab_app"))
                .isInstanceOf(SwarmException.class)
                .hasMessageContaining("Docker Desktop ne répond pas");
    }

    @Test
    void lesErreursDuRunnerSontPropagees() {
        runner.nextError = new SwarmException("Délai dépassé (10 s)");

        assertThatThrownBy(() -> client.getReplicas("autoscalelab_app"))
                .isInstanceOf(SwarmException.class)
                .hasMessageContaining("Délai dépassé");
    }

    @Test
    void scaleServiceConstruitLaBonneCommande() {
        client.scaleService("autoscalelab_app", 3);

        assertThat(runner.commands).containsExactly(List.of(
                "docker", "service", "scale", "autoscalelab_app=3", "--detach"));
    }

    @Test
    void scaleServiceSignaleUnEchecDocker() {
        runner.nextResult = new CommandResult(1, "", "quelque chose a échoué");

        assertThatThrownBy(() -> client.scaleService("autoscalelab_app", 2))
                .isInstanceOf(SwarmException.class)
                .hasMessageContaining("code 1")
                .hasMessageContaining("quelque chose a échoué");
    }

    @Test
    void scaleServiceRefuseUnNombreNegatifSansAppelerDocker() {
        assertThatThrownBy(() -> client.scaleService("autoscalelab_app", -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(runner.commands).isEmpty();
    }

    @Test
    void lesNomsDeServiceInvalidesSontRefusesSansAppelerDocker() {
        for (String name : new String[] {null, "", "  ", "--help", "app;rm", "a b"}) {
            assertThatThrownBy(() -> client.getReplicas(name))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> client.scaleService(name, 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(runner.commands).isEmpty();
    }

    @Test
    void unDelaiInvalideEstRefuse() {
        assertThatThrownBy(() -> new DockerSwarmClient(runner, "docker", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
