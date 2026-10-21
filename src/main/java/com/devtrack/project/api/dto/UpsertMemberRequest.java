package com.devtrack.project.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Peticion de alta de miembro.
 *
 * @param userId usuario que se anade al proyecto
 * @param role rol dentro del proyecto; no puede ser {@code OWNER}
 */
public record UpsertMemberRequest(
        @NotNull(message = "el usuario es obligatorio") @Positive(message = "el usuario no es valido") Long userId,
        @NotNull(message = "el rol es obligatorio") ProjectRoleRequest role) {}
