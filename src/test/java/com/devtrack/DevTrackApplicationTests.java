package com.devtrack;

import com.devtrack.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de arranque del contexto completo.
 *
 * <p>Verifica que la aplicacion arranca contra un PostgreSQL real, que Flyway aplica las
 * migraciones y que la validacion del esquema ({@code ddl-auto=validate}) no detecta
 * deriva entre las entidades y el esquema.
 */
@IntegrationTest
class DevTrackApplicationTests {

    @Test
    @DisplayName("El contexto de Spring arranca con el esquema migrado")
    void contextLoads() {
        assertThat(true).isTrue();
    }
}
