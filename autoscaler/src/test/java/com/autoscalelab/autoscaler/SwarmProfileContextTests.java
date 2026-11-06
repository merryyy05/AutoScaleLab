package com.autoscalelab.autoscaler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import com.autoscalelab.autoscaler.client.DockerSwarmClient;
import com.autoscalelab.autoscaler.client.SwarmClient;

/**
 * Profil "swarm" : le client Docker réel est sélectionné.
 * La tâche planifiée est désactivée : aucune commande Docker n'est exécutée.
 */
@SpringBootTest(properties = "autoscaler.scheduler.enabled=false")
@ActiveProfiles("swarm")
class SwarmProfileContextTests {

	@Autowired
	private SwarmClient swarmClient;

	@Autowired
	private Environment environment;

	@Test
	void leProfilSwarmActiveLeModeReel() {
		assertThat(environment.getProperty("autoscaler.swarm.simulation")).isEqualTo("false");
		assertThat(swarmClient).isInstanceOf(DockerSwarmClient.class);
	}

	@Test
	void leProfilSwarmNeChangeQueLeMode() {
		assertThat(environment.getProperty("server.port")).isEqualTo("8085");
		assertThat(environment.getProperty("autoscaler.cpu-query"))
				.isEqualTo("avg(docker_container_cpu_percent{service=\"autoscalelab_app\"})");
	}
}
