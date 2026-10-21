package com.devtrack.project.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
        @Schema(description = "Nuevo nombre; omitir para no cambiarlo", example = "Lanzamiento v2", maxLength = 120)
                @Size(min = 1, max = 120, message = "el nombre no puede superar los 120 caracteres") String name,
        @Schema(description = "Nueva descripcion; omitir para no cambiarla", example = "Actualizado", maxLength = 5000)
                @Size(max = 5000, message = "la descripcion no puede superar los 5000 caracteres") String description) {}
