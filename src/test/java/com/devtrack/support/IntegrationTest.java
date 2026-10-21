package com.devtrack.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Anotacion meta para los tests de integracion.
 *
 * <p>Evita repetir en cada clase el conjunto de anotaciones necesarias para levantar la
 * aplicacion contra un PostgreSQL real (Testcontainers).
 *
 * <p><strong>H2 esta prohibido</strong> en los tests de integracion: el dialecto SQL debe
 * coincidir con el de produccion (riesgo R-09 de {@code docs/architecture.md}).
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
public @interface IntegrationTest {
}
