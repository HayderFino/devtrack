package com.devtrack.project.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Peticion de creacion de proyecto.
 *
 * @param name nombre del proyecto
 * @param description descripcion opcional
 */
public record CreateProjectRequest(
        @NotBlank(message = "el nombre es obligatorio") @Size(max = 120, message = "el nombre no puede superar los 120 caracteres")
                String name,
        @Size(max = 5000, message = "la descripcion no puede superar los 5000 caracteres") String description) {}
