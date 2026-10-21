package com.devtrack.user.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

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
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * Inicia sesion y devuelve un token de acceso.
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
