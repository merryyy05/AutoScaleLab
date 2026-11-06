package com.autoscalelab.autoscaler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import com.autoscalelab.autoscaler.scheduler.AutoscalerScheduler;

/**
 * Vérifie que la tâche planifiée est bien enregistrée quand elle est activée.
 * Le premier cycle est repoussé à 1 h : aucun cycle ne s'exécute pendant le test.
 */
@SpringBootTest(properties = {
		"autoscaler.scheduler.enabled=true",
		"autoscaler.initial-delay=1h",
		"autoscaler.check-interval=1h",
		"prometheus.url=http://localhost:1"})
class SchedulerEnabledContextTests {

	@Autowired
	private ApplicationContext context;

	@Test
	void laTachePlanifieeEstEnregistree() {
		assertThat(context.getBeansOfType(AutoscalerScheduler.class)).hasSize(1);

		assertThat(context.getBeansOfType(ScheduledTaskHolder.class).values())
				.flatMap(ScheduledTaskHolder::getScheduledTasks)
				.anySatisfy(task -> assertThat(task.getTask().toString()).contains("AutoscalerScheduler"));
	}
}
