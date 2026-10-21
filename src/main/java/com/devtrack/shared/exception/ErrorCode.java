package com.devtrack.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Catalogo unico de codigos de error de la API.
 *
 * <p>Cada codigo tiene un {@link HttpStatus} asociado y un identificador estable que el
 * cliente puede usar para ramificar sin parsear texto (ADR-0005).
 *
 * <p>El enum es la unica fuente de verdad: ni los controladores ni los servicios inventan
 * codigos.
 */
public enum ErrorCode {

    /** Fallo de validacion de Bean Validation. */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Solicitud invalida"),
    /** El cuerpo de la peticion no es JSON valido. */
    MALFORMED_JSON(HttpStatus.BAD_REQUEST, "Peticion malformada"),
    /** Parametro de query o de path invalido. */
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Parametro invalido"),

    /** Credenciales de login incorrectas. */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Credenciales invalidas"),
    /** Token ausente, invalido, malformado o expirado. */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "No autenticado"),

    /** La cuenta esta deshabilitada. */
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Cuenta deshabilitada"),
    /** El usuario esta autenticado pero su rol no permite la operacion. */
    FORBIDDEN(HttpStatus.FORBIDDEN, "Acceso denegado"),

    /** El proyecto no existe o el llamador no es miembro. */
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "Proyecto no encontrado"),
    /** La tarea no existe o el llamador no tiene acceso. */
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "Tarea no encontrada"),
    /** El usuario no existe. */
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Usuario no encontrado"),
    /** El usuario no es miembro del proyecto. */
    PROJECT_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "El usuario no es miembro del proyecto"),

    /** El email ya esta registrado. */
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "El email ya esta registrado"),
    /** El usuario ya es miembro del proyecto. */
    MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "El usuario ya es miembro del proyecto"),
    /** El propietario del proyecto no puede ser degradado ni eliminado. */
    OWNER_CANNOT_BE_REMOVED(HttpStatus.CONFLICT, "El propietario del proyecto no puede ser eliminado"),
    /** Conflicto de locking optimista. */
    OPTIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT, "El recurso fue modificado por otra accion"),

    /** Transicion de estado no permitida por la maquina de estados. */
    INVALID_STATUS_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "Transicion de estado no permitida"),
    /** El asignado no pertenece al proyecto. */
    ASSIGNEE_NOT_A_MEMBER(HttpStatus.UNPROCESSABLE_ENTITY, "El asignado no es miembro del proyecto"),
    /** No se puede asignar el rol OWNER por las rutas de membresia. */
    OWNER_ROLE_NOT_ASSIGNABLE(HttpStatus.UNPROCESSABLE_ENTITY, "El rol de propietario solo se concede al crear el proyecto o por transferencia"),
    /** Un EDITOR solo puede borrar las tareas que ha creado. */
    TASK_DELETION_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "No tienes permiso para eliminar esta tarea"),

    /** Fallo no previsto. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** URI estable que documenta el significado del error. */
    public String type() {
        return "https://devtrack.dev/problems/" + name().toLowerCase().replace('_', '-');
    }
}
