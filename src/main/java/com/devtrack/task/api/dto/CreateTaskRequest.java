package com.devtrack.task.api.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.devtrack.task.domain.TaskPriority;
import com.devtrack.task.domain.TaskStatus;

/**
 * Peticion de creacion de tarea.
 *
 * @param title titulo de la tarea
 * @param description descripcion opcional
 * @param status estado inicial; por defecto {@code TODO}
 * @param priority prioridad; por defecto {@code MEDIUM}
 * @param assigneeId usuario asignado; debe ser miembro del proyecto
 * @param dueDate fecha limite
 */
public record CreateTaskRequest(
        @Schema(description = "Titulo de la tarea", example = "Preparar release", maxLength = 200)
                @NotBlank(message = "el titulo es obligatorio")
                @Size(max = 200, message = "el titulo no puede superar los 200 caracteres")
                String title,
        @Schema(description = "Descripcion opcional", example = "Checklist de despliegue", maxLength = 10000)
                @Size(max = 10000, message = "la descripcion no puede superar los 10000 caracteres") String description,
        @Schema(description = "Estado inicial; por defecto TODO", example = "TODO") TaskStatus status,
        @Schema(description = "Prioridad; por defecto MEDIUM", example = "HIGH") TaskPriority priority,
        @Schema(description = "Usuario asignado; debe ser miembro del proyecto", example = "42", minimum = "1")
                @Positive(message = "el asignado no es valido") Long assigneeId,
        @Schema(description = "Fecha limite en formato ISO", example = "2026-11-01") LocalDate dueDate) {

    public TaskStatus statusOrDefault() {
        return status == null ? TaskStatus.TODO : status;
    }

    public TaskPriority priorityOrDefault() {
        return priority == null ? TaskPriority.MEDIUM : priority;
    }
}
