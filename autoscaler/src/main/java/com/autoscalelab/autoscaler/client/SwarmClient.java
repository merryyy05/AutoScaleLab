
package com.autoscalelab.autoscaler.client;

import org.springframework.stereotype.Component;

@Component
public class SwarmClient {

    public void scaleService(String serviceName, int replicas) {

        System.out.println(
            "Simulation Docker Swarm : service "
            + serviceName
            + " → "
            + replicas
            + " réplicas"
        );
    }
}
