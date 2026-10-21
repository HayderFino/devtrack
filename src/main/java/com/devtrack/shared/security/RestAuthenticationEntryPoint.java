package com.devtrack.shared.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.devtrack.shared.exception.ErrorCode;
import com.devtrack.shared.exception.ProblemDetails;
import com.devtrack.shared.web.ProblemDetailWriter;

/**
 * Respuesta 401 para peticiones sin token valido.
 *
 * <p>Se ejecuta dentro de la cadena de filtros, por lo que no puede pasar por el
 * {@code @RestControllerAdvice}: construye el mismo {@code ProblemDetail} que el resto de
 * la API para que el formato sea uniforme (ADR-0005).
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailWriter problemDetailWriter;

    public RestAuthenticationEntryPoint(ProblemDetailWriter problemDetailWriter) {
        this.problemDetailWriter = problemDetailWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {

        var problem = ProblemDetails.of(
                ErrorCode.UNAUTHENTICATED, "Se requiere autenticacion valida", request.getRequestURI());
        problemDetailWriter.write(response, problem);
    }
}
