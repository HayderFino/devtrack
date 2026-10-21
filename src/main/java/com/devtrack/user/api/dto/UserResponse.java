package com.devtrack.user.api.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.devtrack.user.domain.PlatformRole;
import com.devtrack.user.domain.User;

/**
 * Perfil de usuario.
 *
 * <p>Nunca incluye el hash de la contrasena.
 *
 * @param id identificador del usuario
 * @param email correo electronico
 * @param fullName nombre completo
 * @param platformRole rol de plataforma
 * @param createdAt fecha de alta
 */
public record UserResponse(
        @Schema(description = "Identificador del usuario", example = "1") Long id,
        @Schema(description = "Email normalizado", example = "ada@devtrack.test") String email,
        @Schema(description = "Nombre completo", example = "Ada Lovelace") String fullName,
        @Schema(description = "Rol de plataforma", example = "USER") PlatformRole platformRole,
        @Schema(description = "Fecha de alta en UTC", example = "2026-10-21T12:00:00Z") Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getPlatformRole(), user.getCreatedAt());
    }
}
