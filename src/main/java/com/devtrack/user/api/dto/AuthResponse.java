package com.devtrack.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.devtrack.user.domain.User;

/**
 * Respuesta de un inicio de sesion correcto.
 *
 * @param accessToken token JWT de acceso
 * @param tokenType tipo de token, siempre {@code Bearer}
 * @param expiresIn vigencia del token en segundos
 * @param user perfil del usuario autenticado
 */
public record AuthResponse(
        @Schema(description = "JWT de acceso", example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(description = "Esquema de autorizacion", example = "Bearer") String tokenType,
        @Schema(description = "Vigencia del token en segundos", example = "3600") long expiresIn,
        @Schema(description = "Perfil del usuario autenticado") UserResponse user) {

    public static AuthResponse from(User user, String accessToken, long expiresIn) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, UserResponse.from(user));
    }
}
