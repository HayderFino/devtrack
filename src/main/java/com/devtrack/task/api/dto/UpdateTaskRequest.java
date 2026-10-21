package com.devtrack.task.api.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import com.devtrack.task.domain.TaskPriority;
import com.devtrack.task.domain.TaskStatus;

/**
 * Peticion de actualizacion de tarea.
 *
 * <p>Todos los campos son opcionales y los ausentes no se modifican. La asignacion
 * <strong>no</strong> se modifica aqui: tiene su propio endpoint, para no confundir
 * "campo ausente" con "campo a null".
 *
 * @param title nuevo titulo
 * @param description nueva descripcion
 * @param status nuevo estado; se valida contra la maquina de estados
 * @param priority nueva prioridad
 * @param dueDate nueva fecha limite
 */
public record UpdateTaskRequest(
        @Schema(description = "Nuevo titulo; omitir para no cambiarlo", example = "Preparar release v1", maxLength = 200)
                @Size(min = 1, max = 200, message = "el titulo no puede superar los 200 caracteres") String title,
        @Schema(description = "Nueva descripcion; omitir para no cambiarla", example = "Checklist actualizado", maxLength = 10000)
                @Size(max = 10000, message = "la descripcion no puede superar los 10000 caracteres") String description,
        @Schema(description = "Nuevo estado; se valida contra la maquina de transiciones", example = "IN_PROGRESS") TaskStatus status,
        @Schema(description = "Nueva prioridad", example = "URGENT") TaskPriority priority,
        @Schema(description = "Nueva fecha limite en formato ISO", example = "2026-11-15") LocalDate dueDate) {}
