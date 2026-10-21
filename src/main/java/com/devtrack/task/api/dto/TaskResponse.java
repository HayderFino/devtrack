package com.devtrack.task.api.dto;

import java.time.Instant;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

import com.devtrack.task.domain.Task;
import com.devtrack.task.domain.TaskPriority;
import com.devtrack.task.domain.TaskStatus;

/**
 * Respuesta de tarea.
 *
 * @param id identificador de la tarea
 * @param projectId proyecto al que pertenece
 * @param title titulo
 * @param description descripcion
 * @param status estado actual
 * @param priority prioridad
 * @param assigneeId usuario asignado, si lo hay
 * @param assigneeName nombre del usuario asignado
 * @param dueDate fecha limite
 * @param createdById usuario que creo la tarea
 * @param createdByName nombre del creador
 * @param overdue si la tarea ha vencido y no esta completada
 * @param version version para el locking optimista
 * @param createdAt fecha de creacion
 * @param updatedAt fecha de la ultima modificacion
 */
public record TaskResponse(
        @Schema(description = "Identificador de la tarea", example = "10") Long id,
        @Schema(description = "Proyecto al que pertenece", example = "1") Long projectId,
        @Schema(description = "Titulo", example = "Preparar release") String title,
        @Schema(description = "Descripcion", example = "Checklist de despliegue") String description,
        @Schema(description = "Estado actual", example = "TODO") TaskStatus status,
        @Schema(description = "Prioridad", example = "HIGH") TaskPriority priority,
        @Schema(description = "Usuario asignado, si lo hay", example = "42") Long assigneeId,
        @Schema(description = "Nombre del asignado", example = "Ada Lovelace") String assigneeName,
        @Schema(description = "Fecha limite", example = "2026-11-01") LocalDate dueDate,
        @Schema(description = "Usuario creador", example = "7") Long createdById,
        @Schema(description = "Nombre del creador", example = "Grace Hopper") String createdByName,
        @Schema(description = "Si vencio y no esta completada", example = "false") boolean overdue,
        @Schema(description = "Version para locking optimista", example = "0") Long version,
        @Schema(description = "Fecha de creacion en UTC", example = "2026-10-21T12:00:00Z") Instant createdAt,
        @Schema(description = "Fecha de ultima modificacion en UTC", example = "2026-10-21T12:30:00Z") Instant updatedAt) {

    public static TaskResponse from(Task task, LocalDate today) {
        return new TaskResponse(
                task.getId(),
                task.getProject().getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getAssignee() == null ? null : task.getAssignee().getId(),
                task.getAssignee() == null ? null : task.getAssignee().getFullName(),
                task.getDueDate(),
                task.getCreatedBy() == null ? null : task.getCreatedBy().getId(),
                task.getCreatedBy() == null ? null : task.getCreatedBy().getFullName(),
                task.isOverdue(today),
                task.getVersion(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
