
package com.autoscalelab.autoscaler.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MetricsService {

    private final RestClient restClient;

    public MetricsService(
            @Value("${prometheus.url:http://localhost:9090}") String prometheusUrl) {

        this.restClient = RestClient.builder()
                .baseUrl(prometheusUrl)
                .build();
    }

    public String getPrometheusStatus() {

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/query")
                        .queryParam("query", "up")
                        .build())
                .retrieve()
                .body(String.class);
    }

    // Valeur simulée pour tester l'autoscaling
    public double getCpuUsage() {
        return 20.0;
    }
}
