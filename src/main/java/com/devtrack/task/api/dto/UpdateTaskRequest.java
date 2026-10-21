package com.devtrack.task.api.dto;

import java.time.LocalDate;

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
        @Size(min = 1, max = 200, message = "el titulo no puede superar los 200 caracteres") String title,
        @Size(max = 10000, message = "la descripcion no puede superar los 10000 caracteres") String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate) {}
