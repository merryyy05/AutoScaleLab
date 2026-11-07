package com.autoscalelab.autoscaler.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.autoscalelab.autoscaler.config.AutoscalerProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Lit les métriques dans Prometheus.
 *
 * La requête CPU vient de {@code autoscaler.cpu-query}. Elle doit renvoyer
 * exactement UNE série (utiliser par exemple avg(...)).
 */
@Service
public class MetricsService implements CpuMetricsProvider {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String prometheusUrl;
    private final String cpuQuery;
    private final boolean cpuQueryTargetsService;

    public MetricsService(
            AutoscalerProperties properties,
            @Value("${prometheus.url:http://localhost:9090}") String prometheusUrl,
            @Value("${prometheus.connect-timeout:2s}") Duration connectTimeout,
            @Value("${prometheus.read-timeout:5s}") Duration readTimeout) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(prometheusUrl)
                .requestFactory(requestFactory)
                .build();
        this.prometheusUrl = prometheusUrl;
        this.cpuQuery = properties.cpuQuery();
        this.cpuQueryTargetsService = cpuQuery.contains(properties.serviceName());

        log.info("Requête CPU Prometheus : {}", cpuQuery);
        if (!cpuQueryTargetsService) {
            log.warn("La requête CPU ne filtre pas sur le service '{}' : elle ne mesure "
                    + "probablement PAS la charge de ce service (ex. CPU de la machine entière). "
                    + "À remplacer quand une métrique par service sera disponible.",
                    properties.serviceName());
        }
    }

    /** Vérifie la connexion avec Prometheus (requête "up"). */
    public String getPrometheusStatus() {
        return query("up");
    }

    /** CPU mesuré, en pourcentage. Lève MetricsUnavailableException si indisponible ou invalide. */
    @Override
    public double getCpuUsage() {
        double cpu = parseCpu(query(cpuQuery));
        log.debug("CPU mesuré : {} %", cpu);
        return cpu;
    }

    public String getCpuQuery() {
        return cpuQuery;
    }

    /** false si la requête ne mentionne pas le service surveillé. */
    public boolean isCpuQueryTargetingService() {
        return cpuQueryTargetsService;
    }

    private String query(String promQl) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/query")
                            .queryParam("query", "{query}")
                            .build(promQl))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new MetricsUnavailableException(
                    "Prometheus injoignable ou en erreur (" + prometheusUrl + ") : " + e.getMessage(), e);
        }
    }

    /** Extrait la valeur CPU d'une réponse JSON Prometheus (type "vector"). */
    static double parseCpu(String json) {

        if (json == null || json.isBlank()) {
            throw new MetricsUnavailableException("Réponse Prometheus vide");
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new MetricsUnavailableException("Réponse JSON Prometheus invalide", e);
        }

        if (!"success".equals(root.path("status").asText())) {
            throw new MetricsUnavailableException(
                    "Prometheus a répondu une erreur : " + root.path("error").asText("inconnue"));
        }

        JsonNode results = root.path("data").path("result");
        if (!results.isArray() || results.isEmpty()) {
            throw new MetricsUnavailableException("Aucune métrique CPU disponible");
        }
        if (results.size() != 1) {
            throw new MetricsUnavailableException(
                    "La requête CPU renvoie " + results.size()
                    + " séries au lieu d'une seule (utiliser avg(...) ou sum(...))");
        }

        JsonNode value = results.get(0).path("value");
        if (!value.isArray() || value.size() < 2) {
            throw new MetricsUnavailableException("Format de métrique CPU invalide");
        }

        double cpu;
        try {
            cpu = Double.parseDouble(value.get(1).asText());
        } catch (NumberFormatException e) {
            throw new MetricsUnavailableException(
                    "Valeur CPU non numérique : " + value.get(1).asText(), e);
        }

        if (!Double.isFinite(cpu) || cpu < 0) {
            throw new MetricsUnavailableException("Valeur CPU invalide : " + cpu);
        }
        return cpu;
    }
}
