package com.autoscalelab.autoscaler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import com.autoscalelab.autoscaler.client.SimulatedSwarmClient;
import com.autoscalelab.autoscaler.client.SwarmClient;
import com.autoscalelab.autoscaler.config.AutoscalerProperties;
import com.autoscalelab.autoscaler.service.MetricsService;

/** Vérifie les valeurs livrées dans application.properties (sans profil). */
@SpringBootTest(properties = "autoscaler.scheduler.enabled=false")
class DefaultConfigurationTests {

	@Autowired
	private Environment environment;

	@Autowired
	private AutoscalerProperties properties;

	@Autowired
	private MetricsService metricsService;

	@Autowired
	private SwarmClient swarmClient;

	@Test
	void lePortParDefautEst8085() {
		assertThat(environment.getProperty("server.port")).isEqualTo("8085");
	}

	@Test
	void aucunProfilActifParDefaut() {
		assertThat(environment.getActiveProfiles()).isEmpty();
		assertThat(swarmClient).isInstanceOf(SimulatedSwarmClient.class);
	}

	@Test
	void laRequeteCpuMesureLaMoyenneDuService() {
		assertThat(properties.cpuQuery())
				.isEqualTo("avg(docker_container_cpu_percent{service=\"autoscalelab_app\"})");
		assertThat(metricsService.isCpuQueryTargetingService()).isTrue();
	}

	@Test
	void lesReglesDeScalingSontCellesDuProjet() {
		assertThat(properties.serviceName()).isEqualTo("autoscalelab_app");
		assertThat(properties.cpuHigh()).isEqualTo(75);
		assertThat(properties.cpuLow()).isEqualTo(30);
		assertThat(properties.minReplicas()).isEqualTo(1);
		assertThat(properties.maxReplicas()).isEqualTo(5);
		assertThat(properties.cooldown()).isEqualTo(Duration.ofSeconds(60));
		assertThat(properties.checkInterval()).isEqualTo(Duration.ofSeconds(15));
	}
}
