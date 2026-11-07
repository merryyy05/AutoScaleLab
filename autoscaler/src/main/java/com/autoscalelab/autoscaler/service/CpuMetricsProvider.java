package com.autoscalelab.autoscaler.service;

/**
 * Source de la mesure CPU utilisée pour décider du scaling.
 * Implémentée par {@link MetricsService} (Prometheus) ; remplacée par un faux dans les tests.
 */
public interface CpuMetricsProvider {

    /**
     * @return le CPU mesuré, en pourcentage
     * @throws RuntimeException si la mesure est indisponible ou invalide
     */
    double getCpuUsage();
}
