package com.devtrack.project.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Peticion de creacion de proyecto.
 *
 * @param name nombre del proyecto
 * @param description descripcion opcional
 */
public record CreateProjectRequest(
        @Schema(description = "Nombre visible del proyecto", example = "Lanzamiento v1", maxLength = 120)
                @NotBlank(message = "el nombre es obligatorio") @Size(max = 120, message = "el nombre no puede superar los 120 caracteres")
                String name,
        @Schema(description = "Descripcion opcional", example = "Plan de lanzamiento del producto", maxLength = 5000)
                @Size(max = 5000, message = "la descripcion no puede superar los 5000 caracteres") String description) {}
