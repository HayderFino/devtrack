package com.devtrack.project.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Peticion de cambio de rol de un miembro.
 *
 * @param role nuevo rol
 */
public record UpdateMemberRoleRequest(
        @Schema(description = "Nuevo rol del miembro", example = "VIEWER")
                @NotNull(message = "el rol es obligatorio") ProjectRoleRequest role) {}
