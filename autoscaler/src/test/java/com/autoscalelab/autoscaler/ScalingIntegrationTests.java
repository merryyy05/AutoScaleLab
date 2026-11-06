package com.autoscalelab.autoscaler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.autoscalelab.autoscaler.client.SwarmClient;

/**
 * Application complète en mode simulation, avec un Prometheus injoignable (port 1).
 * Vérifie de bout en bout qu'aucun réplica n'est modifié dans ce cas.
 */
@SpringBootTest(properties = {
		"autoscaler.scheduler.enabled=false",
		"prometheus.url=http://localhost:1",
		"prometheus.connect-timeout=1s",
		"prometheus.read-timeout=1s"})
class ScalingIntegrationTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private SwarmClient swarmClient;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void prometheusIndisponibleAucunScaling() throws Exception {
		int before = swarmClient.getReplicas("autoscalelab_app");

		mvc.perform(get("/api/status")).andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("SIMULATION"))
				.andExpect(jsonPath("$.cpuQueryWarning").exists());
		mvc.perform(get("/api/scaling")).andExpect(status().isOk());

		mvc.perform(post("/api/scaling/check")).andExpect(status().isOk())
				.andExpect(jsonPath("$.action").value("NO_ACTION"))
				.andExpect(jsonPath("$.error").value(true));

		mvc.perform(get("/api/scaling")).andExpect(status().isOk())
				.andExpect(jsonPath("$.action").value("NO_ACTION"));

		assertThat(swarmClient.getReplicas("autoscalelab_app")).isEqualTo(before);
	}
}
