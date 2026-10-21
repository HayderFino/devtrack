package com.devtrack.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Peticion de inicio de sesion.
 *
 * @param email correo electronico
 * @param password contrasena
 */
public record LoginRequest(
        @NotBlank(message = "el email es obligatorio") @Email(message = "el email no tiene un formato valido") String email,
        @NotBlank(message = "la contrasena es obligatoria") String password) {}
