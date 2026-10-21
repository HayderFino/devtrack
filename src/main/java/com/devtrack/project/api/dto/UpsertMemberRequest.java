package com.devtrack.project.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Peticion de alta de miembro.
 *
 * @param userId usuario que se anade al proyecto
 * @param role rol dentro del proyecto; no puede ser {@code OWNER}
 */
public record UpsertMemberRequest(
        @Schema(description = "Usuario que se anade al proyecto", example = "42", minimum = "1")
                @NotNull(message = "el usuario es obligatorio") @Positive(message = "el usuario no es valido") Long userId,
        @Schema(description = "Rol en el proyecto; OWNER no se asigna por esta ruta", example = "EDITOR")
                @NotNull(message = "el rol es obligatorio") ProjectRoleRequest role) {}
