package com.devtrack.user.api.dto;

import com.devtrack.user.domain.User;

/**
 * Respuesta de un inicio de sesion correcto.
 *
 * @param accessToken token JWT de acceso
 * @param tokenType tipo de token, siempre {@code Bearer}
 * @param expiresIn vigencia del token en segundos
 * @param user perfil del usuario autenticado
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {

    public static AuthResponse from(User user, String accessToken, long expiresIn) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, UserResponse.from(user));
    }
}
