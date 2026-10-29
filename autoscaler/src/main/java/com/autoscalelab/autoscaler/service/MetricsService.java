
package com.autoscalelab.autoscaler.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

@Service
public class MetricsService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MetricsService(
            @Value("${prometheus.url:http://localhost:9090}") String prometheusUrl) {

        this.restClient = RestClient.builder()
                .baseUrl(prometheusUrl)
                .build();
    }

    // Vérifier la connexion avec Prometheus
    public String getPrometheusStatus() {

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/query")
                        .queryParam("query", "up")
                        .build())
                .retrieve()
                .body(String.class);
    }

    // Récupérer le CPU depuis Prometheus
    public double getCpuUsage() {

        String query =
                "rate(container_cpu_usage_seconds_total{job=\"cadvisor\",id=\"/\"}[1m]) * 100";

        String response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/query")
                        .queryParam("query", "{query}")
                        .build(query))
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);

            if (!"success".equals(root.path("status").asText())) {
                throw new IllegalStateException("Erreur Prometheus");
            }

            JsonNode results = root.path("data").path("result");

            if (!results.isArray() || results.isEmpty()) {
                throw new IllegalStateException(
                        "Aucune métrique CPU disponible");
            }

            JsonNode value = results.get(0).path("value");

            if (!value.isArray() || value.size() < 2) {
                throw new IllegalStateException(
                        "Format de métrique CPU invalide");
            }

            double cpu = Double.parseDouble(
                    value.get(1).asText()
            );

            if (!Double.isFinite(cpu)) {
                throw new IllegalStateException(
                        "Valeur CPU invalide");
            }

            System.out.println("CPU réel mesuré : " + cpu + "%");

            return cpu;

        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Réponse JSON Prometheus invalide", e);
        }
    }
}
