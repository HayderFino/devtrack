package com.devtrack.user.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.devtrack.shared.config.OpenApiConfig;

import com.devtrack.shared.security.CurrentUser;
import com.devtrack.user.api.dto.UserResponse;
import com.devtrack.user.service.UserService;

/**
 * Endpoints de consulta de usuarios.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Usuarios", description = "Perfil del usuario autenticado")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Perfil del usuario autenticado.
     */
    @GetMapping("/me")
    @Operation(summary = "Perfil propio", description = "Devuelve el perfil del usuario del token; nunca el hash de contrasena.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil del usuario"),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido o cuenta deshabilitada")
    })
    public UserResponse me(@AuthenticationPrincipal CurrentUser currentUser) {
        return userService.getById(currentUser.userId());
    }
}
