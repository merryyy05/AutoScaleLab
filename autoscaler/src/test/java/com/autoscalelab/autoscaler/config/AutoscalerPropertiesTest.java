package com.autoscalelab.autoscaler.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AutoscalerPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(AutoscalerProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "autoscaler.cpu-high=75",
                    "autoscaler.cpu-low=30",
                    "autoscaler.min-replicas=1",
                    "autoscaler.max-replicas=5",
                    "autoscaler.service-name=autoscalelab_app",
                    "autoscaler.cpu-query=avg(cpu)");

    private static AutoscalerProperties props(double high, double low, int min, int max, Duration cooldown) {
        return new AutoscalerProperties(high, low, min, max, "autoscalelab_app", 1,
                Duration.ofSeconds(15), cooldown, "avg(cpu)");
    }

    @Test
    void lesValeursDuProjetSontAcceptees() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            AutoscalerProperties p = context.getBean(AutoscalerProperties.class);
            assertThat(p.cpuHigh()).isEqualTo(75);
            assertThat(p.maxReplicas()).isEqualTo(5);
            // valeurs par défaut
            assertThat(p.cooldown()).isEqualTo(Duration.ofSeconds(60));
            assertThat(p.checkInterval()).isEqualTo(Duration.ofSeconds(15));
        });
    }

    @Test
    void lesDureesSontLuesDepuisLaConfiguration() {
        runner.withPropertyValues("autoscaler.cooldown=2m", "autoscaler.check-interval=5s")
                .run(context -> {
                    AutoscalerProperties p = context.getBean(AutoscalerProperties.class);
                    assertThat(p.cooldown()).isEqualTo(Duration.ofMinutes(2));
                    assertThat(p.checkInterval()).isEqualTo(Duration.ofSeconds(5));
                });
    }

    @Test
    void desSeuilsInversesEmpechentLeDemarrage() {
        runner.withPropertyValues("autoscaler.cpu-low=80").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause().hasMessageContaining("cpu-high");
        });
    }

    @Test
    void minSuperieurAMaxEmpecheLeDemarrage() {
        runner.withPropertyValues("autoscaler.min-replicas=6").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause().hasMessageContaining("max-replicas");
        });
    }

    @Test
    void lesValeursIncoherentesSontRefusees() {
        assertThatThrownBy(() -> props(30, 30, 1, 5, Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> props(75, -1, 1, 5, Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> props(75, 30, 0, 5, Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("min-replicas");
        assertThatThrownBy(() -> props(75, 30, 1, 5, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cooldown");
        assertThatThrownBy(() -> new AutoscalerProperties(75, 30, 1, 5, " ", 1,
                Duration.ofSeconds(15), Duration.ofSeconds(60), "avg(cpu)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("service-name");
        assertThatThrownBy(() -> new AutoscalerProperties(75, 30, 1, 5, "app", 1,
                Duration.ZERO, Duration.ofSeconds(60), "avg(cpu)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("check-interval");
    }
}
