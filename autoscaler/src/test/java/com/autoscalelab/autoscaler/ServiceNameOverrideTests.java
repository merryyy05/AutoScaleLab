package com.autoscalelab.autoscaler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.autoscalelab.autoscaler.config.AutoscalerProperties;

/** Changer le nom du service met aussi à jour la requête CPU (placeholder). */
@SpringBootTest(properties = {
		"autoscaler.scheduler.enabled=false",
		"autoscaler.service-name=autre_stack_web"})
class ServiceNameOverrideTests {

	@Autowired
	private AutoscalerProperties properties;

	@Test
	void laRequeteSuitLeNomDuService() {
		assertThat(properties.cpuQuery())
				.isEqualTo("avg(docker_container_cpu_percent{service=\"autre_stack_web\"})");
	}
}
