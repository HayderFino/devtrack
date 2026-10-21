package com.devtrack.shared.security;

import com.devtrack.user.domain.PlatformRole;

/**
 * Identidad del llamador autenticado, inyectada en los controladores.
 *
 * <p>Se obtiene con {@code @AuthenticationPrincipal CurrentUser currentUser}. Transporta el
 * identificador del usuario (no solo el email), que es lo que necesita el guard de
 * autorizacion para resolver la membresia de un proyecto.
 *
 * @param userId identificador del usuario
 * @param email email del usuario
 * @param platformRole rol de plataforma
 */
public record CurrentUser(Long userId, String email, PlatformRole platformRole) {

    public boolean isAdmin() {
        return platformRole == PlatformRole.ADMIN;
    }
}
