package com.autoscalelab.autoscaler.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Teste CommandRunner avec un petit programme Java (FakeProcess),
 * pour ne dépendre ni de Docker ni du système d'exploitation.
 */
class CommandRunnerTest {

    private final CommandRunner runner = new CommandRunner();

    private static List<String> fakeProcess(String... args) {
        String java = ProcessHandle.current().info().command().orElse("java");
        List<String> command = new ArrayList<>(List.of(
                java, "-cp", System.getProperty("java.class.path"),
                FakeProcess.class.getName()));
        command.addAll(List.of(args));
        return command;
    }

    @Test
    void recupereLaSortieEtLeCodeDeRetour() {
        CommandResult result = runner.run(
                fakeProcess("print", "3"), Duration.ofSeconds(30));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stdout().trim()).isEqualTo("3");
    }

    @Test
    void recupereLaSortieErreurEtLeCodeDEchec() {
        CommandResult result = runner.run(
                fakeProcess("fail", "7", "erreur simulee"), Duration.ofSeconds(30));

        assertThat(result.exitCode()).isEqualTo(7);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.stderr().trim()).isEqualTo("erreur simulee");
    }

    @Test
    void arreteLaCommandeSiLeDelaiEstDepasse() {
        long start = System.nanoTime();

        assertThatThrownBy(() -> runner.run(
                fakeProcess("sleep", "60000"), Duration.ofSeconds(2)))
                .isInstanceOf(SwarmException.class)
                .hasMessageContaining("Délai dépassé");

        assertThat(Duration.ofNanos(System.nanoTime() - start))
                .isLessThan(Duration.ofSeconds(20));
    }

    @Test
    void signaleUneCommandeIntrouvable() {
        assertThatThrownBy(() -> runner.run(
                List.of("commande-inexistante-autoscalelab"), Duration.ofSeconds(5)))
                .isInstanceOf(SwarmException.class)
                .hasMessageContaining("Impossible de lancer");
    }

    @Test
    void refuseLesParametresInvalides() {
        assertThatThrownBy(() -> runner.run(List.of(), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runner.run(List.of("x"), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
