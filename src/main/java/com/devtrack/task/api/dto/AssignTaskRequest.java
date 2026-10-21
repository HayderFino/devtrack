package com.devtrack.task.api.dto;

/**
 * Peticion de asignacion de tarea.
 *
 * <p>Es idempotente: enviar el mismo asignatario dos veces no produce ningun cambio, y
 * {@code null} desasigna la tarea.
 *
 * @param assigneeId usuario asignado, o {@code null} para desasignar
 */
public record AssignTaskRequest(Long assigneeId) {}
