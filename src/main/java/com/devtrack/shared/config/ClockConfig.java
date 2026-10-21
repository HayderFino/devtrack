package com.devtrack.shared.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Proporciona un {@link Clock} inyectable.
 *
 * <p>Centralizar el reloj en un bean permite sustituirlo por uno fijo en los tests y evita
 * que la logica de negocio dependa de {@code Instant.now()} disperso por el codigo.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
