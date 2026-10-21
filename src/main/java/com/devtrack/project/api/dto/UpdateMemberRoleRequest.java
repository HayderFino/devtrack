package com.devtrack.project.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Peticion de cambio de rol de un miembro.
 *
 * @param role nuevo rol
 */
public record UpdateMemberRoleRequest(@NotNull(message = "el rol es obligatorio") ProjectRoleRequest role) {}
