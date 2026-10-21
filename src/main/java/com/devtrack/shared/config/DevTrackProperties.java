package com.devtrack.shared.config;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades tipadas de la aplicacion, validadas en el arranque.
 *
 * <p>La validacion con Bean Validation sobre un {@code @ConfigurationProperties} hace que
 * el contexto de Spring falle al arrancar si falta o es invalido un valor critico. Es el
 * mecanismo que implementa el <em>fail-fast</em> del secreto JWT (ADR-0003, riesgo R-04):
 * es preferible a arrancar con un secreto inseguro por defecto.
 *
 * @param jwt configuracion de emision y validacion de tokens
 * @param cors origenes permitidos por CORS
 */
@Validated
@ConfigurationProperties(prefix = "devtrack")
public record DevTrackProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull Cors cors) {

    /**
     * Configuracion de JWT.
     *
     * @param secret clave de firma; minimo 32 caracteres
     * @param issuer emisor declarado en el claim {@code iss}
     * @param accessTokenTtl vigencia del token de acceso
     */
    public record Jwt(
            @NotBlank(message = "devtrack.jwt.secret es obligatorio")
            @Size(min = 32, message = "devtrack.jwt.secret debe tener al menos 32 caracteres")
            String secret,
            @NotBlank(message = "devtrack.jwt.issuer es obligatorio") String issuer,
            @NotNull(message = "devtrack.jwt.access-token-ttl es obligatorio") Duration accessTokenTtl) {}

    /**
     * Configuracion de CORS.
     *
     * @param allowedOrigins origenes permitidos; nunca {@code *}
     */
    public record Cors(@NotEmpty(message = "devtrack.cors.allowed-origins no puede estar vacio") List<String> allowedOrigins) {}
}
