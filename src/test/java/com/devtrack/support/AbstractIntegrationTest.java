package com.devtrack.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base para tests de integracion que necesitan la aplicacion completa.
 *
 * <p>La base de datos es un PostgreSQL real levantado con Testcontainers; la conexion se
 * inyecta en el {@code DataSource} mediante {@code @ServiceConnection}, de modo que la
 * migracion de Flyway corre contra el contenedor en cada contexto.
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(PostgresTestContainerConfiguration.class)
public abstract class AbstractIntegrationTest {}
