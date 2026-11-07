package com.autoscalelab.autoscaler.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.autoscalelab.autoscaler.client.SwarmClient;
import com.autoscalelab.autoscaler.client.SwarmException;
import com.autoscalelab.autoscaler.config.AutoscalerProperties;
import com.autoscalelab.autoscaler.service.CpuMetricsProvider;
import com.autoscalelab.autoscaler.service.MetricsUnavailableException;

/** Faux objets partagés par les tests (aucun appel Docker ni Prometheus). */
public final class TestFixtures {

    public static final String SERVICE = "autoscalelab_app";

    private TestFixtures() {
    }

    /** Configuration du projet : 75 % / 30 %, 1 à 5 réplicas, cooldown 60 s. */
    public static AutoscalerProperties properties() {
        return new AutoscalerProperties(75, 30, 1, 5, SERVICE, 1,
                Duration.ofSeconds(15), Duration.ofSeconds(60),
                "avg(test_cpu{service=\"autoscalelab_app\"})");
    }

    /** CPU contrôlé par le test. */
    public static class FakeCpu implements CpuMetricsProvider {
        public double value = 50;
        public RuntimeException error;
        public int calls;

        @Override
        public double getCpuUsage() {
            calls++;
            if (error != null) {
                throw error;
            }
            return value;
        }

        public void unavailable() {
            error = new MetricsUnavailableException("Prometheus injoignable");
        }
    }

    /** Swarm contrôlé par le test : enregistre les appels à scaleService. */
    public static class FakeSwarm implements SwarmClient {
        public int replicas = 1;
        public RuntimeException getError;
        public RuntimeException scaleError;
        public final List<Integer> scaleCalls = new ArrayList<>();
        public int getCalls;

        @Override
        public int getReplicas(String serviceName) {
            getCalls++;
            if (getError != null) {
                throw getError;
            }
            return replicas;
        }

        @Override
        public void scaleService(String serviceName, int newReplicas) {
            scaleCalls.add(newReplicas);
            if (scaleError != null) {
                throw scaleError;
            }
            replicas = newReplicas;
        }

        public void dockerDown() {
            getError = new SwarmException("Docker Desktop ne répond pas");
        }
    }

    /** Horloge que le test peut avancer à la main. */
    public static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-06T10:00:00Z");

        public void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
