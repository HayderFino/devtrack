package com.devtrack.shared.exception;

import java.net.URI;
import java.time.Instant;

import org.springframework.http.ProblemDetail;

/**
 * Fabrica de {@link ProblemDetail} con el formato unico de la API (ADR-0005).
 *
 * <p>Todos los errores de DevTrack se serializan como {@code application/problem+json} con
 * los campos {@code type}, {@code title}, {@code status}, {@code detail}, {@code instance},
 * {@code code} y {@code timestamp}.
 */
public final class ProblemDetails {

    private ProblemDetails() {}

    /**
     * Construye el detalle de problema a partir de un codigo del catalogo.
     *
     * @param code codigo del catalogo
     * @param detail explicacion concreta, sin datos de otros usuarios
     * @param instance ruta que ha fallado
     */
    public static ProblemDetail of(ErrorCode code, String detail, String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        problem.setType(URI.create(code.type()));
        problem.setInstance(URI.create(instance));
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
