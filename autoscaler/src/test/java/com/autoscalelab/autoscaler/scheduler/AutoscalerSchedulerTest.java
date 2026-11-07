package com.autoscalelab.autoscaler.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import com.autoscalelab.autoscaler.service.ScalingDecision;
import com.autoscalelab.autoscaler.service.ScalingService;
import com.autoscalelab.autoscaler.support.TestFixtures;
import com.autoscalelab.autoscaler.support.TestFixtures.FakeCpu;
import com.autoscalelab.autoscaler.support.TestFixtures.FakeSwarm;
import com.autoscalelab.autoscaler.support.TestFixtures.MutableClock;

/** Appelle run() directement : aucune boucle réelle n'est démarrée. */
class AutoscalerSchedulerTest {

    @Test
    void unCycleAppelleLeServiceDeScaling() {
        FakeCpu cpu = new FakeCpu();
        cpu.value = 90;
        FakeSwarm swarm = new FakeSwarm();
        ScalingService service = new ScalingService(cpu, swarm, TestFixtures.properties(), new MutableClock());

        new AutoscalerScheduler(service).run();

        assertThat(cpu.calls).isEqualTo(1);
        assertThat(swarm.scaleCalls).containsExactly(2);
    }

    @Test
    void uneErreurInattendueNArretePasLaBoucle() {
        ScalingService failing = new ScalingService(new FakeCpu(), new FakeSwarm(),
                TestFixtures.properties(), new MutableClock()) {
            @Override
            public synchronized ScalingDecision evaluateAndScale() {
                throw new IllegalStateException("panne inattendue");
            }
        };

        assertThatCode(() -> new AutoscalerScheduler(failing).run()).doesNotThrowAnyException();
    }
}
