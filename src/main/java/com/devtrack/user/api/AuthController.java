package com.devtrack.user.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.devtrack.user.api.dto.AuthResponse;
import com.devtrack.user.api.dto.LoginRequest;
import com.devtrack.user.api.dto.RegisterRequest;
import com.devtrack.user.api.dto.UserResponse;
import com.devtrack.user.service.AuthService;

/**
 * Endpoints de autenticacion.
 *
 * <p>Sin logica de negocio: solo validan la entrada y delegan en {@link AuthService}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticacion", description = "Registro e inicio de sesion (endpoints publicos)")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registra una cuenta nueva.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una cuenta", description = "Crea un usuario y devuelve su perfil, sin token.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta creada"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "409", description = "Email ya registrado")
    })
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * Inicia sesion y devuelve un token de acceso.
     */
    @PostMapping("/login")
    @Operation(summary = "Iniciar sesion", description = "Devuelve un JWT Bearer y el perfil del usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credenciales correctas"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "401", description = "Credenciales incorrectas o cuenta deshabilitada")
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
