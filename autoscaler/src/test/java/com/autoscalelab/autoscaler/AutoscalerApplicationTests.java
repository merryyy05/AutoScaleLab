package com.autoscalelab.autoscaler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.autoscalelab.autoscaler.client.SimulatedSwarmClient;
import com.autoscalelab.autoscaler.client.SwarmClient;
import com.autoscalelab.autoscaler.scheduler.AutoscalerScheduler;

@SpringBootTest(properties = "autoscaler.scheduler.enabled=false")
class AutoscalerApplicationTests {

	@Autowired
	private SwarmClient swarmClient;

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
	}

	@Test
	void leModeSimulationEstActifParDefaut() {
		assertThat(swarmClient).isInstanceOf(SimulatedSwarmClient.class);
	}

	@Test
	void laTachePlanifieeEstDesactiveeDansLesTests() {
		assertThat(context.getBeansOfType(AutoscalerScheduler.class)).isEmpty();
	}
}
