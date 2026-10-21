package com.devtrack.shared.exception;

/**
 * Excepcion base de la aplicacion.
 *
 * <p>Toda excepcion de dominio lleva asociado un {@link ErrorCode} del catalogo, de modo que
 * la traduccion a HTTP y a {@code ProblemDetail} es automatica y uniforme.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** El recurso no existe o el llamador no tiene acceso a el (se responde 404). */
    public static class ResourceNotFound extends ApiException {
        public ResourceNotFound(ErrorCode errorCode, String detail) {
            super(errorCode, detail);
        }
    }

    /** Conflicto con el estado actual del recurso (se responde 409). */
    public static class Conflict extends ApiException {
        public Conflict(ErrorCode errorCode, String detail) {
            super(errorCode, detail);
        }
    }

    /** La peticion es sintacticamente valida pero imposible en el contexto actual (422). */
    public static class Unprocessable extends ApiException {
        public Unprocessable(ErrorCode errorCode, String detail) {
            super(errorCode, detail);
        }
    }

    /** El llamador esta autenticado pero no tiene permiso suficiente (403). */
    public static class Forbidden extends ApiException {
        public Forbidden(ErrorCode errorCode, String detail) {
            super(errorCode, detail);
        }
    }

    /** El llamador no esta autenticado (401). */
    public static class Unauthenticated extends ApiException {
        public Unauthenticated(ErrorCode errorCode, String detail) {
            super(errorCode, detail);
        }
    }
}
