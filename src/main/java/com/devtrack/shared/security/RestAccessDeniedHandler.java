package com.devtrack.shared.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.devtrack.shared.exception.ErrorCode;
import com.devtrack.shared.exception.ProblemDetails;
import com.devtrack.shared.web.ProblemDetailWriter;

/**
 * Respuesta 403 para peticiones autenticadas sin permiso suficiente.
 *
 * <p>Se ejecuta dentro de la cadena de filtros. El 403 por rol insuficiente lo produce
 * normalmente el {@code @RestControllerAdvice}; este handler cubre el caso en que el
 * permiso se nega antes de llegar al controlador.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailWriter problemDetailWriter;

    public RestAccessDeniedHandler(ProblemDetailWriter problemDetailWriter) {
        this.problemDetailWriter = problemDetailWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {

        var problem = ProblemDetails.of(
                ErrorCode.FORBIDDEN, "No tienes permiso para realizar esta operacion", request.getRequestURI());
        problemDetailWriter.write(response, problem);
    }
}
