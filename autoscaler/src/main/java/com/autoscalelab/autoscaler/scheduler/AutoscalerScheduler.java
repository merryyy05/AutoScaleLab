package com.autoscalelab.autoscaler.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.autoscalelab.autoscaler.service.ScalingService;

/**
 * Boucle automatique : lance un cycle de scaling à intervalle régulier.
 *
 * fixedDelay : l'intervalle est compté à partir de la FIN du cycle précédent,
 * donc deux cycles ne se chevauchent jamais.
 *
 * Désactivable avec {@code autoscaler.scheduler.enabled=false} (utilisé dans les tests).
 */
@Component
@ConditionalOnProperty(name = "autoscaler.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class AutoscalerScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoscalerScheduler.class);

    private final ScalingService scalingService;

    public AutoscalerScheduler(ScalingService scalingService) {
        this.scalingService = scalingService;
    }

    @Scheduled(
            fixedDelayString = "${autoscaler.check-interval:15s}",
            initialDelayString = "${autoscaler.initial-delay:10s}")
    public void run() {
        try {
            scalingService.evaluateAndScale();
        } catch (RuntimeException e) {
            // Ne jamais laisser une erreur inattendue arrêter la boucle
            log.error("Erreur inattendue pendant le cycle de scaling – aucun changement", e);
        }
    }
}
