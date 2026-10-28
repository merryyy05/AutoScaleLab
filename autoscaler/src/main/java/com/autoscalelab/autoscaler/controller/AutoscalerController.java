
package com.autoscalelab.autoscaler.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autoscalelab.autoscaler.service.MetricsService;
import com.autoscalelab.autoscaler.service.ScalingService;

@RestController
@RequestMapping("/api")
public class AutoscalerController {

    private final ScalingService scalingService;
    private final MetricsService metricsService;

    public AutoscalerController(
            ScalingService scalingService,
            MetricsService metricsService) {

        this.scalingService = scalingService;
        this.metricsService = metricsService;
    }

    @GetMapping("/status")
    public String getStatus() {
        return "AutoScaleLab fonctionne !";
    }

    @GetMapping("/scaling")
    public String getScalingDecision() {
        return scalingService.checkScaling();
    }

    @GetMapping("/prometheus")
    public String getPrometheusData() {
        return metricsService.getPrometheusStatus();
    }
}
