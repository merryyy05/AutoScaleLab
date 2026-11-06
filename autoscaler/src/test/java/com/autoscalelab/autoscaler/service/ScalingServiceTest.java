package com.autoscalelab.autoscaler.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.autoscalelab.autoscaler.client.SwarmException;
import com.autoscalelab.autoscaler.service.ScalingDecision.Action;
import com.autoscalelab.autoscaler.support.TestFixtures;
import com.autoscalelab.autoscaler.support.TestFixtures.FakeCpu;
import com.autoscalelab.autoscaler.support.TestFixtures.FakeSwarm;
import com.autoscalelab.autoscaler.support.TestFixtures.MutableClock;

class ScalingServiceTest {

    private FakeCpu cpu;
    private FakeSwarm swarm;
    private MutableClock clock;
    private ScalingService service;

    @BeforeEach
    void setUp() {
        cpu = new FakeCpu();
        swarm = new FakeSwarm();
        clock = new MutableClock();
        service = new ScalingService(cpu, swarm, TestFixtures.properties(), clock);
    }

    private ScalingDecision check(double cpuValue, int replicas) {
        cpu.value = cpuValue;
        swarm.replicas = replicas;
        return service.evaluateAndScale();
    }

    @Nested
    class Seuils {

        @Test
        void cpuEleveAjouteUnReplica() {
            ScalingDecision d = check(82.4, 2);

            assertThat(d.action()).isEqualTo(Action.SCALE_UP);
            assertThat(d.replicasBefore()).isEqualTo(2);
            assertThat(d.replicasAfter()).isEqualTo(3);
            assertThat(d.error()).isFalse();
            assertThat(swarm.scaleCalls).containsExactly(3);
            assertThat(d.summary()).isEqualTo("SCALE UP : cpu=82,4 % > 75 %, réplicas 2 → 3");
        }

        @Test
        void cpuBasRetireUnReplica() {
            ScalingDecision d = check(12, 3);

            assertThat(d.action()).isEqualTo(Action.SCALE_DOWN);
            assertThat(d.replicasAfter()).isEqualTo(2);
            assertThat(swarm.scaleCalls).containsExactly(2);
            assertThat(d.summary()).startsWith("SCALE DOWN");
        }

        @Test
        void cpuNormalNeChangeRien() {
            ScalingDecision d = check(50, 2);

            assertThat(d.action()).isEqualTo(Action.NO_ACTION);
            assertThat(d.error()).isFalse();
            assertThat(d.reason()).contains("plage normale");
            assertThat(swarm.scaleCalls).isEmpty();
        }

        @Test
        void exactementSurLesSeuilsNeChangeRien() {
            assertThat(check(75, 2).action()).isEqualTo(Action.NO_ACTION);
            assertThat(check(30, 2).action()).isEqualTo(Action.NO_ACTION);
            assertThat(swarm.scaleCalls).isEmpty();
        }

        @Test
        void lesReplicasSontLusDansSwarmAChaqueCycle() {
            check(50, 2);
            check(50, 4);

            assertThat(swarm.getCalls).isEqualTo(2);
            assertThat(service.getLastDecision()).get()
                    .extracting(ScalingDecision::replicasBefore).isEqualTo(4);
        }
    }

    @Nested
    class Limites {

        @Test
        void pasDeScaleUpAuMaximum() {
            ScalingDecision d = check(95, 5);

            assertThat(d.action()).isEqualTo(Action.NO_ACTION);
            assertThat(d.reason()).contains("limite max");
            assertThat(swarm.scaleCalls).isEmpty();
        }

        @Test
        void pasDeScaleDownAuMinimum() {
            ScalingDecision d = check(5, 1);

            assertThat(d.action()).isEqualTo(Action.NO_ACTION);
            assertThat(d.reason()).contains("limite min");
            assertThat(swarm.scaleCalls).isEmpty();
        }

        @Test
        void auDessusDuMaximumOnRevientAuMaximum() {
            ScalingDecision d = check(50, 8);

            assertThat(d.action()).isEqualTo(Action.SCALE_DOWN);
            assertThat(d.replicasAfter()).isEqualTo(5);
            assertThat(d.reason()).contains("hors limites");
            assertThat(swarm.scaleCalls).containsExactly(5);
        }

        @Test
        void sousLeMinimumOnRevientAuMinimum() {
            ScalingDecision d = check(10, 0);

            assertThat(d.action()).isEqualTo(Action.SCALE_UP);
            assertThat(d.replicasAfter()).isEqualTo(1);
            assertThat(swarm.scaleCalls).containsExactly(1);
        }

        @Test
        void laCorrectionHorsLimitesRespecteLeCooldown() {
            check(90, 2);                      // changement → cooldown démarre
            clock.advance(Duration.ofSeconds(10));

            ScalingDecision d = check(50, 9);

            assertThat(d.action()).isEqualTo(Action.NO_ACTION);
            assertThat(d.reason()).contains("cooldown");
            assertThat(swarm.scaleCalls).containsExactly(3);
        }
    }

    @Nested
    class Cooldown {

        @Test
        void unDeuxiemeChangementTropRapideEstBloque() {
            check(90, 1);
            clock.advance(Duration.ofSeconds(30));

            ScalingDecision d = check(90, 2);

            assertThat(d.action()).isEqualTo(Action.NO_ACTION);
            assertThat(d.error()).isFalse();
            assertThat(d.reason()).contains("cooldown actif (encore 30 s)");
            assertThat(swarm.scaleCalls).containsExactly(2);
        }

        @Test
        void apresLeCooldownLeChangementEstAutorise() {
            check(90, 1);
            clock.advance(Duration.ofSeconds(60));

            ScalingDecision d = check(90, 2);

            assertThat(d.action()).isEqualTo(Action.SCALE_UP);
            assertThat(swarm.scaleCalls).containsExactly(2, 3);
        }

        @Test
        void leCooldownBloqueAussiLeScaleDown() {
            check(90, 1);
            clock.advance(Duration.ofSeconds(59));

            assertThat(check(5, 2).action()).isEqualTo(Action.NO_ACTION);
            assertThat(swarm.scaleCalls).containsExactly(2);
        }

        @Test
        void unCycleSansChangementNeDemarrePasLeCooldown() {
            check(50, 2);
            assertThat(service.getLastScaleTime()).isEmpty();

            assertThat(check(90, 2).action()).isEqualTo(Action.SCALE_UP);
        }

        @Test
        void unEchecDeScalingNeDemarrePasLeCooldown() {
            swarm.scaleError = new SwarmException("échec simulé");
            ScalingDecision failed = check(90, 2);

            assertThat(failed.action()).isEqualTo(Action.NO_ACTION);
            assertThat(failed.error()).isTrue();
            assertThat(failed.reason()).contains("échec du scaling 2 → 3", "cooldown non démarré");
            assertThat(service.getLastScaleTime()).isEmpty();

            swarm.scaleError = null;
            clock.advance(Duration.ofSeconds(15));
            assertThat(check(90, 2).action()).isEqualTo(Action.SCALE_UP);
        }
    }

    @Nested
    class Erreurs {

        @Test
        void prometheusIndisponibleAucunChangement() {
            cpu.unavailable();
            swarm.replicas = 3;

            ScalingDecision d = service.evaluateAndScale();

            assertThat(d.action()).isEqualTo(Action.NO_ACTION);
            assertThat(d.error()).isTrue();
            assertThat(d.cpu()).isNull();
            assertThat(d.reason()).contains("métrique CPU indisponible");
            assertThat(swarm.scaleCalls).isEmpty();
            assertThat(swarm.getCalls).isZero();
        }

        @Test
        void erreurInattendueDuCpuAucunChangement() {
            cpu.error = new IllegalStateException("bug");

            assertThat(service.evaluateAndScale().error()).isTrue();
            assertThat(swarm.scaleCalls).isEmpty();
        }

        @Test
        void valeurCpuInvalideAucunChangement() {
            for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1}) {
                ScalingDecision d = check(invalid, 2);

                assertThat(d.error()).isTrue();
                assertThat(d.reason()).contains("métrique CPU invalide");
            }
            assertThat(swarm.scaleCalls).isEmpty();
        }

        @Test
        void replicasIllisiblesAucunChangement() {
            cpu.value = 95;
            swarm.dockerDown();

            ScalingDecision d = service.evaluateAndScale();

            assertThat(d.action()).isEqualTo(Action.NO_ACTION);
            assertThat(d.error()).isTrue();
            assertThat(d.reason()).contains("réplicas illisible");
            assertThat(swarm.scaleCalls).isEmpty();
        }

        @Test
        void laDerniereDecisionEstMemorisee() {
            assertThat(service.getLastDecision()).isEmpty();

            ScalingDecision d = check(50, 2);

            assertThat(service.getLastDecision()).contains(d);
        }
    }
}
