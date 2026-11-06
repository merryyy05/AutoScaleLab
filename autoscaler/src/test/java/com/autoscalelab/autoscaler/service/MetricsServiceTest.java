package com.autoscalelab.autoscaler.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.autoscalelab.autoscaler.config.AutoscalerProperties;
import com.autoscalelab.autoscaler.support.TestFixtures;

/** Lecture des réponses Prometheus, sans Prometheus réel. */
class MetricsServiceTest {

    private static String vector(String... values) {
        StringBuilder results = new StringBuilder();
        for (String v : values) {
            if (!results.isEmpty()) {
                results.append(',');
            }
            results.append("{\"metric\":{},\"value\":[1762423200.123,\"").append(v).append("\"]}");
        }
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":["
                + results + "]}}";
    }

    @Test
    void litUneValeurCorrecte() {
        assertThat(MetricsService.parseCpu(vector("82.5"))).isEqualTo(82.5);
        assertThat(MetricsService.parseCpu(vector("0"))).isZero();
    }

    @Test
    void refuseUneErreurPrometheus() {
        String json = "{\"status\":\"error\",\"errorType\":\"bad_data\",\"error\":\"parse error\"}";

        assertThatThrownBy(() -> MetricsService.parseCpu(json))
                .isInstanceOf(MetricsUnavailableException.class)
                .hasMessageContaining("parse error");
    }

    @Test
    void refuseUnResultatVide() {
        assertThatThrownBy(() -> MetricsService.parseCpu(vector()))
                .isInstanceOf(MetricsUnavailableException.class)
                .hasMessageContaining("Aucune métrique");
    }

    @Test
    void refusePlusieursSeries() {
        assertThatThrownBy(() -> MetricsService.parseCpu(vector("10", "90")))
                .isInstanceOf(MetricsUnavailableException.class)
                .hasMessageContaining("2 séries");
    }

    @Test
    void refuseLesValeursInvalides() {
        for (String invalid : new String[] {"NaN", "+Inf", "-Inf", "-3", "abc"}) {
            assertThatThrownBy(() -> MetricsService.parseCpu(vector(invalid)))
                    .as("valeur %s", invalid)
                    .isInstanceOf(MetricsUnavailableException.class);
        }
    }

    @Test
    void refuseUnJsonInvalideOuVide() {
        for (String bad : new String[] {null, "", "pas du json", "{\"status\":\"success\"}",
                "{\"status\":\"success\",\"data\":{\"result\":[{\"value\":[1]}]}}"}) {
            assertThatThrownBy(() -> MetricsService.parseCpu(bad))
                    .as("réponse %s", bad)
                    .isInstanceOf(MetricsUnavailableException.class);
        }
    }

    @Test
    void prometheusInjoignableDonneUneErreurPropre() {
        // Port 1 : aucun service n'écoute, connexion refusée
        MetricsService metrics = new MetricsService(TestFixtures.properties(),
                "http://localhost:1", Duration.ofSeconds(2), Duration.ofSeconds(2));

        long start = System.nanoTime();
        assertThatThrownBy(metrics::getCpuUsage)
                .isInstanceOf(MetricsUnavailableException.class)
                .hasMessageContaining("Prometheus injoignable");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(15));
    }

    @Test
    void detecteSiLaRequeteNeVisePasLeService() {
        AutoscalerProperties p = TestFixtures.properties();
        AutoscalerProperties machineEntiere = new AutoscalerProperties(
                p.cpuHigh(), p.cpuLow(), p.minReplicas(), p.maxReplicas(), p.serviceName(),
                p.initialReplicas(), p.checkInterval(), p.cooldown(),
                "rate(container_cpu_usage_seconds_total{id=\"/\"}[1m]) * 100");

        assertThat(new MetricsService(p, "http://localhost:1", Duration.ofSeconds(1), Duration.ofSeconds(1))
                .isCpuQueryTargetingService()).isTrue();
        assertThat(new MetricsService(machineEntiere, "http://localhost:1", Duration.ofSeconds(1), Duration.ofSeconds(1))
                .isCpuQueryTargetingService()).isFalse();
    }
}
