package com.autoscalelab.autoscaler.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autoscalelab.autoscaler.client.SimulatedSwarmClient;
import com.autoscalelab.autoscaler.client.SwarmClient;
import com.autoscalelab.autoscaler.config.AutoscalerProperties;
import com.autoscalelab.autoscaler.service.MetricsService;
import com.autoscalelab.autoscaler.service.MetricsUnavailableException;
import com.autoscalelab.autoscaler.service.ScalingDecision;
import com.autoscalelab.autoscaler.service.ScalingService;

/**
 * API REST de l'autoscaler.
 *
 * Les routes GET sont en lecture seule : elles ne modifient jamais les réplicas.
 * Seule la route POST /api/scaling/check déclenche un cycle.
 */
@RestController
@RequestMapping("/api")
public class AutoscalerController {

    private final ScalingService scalingService;
    private final MetricsService metricsService;
    private final SwarmClient swarmClient;
    private final AutoscalerProperties properties;
    private final boolean schedulerEnabled;

    public AutoscalerController(
            ScalingService scalingService,
            MetricsService metricsService,
            SwarmClient swarmClient,
            AutoscalerProperties properties,
            @Value("${autoscaler.scheduler.enabled:true}") boolean schedulerEnabled) {

        this.scalingService = scalingService;
        this.metricsService = metricsService;
        this.swarmClient = swarmClient;
        this.properties = properties;
        this.schedulerEnabled = schedulerEnabled;
    }

    /** État et configuration de l'autoscaler (lecture seule). */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("application", "AutoScaleLab");
        status.put("mode", swarmClient instanceof SimulatedSwarmClient ? "SIMULATION" : "DOCKER");
        status.put("service", properties.serviceName());
        status.put("cpuHigh", properties.cpuHigh());
        status.put("cpuLow", properties.cpuLow());
        status.put("minReplicas", properties.minReplicas());
        status.put("maxReplicas", properties.maxReplicas());
        status.put("cooldownSeconds", properties.cooldown().toSeconds());
        status.put("checkIntervalSeconds", properties.checkInterval().toSeconds());
        status.put("schedulerEnabled", schedulerEnabled);
        status.put("cpuQuery", metricsService.getCpuQuery());
        if (!metricsService.isCpuQueryTargetingService()) {
            status.put("cpuQueryWarning",
                    "La requête CPU ne filtre pas sur le service " + properties.serviceName()
                    + " : elle ne mesure probablement pas sa charge.");
        }
        status.put("lastScaleTime", scalingService.getLastScaleTime().orElse(null));
        status.put("lastDecision", scalingService.getLastDecision().orElse(null));
        return status;
    }

    /** Dernière décision prise (lecture seule, ne déclenche rien). */
    @GetMapping("/scaling")
    public Object getLastDecision() {
        return scalingService.getLastDecision()
                .<Object>map(decision -> decision)
                .orElse(Map.of("message", "Aucune vérification effectuée pour l'instant"));
    }

    /** Lance un cycle de vérification manuel (respecte seuils, limites et cooldown). */
    @PostMapping("/scaling/check")
    public ScalingDecision checkNow() {
        return scalingService.evaluateAndScale();
    }

    /** Test de connexion à Prometheus (requête "up"). */
    @GetMapping("/prometheus")
    public String getPrometheusData() {
        return metricsService.getPrometheusStatus();
    }

    @ExceptionHandler(MetricsUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleMetricsUnavailable(MetricsUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", e.getMessage()));
    }
}
