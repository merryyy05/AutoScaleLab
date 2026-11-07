package com.autoscalelab.autoscaler.client;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implémentation réelle : pilote Docker Swarm via la commande {@code docker}.
 *
 * Active uniquement si {@code autoscaler.swarm.simulation=false}.
 */
@Component
@ConditionalOnProperty(name = "autoscaler.swarm.simulation", havingValue = "false")
public class DockerSwarmClient implements SwarmClient {

    private static final Logger log = LoggerFactory.getLogger(DockerSwarmClient.class);

    /** Nom de service Docker valide (empêche aussi un nom commençant par "-"). */
    private static final Pattern SERVICE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");

    private static final String REPLICAS_FORMAT = "{{.Spec.Mode.Replicated.Replicas}}";

    private final CommandRunner commandRunner;
    private final String dockerCommand;
    private final Duration timeout;

    public DockerSwarmClient(
            CommandRunner commandRunner,
            @Value("${autoscaler.docker.command:docker}") String dockerCommand,
            @Value("${autoscaler.docker.timeout-seconds:10}") long timeoutSeconds) {

        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException(
                    "autoscaler.docker.timeout-seconds doit être > 0");
        }
        this.commandRunner = commandRunner;
        this.dockerCommand = dockerCommand;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public int getReplicas(String serviceName) {

        validateServiceName(serviceName);

        CommandResult result = execute(List.of(
                dockerCommand, "service", "inspect", serviceName,
                "--format", REPLICAS_FORMAT));

        String output = result.stdout().trim();
        try {
            int replicas = Integer.parseInt(output);
            if (replicas < 0) {
                throw new NumberFormatException();
            }
            return replicas;
        } catch (NumberFormatException e) {
            throw new SwarmException(
                    "Réponse inattendue de Docker pour le service '" + serviceName
                    + "' : \"" + output + "\" (le service est-il en mode replicated ?)");
        }
    }

    @Override
    public void scaleService(String serviceName, int replicas) {

        validateServiceName(serviceName);
        if (replicas < 0) {
            throw new IllegalArgumentException(
                    "Le nombre de réplicas ne peut pas être négatif : " + replicas);
        }

        // --detach : ne pas attendre que tous les conteneurs soient démarrés
        execute(List.of(
                dockerCommand, "service", "scale",
                serviceName + "=" + replicas, "--detach"));

        log.info("Docker Swarm : service {} → {} réplicas", serviceName, replicas);
    }

    private CommandResult execute(List<String> command) {

        log.debug("Exécution : {}", String.join(" ", command));
        CommandResult result = commandRunner.run(command, timeout);

        if (!result.isSuccess()) {
            throw new SwarmException(explain(result, command));
        }
        return result;
    }

    /** Traduit les erreurs Docker courantes en message clair. */
    private static String explain(CommandResult result, List<String> command) {

        String stderr = result.stderr().trim();
        String lower = stderr.toLowerCase(Locale.ROOT);

        if (lower.contains("not a swarm manager")) {
            return "Docker Swarm n'est pas initialisé sur cette machine "
                    + "(lancer swarm/init-swarm.sh ou 'docker swarm init')";
        }
        // "Error: No such service: x" (inspect) ou "service x not found" (scale)
        if (lower.contains("no such service")
                || (lower.contains("service") && lower.contains("not found"))) {
            return "Le service Docker est introuvable (la stack est-elle déployée ?) : " + stderr;
        }
        if (lower.contains("cannot connect to the docker daemon")
                || lower.contains("error during connect")) {
            return "Docker Desktop ne répond pas (est-il démarré ?) : " + stderr;
        }
        return "La commande Docker a échoué (code " + result.exitCode() + ") : "
                + String.join(" ", command)
                + (stderr.isEmpty() ? "" : " → " + stderr);
    }

    private static void validateServiceName(String serviceName) {
        if (serviceName == null || !SERVICE_NAME.matcher(serviceName).matches()) {
            throw new IllegalArgumentException("Nom de service invalide : " + serviceName);
        }
    }
}
