package com.devtrack.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Peticion de asignacion de tarea.
 *
 * <p>Es idempotente: enviar el mismo asignatario dos veces no produce ningun cambio, y
 * {@code null} desasigna la tarea.
 *
 * @param assigneeId usuario asignado, o {@code null} para desasignar
 */
public record AssignTaskRequest(
        @Schema(description = "Usuario asignado; null desasigna", example = "42", minimum = "1") Long assigneeId) {}
