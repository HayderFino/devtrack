package com.devtrack.user.api.dto;

import java.time.Instant;

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
public record UserResponse(Long id, String email, String fullName, PlatformRole platformRole, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getPlatformRole(), user.getCreatedAt());
    }
}
