package com.autoscalelab.autoscaler.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active les tâches planifiées seulement si {@code autoscaler.scheduler.enabled=true}
 * (valeur par défaut). Les tests la désactivent.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "autoscaler.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
