
package com.autoscalelab.autoscaler.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.autoscalelab.autoscaler.client.SwarmClient;

@Service
public class ScalingService {

    private final MetricsService metricsService;
    private final SwarmClient swarmClient;

    private final double cpuHigh;
    private final double cpuLow;

    private final int minReplicas;
    private final int maxReplicas;

    private final String serviceName;

    private int currentReplicas;

    public ScalingService(
            MetricsService metricsService,
            SwarmClient swarmClient,
            @Value("${autoscaler.cpu-high}") double cpuHigh,
            @Value("${autoscaler.cpu-low}") double cpuLow,
            @Value("${autoscaler.min-replicas}") int minReplicas,
            @Value("${autoscaler.max-replicas}") int maxReplicas,
            @Value("${autoscaler.initial-replicas}") int initialReplicas,
            @Value("${autoscaler.service-name}") String serviceName) {

        this.metricsService = metricsService;
        this.swarmClient = swarmClient;

        this.cpuHigh = cpuHigh;
        this.cpuLow = cpuLow;

        this.minReplicas = minReplicas;
        this.maxReplicas = maxReplicas;

        this.currentReplicas = initialReplicas;
        this.serviceName = serviceName;
    }

    public synchronized String checkScaling() {

        double cpu = metricsService.getCpuUsage();

        if (cpu > cpuHigh && currentReplicas < maxReplicas) {

            currentReplicas++;
            swarmClient.scaleService(serviceName, currentReplicas);

            return "SCALE UP : " + currentReplicas + " replicas";

        } else if (cpu < cpuLow && currentReplicas > minReplicas) {

            currentReplicas--;
            swarmClient.scaleService(serviceName, currentReplicas);

            return "SCALE DOWN : " + currentReplicas + " replicas";

        } else {

            return "STABLE : " + currentReplicas + " replicas";
        }
    }
}
