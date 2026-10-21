package com.devtrack.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Peticion de inicio de sesion.
 *
 * @param email correo electronico
 * @param password contrasena
 */
public record LoginRequest(
        @Schema(description = "Email de la cuenta", example = "ada@devtrack.test")
                @NotBlank(message = "el email es obligatorio") @Email(message = "el email no tiene un formato valido") String email,
        @Schema(description = "Contrasena de la cuenta", example = "Str0ng-Passphrase!")
                @NotBlank(message = "la contrasena es obligatoria") String password) {}
