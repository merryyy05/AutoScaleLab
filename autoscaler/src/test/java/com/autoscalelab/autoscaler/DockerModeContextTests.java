package com.autoscalelab.autoscaler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.autoscalelab.autoscaler.client.DockerSwarmClient;
import com.autoscalelab.autoscaler.client.SwarmClient;

/**
 * Vérifie uniquement le choix du bean quand la simulation est désactivée.
 * Aucune commande Docker n'est lancée au démarrage de l'application.
 */
@SpringBootTest(properties = {
		"autoscaler.swarm.simulation=false",
		"autoscaler.scheduler.enabled=false"})
class DockerModeContextTests {

	@Autowired
	private SwarmClient swarmClient;

	@Test
	void leClientDockerEstUtiliseQuandLaSimulationEstDesactivee() {
		assertThat(swarmClient).isInstanceOf(DockerSwarmClient.class);
	}
}
