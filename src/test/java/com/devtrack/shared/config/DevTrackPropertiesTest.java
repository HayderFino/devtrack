package com.devtrack.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el comportamiento fail-fast del secreto JWT (ADR-0003, riesgo R-04).
 *
 * <p>Si el secreto falta o es demasiado corto, la aplicacion <strong>no debe arrancar</strong>:
 * arrancar con un secreto inseguro por defecto es un fallo que suele llegar a produccion.
 */
class DevTrackPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "devtrack.jwt.issuer=devtrack",
                    "devtrack.jwt.access-token-ttl=PT15M",
                    "devtrack.cors.allowed-origins=http://localhost:3000");

    @Test
    @DisplayName("Arranca cuando el secreto tiene 32 caracteres o mas")
    void startsWithValidSecret() {
        contextRunner
                .withPropertyValues("devtrack.jwt.secret=01234567890123456789012345678901")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("No arranca si falta el secreto")
    void failsWithoutSecret() {
        contextRunner
                .withPropertyValues("devtrack.jwt.secret=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("devtrack.jwt.secret");
                });
    }

    @Test
    @DisplayName("No arranca si el secreto es demasiado corto")
    void failsWithTooShortSecret() {
        contextRunner
                .withPropertyValues("devtrack.jwt.secret=corto")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("al menos 32 caracteres");
                });
    }

    @Test
    @DisplayName("No arranca si la vigencia del token no es valida")
    void failsWithInvalidTtl() {
        contextRunner
                .withPropertyValues("devtrack.jwt.secret=01234567890123456789012345678901", "devtrack.jwt.access-token-ttl=PT")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DevTrackProperties.class)
    static class TestConfiguration {}
}
