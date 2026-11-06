package com.autoscalelab.autoscaler.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.autoscalelab.autoscaler.client.SwarmClient;
import com.autoscalelab.autoscaler.config.AutoscalerProperties;

/**
 * Logique de décision de l'autoscaler.
 *
 * Un cycle : lire le CPU → lire les réplicas réels → décider (+1 / -1 / rien)
 * → vérifier le cooldown → appliquer. En cas d'erreur à n'importe quelle étape,
 * aucun réplica n'est modifié.
 */
@Service
public class ScalingService {

    private static final Logger log = LoggerFactory.getLogger(ScalingService.class);

    private final CpuMetricsProvider cpuMetrics;
    private final SwarmClient swarmClient;
    private final AutoscalerProperties properties;
    private final Clock clock;

    /** Moment du dernier changement réussi (null = jamais). */
    private Instant lastScaleTime;
    private volatile ScalingDecision lastDecision;

    public ScalingService(
            CpuMetricsProvider cpuMetrics,
            SwarmClient swarmClient,
            AutoscalerProperties properties,
            Clock clock) {

        this.cpuMetrics = cpuMetrics;
        this.swarmClient = swarmClient;
        this.properties = properties;
        this.clock = clock;
    }

    /** Exécute un cycle complet et retourne la décision prise. */
    public synchronized ScalingDecision evaluateAndScale() {

        ScalingDecision decision = evaluate();
        lastDecision = decision;

        if (decision.error()) {
            log.warn(decision.summary());
        } else {
            log.info(decision.summary());
        }
        return decision;
    }

    /** Dernière décision prise (lecture seule, ne déclenche rien). */
    public Optional<ScalingDecision> getLastDecision() {
        return Optional.ofNullable(lastDecision);
    }

    /** Moment du dernier changement réussi (lecture seule). */
    public synchronized Optional<Instant> getLastScaleTime() {
        return Optional.ofNullable(lastScaleTime);
    }

    private ScalingDecision evaluate() {

        Instant now = clock.instant();
        String service = properties.serviceName();
        int min = properties.minReplicas();
        int max = properties.maxReplicas();

        // 1. CPU
        double cpu;
        try {
            cpu = cpuMetrics.getCpuUsage();
        } catch (RuntimeException e) {
            return ScalingDecision.failure(now, null, null,
                    "métrique CPU indisponible (" + e.getMessage() + ") – aucun changement");
        }
        if (!Double.isFinite(cpu) || cpu < 0) {
            return ScalingDecision.failure(now, null, null,
                    "métrique CPU invalide (" + cpu + ") – aucun changement");
        }

        // 2. Réplicas réels
        int current;
        try {
            current = swarmClient.getReplicas(service);
        } catch (RuntimeException e) {
            return ScalingDecision.failure(now, cpu, null,
                    "nombre de réplicas illisible (" + e.getMessage() + ") – aucun changement");
        }

        // 3. Décision
        int target;
        String reason;

        if (current > max || current < min) {
            target = current > max ? max : min;
            reason = String.format(Locale.FRANCE,
                    "réplicas hors limites [%d-%d] : %d → %d (cpu=%s %%)",
                    min, max, current, target, fmt(cpu));

        } else if (cpu > properties.cpuHigh()) {
            if (current >= max) {
                return ScalingDecision.noAction(now, cpu, current, String.format(Locale.FRANCE,
                        "cpu=%s %% > %s %% mais limite max atteinte (%d réplicas)",
                        fmt(cpu), fmt(properties.cpuHigh()), current));
            }
            target = current + 1;
            reason = String.format(Locale.FRANCE, "cpu=%s %% > %s %%, réplicas %d → %d",
                    fmt(cpu), fmt(properties.cpuHigh()), current, target);

        } else if (cpu < properties.cpuLow()) {
            if (current <= min) {
                return ScalingDecision.noAction(now, cpu, current, String.format(Locale.FRANCE,
                        "cpu=%s %% < %s %% mais limite min atteinte (%d réplica(s))",
                        fmt(cpu), fmt(properties.cpuLow()), current));
            }
            target = current - 1;
            reason = String.format(Locale.FRANCE, "cpu=%s %% < %s %%, réplicas %d → %d",
                    fmt(cpu), fmt(properties.cpuLow()), current, target);

        } else {
            return ScalingDecision.noAction(now, cpu, current, String.format(Locale.FRANCE,
                    "cpu=%s %%, réplicas %d (dans la plage normale %s–%s %%)",
                    fmt(cpu), current, fmt(properties.cpuLow()), fmt(properties.cpuHigh())));
        }

        // 4. Cooldown
        long remaining = remainingCooldownSeconds(now);
        if (remaining > 0) {
            return ScalingDecision.noAction(now, cpu, current,
                    "cooldown actif (encore " + remaining + " s), changement ignoré : " + reason);
        }

        // 5. Application
        try {
            swarmClient.scaleService(service, target);
        } catch (RuntimeException e) {
            return ScalingDecision.failure(now, cpu, current,
                    "échec du scaling " + current + " → " + target
                    + " (" + e.getMessage() + ") – cooldown non démarré");
        }

        lastScaleTime = now;
        return ScalingDecision.scaled(now, cpu, current, target, reason);
    }

    private long remainingCooldownSeconds(Instant now) {
        if (lastScaleTime == null) {
            return 0;
        }
        Duration elapsed = Duration.between(lastScaleTime, now);
        Duration remaining = properties.cooldown().minus(elapsed);
        if (!remaining.isPositive()) {
            return 0;
        }
        return (remaining.toMillis() + 999) / 1000; // arrondi supérieur
    }

    /** 75.0 → "75", 82.43 → "82,4". */
    private static String fmt(double value) {
        if (value == Math.rint(value)) {
            return String.format(Locale.FRANCE, "%.0f", value);
        }
        return String.format(Locale.FRANCE, "%.1f", value);
    }
}
