package com.devtrack.shared.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Escribe un {@link ProblemDetail} directamente en la respuesta HTTP.
 *
 * <p>Hace falta porque los errores de seguridad generados dentro de la cadena de filtros
 * de Spring Security <em>no</em> pasan por el {@code @RestControllerAdvice}: sin esta
 * pieza, un 401 devolveria un cuerpo vacio o con otro formato que el resto de la API
 * (ADR-0005).
 */
@Component
public class ProblemDetailWriter {

    private final ObjectMapper objectMapper;

    public ProblemDetailWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializa el detalle de problema con el codigo HTTP y el tipo de contenido correctos.
     *
     * @param response respuesta en curso
     * @param problem detalle de problema
     */
    public void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
