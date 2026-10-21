package com.devtrack.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Declara el contenedor de PostgreSQL usado por los tests de integracion.
 *
 * <p>El contenedor se declara como bean (y no con {@code @Container}) para que su ciclo de
 * vida lo gestione el contexto de Spring: asi se reutiliza entre clases de test que
 * comparten el mismo contexto y no se levanta un contenedor por clase.
 *
 * <p>Nota de version: en Testcontainers 2.x el modulo de PostgreSQL cambio de nombre
 * (artifactId {@code testcontainers-postgresql}) y la clase paso al paquete
 * {@code org.testcontainers.postgresql}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainerConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:18-alpine");
    }
}
