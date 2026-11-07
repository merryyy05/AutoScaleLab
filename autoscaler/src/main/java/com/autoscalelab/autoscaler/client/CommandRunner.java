package com.autoscalelab.autoscaler.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;

/**
 * Lance une commande système (sans passer par un shell) avec un délai maximum.
 *
 * Les sorties standard et d'erreur sont lues en parallèle pour éviter
 * qu'un tampon plein bloque le processus. Si le délai est dépassé,
 * le processus est arrêté de force.
 */
@Component
public class CommandRunner {

    private static final Duration STREAM_GRACE = Duration.ofSeconds(2);

    public CommandResult run(List<String> command, Duration timeout) {

        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("La commande ne peut pas être vide");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Le délai doit être strictement positif");
        }

        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new SwarmException(
                    "Impossible de lancer '" + command.get(0)
                    + "' : vérifiez qu'il est installé et présent dans le PATH", e);
        }

        FutureTask<String> stdout = readAsync(process.getInputStream());
        FutureTask<String> stderr = readAsync(process.getErrorStream());

        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new SwarmException(
                        "Délai dépassé (" + timeout.toSeconds() + " s) pour la commande : "
                        + String.join(" ", command));
            }

            return new CommandResult(
                    process.exitValue(),
                    await(stdout),
                    await(stderr));

        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new SwarmException("Commande interrompue : " + String.join(" ", command), e);
        }
    }

    private static FutureTask<String> readAsync(InputStream stream) {
        FutureTask<String> task = new FutureTask<>(() -> {
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        });
        Thread.ofVirtual().start(task);
        return task;
    }

    private static String await(FutureTask<String> task) throws InterruptedException {
        try {
            return task.get(STREAM_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            return "";
        }
    }
}
