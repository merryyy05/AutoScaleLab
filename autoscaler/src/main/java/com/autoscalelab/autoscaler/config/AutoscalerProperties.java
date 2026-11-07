package com.autoscalelab.autoscaler.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages de l'autoscaler (préfixe {@code autoscaler.} dans application.properties).
 *
 * Les valeurs sont vérifiées au démarrage : si elles sont incohérentes,
 * l'application refuse de démarrer avec un message clair.
 *
 * Les autres clés du préfixe (swarm.simulation, docker.*, scheduler.enabled,
 * initial-delay) sont lues ailleurs et simplement ignorées ici.
 */
@ConfigurationProperties(prefix = "autoscaler")
public record AutoscalerProperties(
        double cpuHigh,
        double cpuLow,
        int minReplicas,
        int maxReplicas,
        String serviceName,
        @DefaultValue("1") int initialReplicas,
        @DefaultValue("15s") Duration checkInterval,
        @DefaultValue("60s") Duration cooldown,
        String cpuQuery) {

    public AutoscalerProperties {
        require(Double.isFinite(cpuLow) && cpuLow >= 0,
                "autoscaler.cpu-low doit être un nombre >= 0 (reçu " + cpuLow + ")");
        require(Double.isFinite(cpuHigh) && cpuHigh > cpuLow,
                "autoscaler.cpu-high (" + cpuHigh + ") doit être strictement supérieur à autoscaler.cpu-low (" + cpuLow + ")");
        require(minReplicas >= 1,
                "autoscaler.min-replicas doit être >= 1 (reçu " + minReplicas + ")");
        require(maxReplicas >= minReplicas,
                "autoscaler.max-replicas (" + maxReplicas + ") doit être >= autoscaler.min-replicas (" + minReplicas + ")");
        require(serviceName != null && !serviceName.isBlank(),
                "autoscaler.service-name est obligatoire");
        require(initialReplicas >= 0,
                "autoscaler.initial-replicas doit être >= 0 (reçu " + initialReplicas + ")");
        require(checkInterval != null && checkInterval.isPositive(),
                "autoscaler.check-interval doit être strictement positif");
        require(cooldown != null && !cooldown.isNegative(),
                "autoscaler.cooldown ne peut pas être négatif");
        require(cpuQuery != null && !cpuQuery.isBlank(),
                "autoscaler.cpu-query est obligatoire");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException("Configuration autoscaler invalide : " + message);
        }
    }
}
