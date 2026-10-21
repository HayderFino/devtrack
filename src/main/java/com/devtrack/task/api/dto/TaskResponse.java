package com.devtrack.task.api.dto;

import java.time.Instant;
import java.time.LocalDate;

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
        Long id,
        Long projectId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        Long assigneeId,
        String assigneeName,
        LocalDate dueDate,
        Long createdById,
        String createdByName,
        boolean overdue,
        Long version,
        Instant createdAt,
        Instant updatedAt) {

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
