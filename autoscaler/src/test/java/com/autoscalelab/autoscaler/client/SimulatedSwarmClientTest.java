package com.autoscalelab.autoscaler.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SimulatedSwarmClientTest {

    @Test
    void retourneLeNombreInitialAuDepart() {
        SimulatedSwarmClient client = new SimulatedSwarmClient(2);

        assertThat(client.getReplicas("autoscalelab_app")).isEqualTo(2);
    }

    @Test
    void retientLeNombreDeReplicasDemande() {
        SimulatedSwarmClient client = new SimulatedSwarmClient(1);

        client.scaleService("autoscalelab_app", 4);

        assertThat(client.getReplicas("autoscalelab_app")).isEqualTo(4);
        assertThat(client.getReplicas("autre_service")).isEqualTo(1);
    }

    @Test
    void refuseLesValeursInvalides() {
        SimulatedSwarmClient client = new SimulatedSwarmClient(1);

        assertThatThrownBy(() -> client.scaleService("autoscalelab_app", -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.getReplicas(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SimulatedSwarmClient(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
