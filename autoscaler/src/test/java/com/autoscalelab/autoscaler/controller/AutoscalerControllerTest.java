package com.autoscalelab.autoscaler.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.autoscalelab.autoscaler.config.AutoscalerProperties;
import com.autoscalelab.autoscaler.service.MetricsService;
import com.autoscalelab.autoscaler.service.ScalingService;
import com.autoscalelab.autoscaler.support.TestFixtures;
import com.autoscalelab.autoscaler.support.TestFixtures.FakeCpu;
import com.autoscalelab.autoscaler.support.TestFixtures.FakeSwarm;
import com.autoscalelab.autoscaler.support.TestFixtures.MutableClock;

/** Les GET ne doivent jamais modifier les réplicas ; seul le POST déclenche un cycle. */
class AutoscalerControllerTest {

    private FakeCpu cpu;
    private FakeSwarm swarm;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AutoscalerProperties props = TestFixtures.properties();
        cpu = new FakeCpu();
        cpu.value = 95; // un cycle déclencherait un SCALE UP
        swarm = new FakeSwarm();
        swarm.replicas = 2;

        ScalingService scaling = new ScalingService(cpu, swarm, props, new MutableClock());
        MetricsService metrics = new MetricsService(props, "http://localhost:1",
                Duration.ofSeconds(1), Duration.ofSeconds(1));

        mvc = MockMvcBuilders
                .standaloneSetup(new AutoscalerController(scaling, metrics, swarm, props, false))
                .build();
    }

    @Test
    void lesGetNeModifientPasLesReplicas() throws Exception {
        mvc.perform(get("/api/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DOCKER"))
                .andExpect(jsonPath("$.service").value("autoscalelab_app"))
                .andExpect(jsonPath("$.schedulerEnabled").value(false));

        mvc.perform(get("/api/scaling")).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        assertThat(cpu.calls).isZero();
        assertThat(swarm.getCalls).isZero();
        assertThat(swarm.scaleCalls).isEmpty();
        assertThat(swarm.replicas).isEqualTo(2);
    }

    @Test
    void lePostDeclencheUnCycleEtLeGetAfficheLaDecision() throws Exception {
        mvc.perform(post("/api/scaling/check")).andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("SCALE_UP"))
                .andExpect(jsonPath("$.replicasBefore").value(2))
                .andExpect(jsonPath("$.replicasAfter").value(3));

        assertThat(swarm.scaleCalls).containsExactly(3);

        // Relire plusieurs fois ne refait pas de scaling
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/scaling")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.action").value("SCALE_UP"));
        }
        assertThat(swarm.scaleCalls).containsExactly(3);
        assertThat(cpu.calls).isEqualTo(1);
    }

    @Test
    void getScalingNAcceptePlusDeDeclencher() throws Exception {
        mvc.perform(get("/api/scaling/check")).andExpect(status().isMethodNotAllowed());
        assertThat(swarm.scaleCalls).isEmpty();
    }

    @Test
    void prometheusInjoignableRenvoie503() throws Exception {
        mvc.perform(get("/api/prometheus"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }
}
