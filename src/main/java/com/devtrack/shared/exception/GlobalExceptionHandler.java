package com.devtrack.shared.exception;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Punto unico de traduccion de excepciones a respuestas HTTP.
 *
 * <p>Extiende {@link ResponseEntityExceptionHandler} para que las excepciones estándar de
 * Spring MVC (validacion, JSON malformado, metodo no permitido...) compartan exactamente el
 * mismo formato que las excepciones de dominio.
 *
 * <p>Los errores generados <em>dentro</em> de la cadena de filtros de Spring Security no
 * pasan por aqui: los gestiona {@code RestAuthenticationEntryPoint} y
 * {@code RestAccessDeniedHandler}, que construyen el mismo {@code ProblemDetail}
 * (ver ADR-0005 y el riesgo R-06).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Excepciones de dominio: el codigo de error lo aporta la propia excepcion. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetails.of(ex.errorCode(), ex.getMessage(), request.getRequestURI());
        log.debug("Error de dominio {} en {}: {}", ex.errorCode(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(ex.errorCode().status()).body(problem);
    }

    /**
     * Seguridad a nivel de metodo.
     *
     * <p>Spring Security 7 deprecó {@code AccessDeniedException} en favor de
     * {@code AuthorizationDeniedException}; se cubren ambas porque el tipo que lanza
     * depende de como se haya configurado la autorizacion (riesgo R-06).
     */
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ProblemDetail> handleAccessDenied(Exception ex, HttpServletRequest request) {
        ProblemDetail problem =
                ProblemDetails.of(ErrorCode.FORBIDDEN, "No tienes permiso para realizar esta operacion", request.getRequestURI());
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status()).body(problem);
    }

    /** Autenticacion fallida dentro del contexto de la aplicacion. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        ProblemDetail problem =
                ProblemDetails.of(ErrorCode.UNAUTHENTICATED, "Se requiere autenticacion valida", request.getRequestURI());
        return ResponseEntity.status(ErrorCode.UNAUTHENTICATED.status()).body(problem);
    }

    /** Conflicto de locking optimista: otra accion modifico el recurso primero. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetails.of(
                ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                "El recurso fue modificado por otra accion. Vuelve a cargarlo e intentalo de nuevo.",
                request.getRequestURI());
        return ResponseEntity.status(ErrorCode.OPTIMISTIC_LOCK_CONFLICT.status()).body(problem);
    }

    /**
     * Violacion de una restriccion de integridad de la base de datos.
     *
     * <p>Ocurre solo en carreras que escapan a las comprobaciones previas de los servicios. Se
     * traduce unicamente cuando la restriccion identificada tiene un error de dominio claro;
     * cualquier otra violacion es un fallo inesperado y no debe disfrazarse de duplicado.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String constraintMessage = ex.getMostSpecificCause().getMessage();
        ErrorCode code = classifyIntegrityViolation(constraintMessage);

        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("Violacion de integridad no clasificada en {}", request.getRequestURI(), ex);
            ProblemDetail problem = ProblemDetails.of(
                    ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.title(), request.getRequestURI());
            return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(problem);
        }

        log.warn("Violacion de integridad {} en {}: {}", code, request.getRequestURI(), constraintMessage);
        ProblemDetail problem = ProblemDetails.of(code, code.title(), request.getRequestURI());
        return ResponseEntity.status(code.status()).body(problem);
    }

    private static ErrorCode classifyIntegrityViolation(String message) {
        if (message == null) {
            return ErrorCode.INTERNAL_ERROR;
        }
        if (message.contains("uq_project_members")) {
            return ErrorCode.MEMBER_ALREADY_EXISTS;
        }
        if (message.contains("uq_users_email")) {
            return ErrorCode.EMAIL_ALREADY_REGISTERED;
        }
        return ErrorCode.INTERNAL_ERROR;
    }

    /** Cualquier otra excepcion: se registra completa y se responde de forma generica. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error inesperado en {}", request.getRequestURI(), ex);
        ProblemDetail problem = ProblemDetails.of(
                ErrorCode.INTERNAL_ERROR, "Se ha producido un error inesperado", request.getRequestURI());
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(problem);
    }

    /**
     * Validacion de Bean Validation: 400 con el detalle por campo.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toViolation)
                .toList();

        ProblemDetail problem = ProblemDetails.of(
                ErrorCode.VALIDATION_ERROR, "La peticion contiene datos no validos", pathOf(request));
        problem.setProperty("errors", violations);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status()).body(problem);
    }

    /** JSON ilegible: 400 con el mismo formato que el resto de errores. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problem =
                ProblemDetails.of(ErrorCode.MALFORMED_JSON, "El cuerpo de la peticion no es un JSON valido", pathOf(request));
        return ResponseEntity.status(ErrorCode.MALFORMED_JSON.status()).body(problem);
    }

    private static FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getDefaultMessage(), error.getRejectedValue());
    }

    private static String pathOf(WebRequest request) {
        if (request instanceof org.springframework.web.context.request.ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest().getRequestURI();
        }
        return "";
    }

    /**
     * Detalle de una violacion de validacion de un campo.
     *
     * @param field nombre del campo
     * @param message descripcion del problema
     * @param rejectedValue valor rechazado
     */
    public record FieldViolation(String field, String message, Object rejectedValue) {}
}
