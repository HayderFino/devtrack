package com.devtrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada de la aplicacion DevTrack.
 *
 * <p>DevTrack es un monolito modular organizado por funcionalidad (ADR-0001): los contextos
 * {@code user}, {@code project} y {@code task} dependen en un solo sentido y comparten el
 * paquete {@code shared} para concerns transversales.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DevTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevTrackApplication.class, args);
    }
}
