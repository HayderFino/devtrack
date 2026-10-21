package com.devtrack.project.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Peticion de actualizacion de proyecto.
 *
 * <p>Todos los campos son opcionales: los ausentes no se modifican. Se distingue
 * "campo ausente" de "campo a null" por la semantica de la API, no por el valor.
 *
 * @param name nuevo nombre, opcional
 * @param description nueva descripcion, opcional
 */
public record UpdateProjectRequest(
        @Size(min = 1, max = 120, message = "el nombre no puede superar los 120 caracteres") String name,
        @Size(max = 5000, message = "la descripcion no puede superar los 5000 caracteres") String description) {}
