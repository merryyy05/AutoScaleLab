package com.autoscalelab.autoscaler.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mode simulation : aucun appel à Docker, les réplicas sont gardés en mémoire.
 *
 * Actif par défaut (propriété {@code autoscaler.swarm.simulation} absente ou à true).
 */
@Component
@ConditionalOnProperty(name = "autoscaler.swarm.simulation", havingValue = "true", matchIfMissing = true)
public class SimulatedSwarmClient implements SwarmClient {

    private static final Logger log = LoggerFactory.getLogger(SimulatedSwarmClient.class);

    private final int initialReplicas;
    private final Map<String, Integer> replicas = new ConcurrentHashMap<>();

    public SimulatedSwarmClient(
            @Value("${autoscaler.initial-replicas:1}") int initialReplicas) {

        if (initialReplicas < 0) {
            throw new IllegalArgumentException(
                    "autoscaler.initial-replicas ne peut pas être négatif");
        }
        this.initialReplicas = initialReplicas;
        log.warn("Mode SIMULATION actif : aucune commande Docker ne sera exécutée");
    }

    @Override
    public int getReplicas(String serviceName) {
        validateServiceName(serviceName);
        return replicas.getOrDefault(serviceName, initialReplicas);
    }

    @Override
    public void scaleService(String serviceName, int newReplicas) {
        validateServiceName(serviceName);
        if (newReplicas < 0) {
            throw new IllegalArgumentException(
                    "Le nombre de réplicas ne peut pas être négatif : " + newReplicas);
        }
        replicas.put(serviceName, newReplicas);
        log.info("[SIMULATION] service {} → {} réplicas", serviceName, newReplicas);
    }

    private static void validateServiceName(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("Nom de service vide");
        }
    }
}
