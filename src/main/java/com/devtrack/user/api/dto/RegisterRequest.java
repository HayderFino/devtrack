package com.devtrack.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Peticion de registro de usuario.
 *
 * @param email correo electronico; se normaliza a minusculas
 * @param password contrasena en claro; se almacena hasheada con BCrypt
 * @param fullName nombre completo
 */
public record RegisterRequest(
        @NotBlank(message = "el email es obligatorio")
                @Email(message = "el email no tiene un formato valido")
                @Size(max = 320, message = "el email no puede superar los 320 caracteres")
                String email,
        @NotBlank(message = "la contrasena es obligatoria")
                @Size(min = 10, message = "la contrasena debe tener al menos 10 caracteres")
                @Size(max = 72, message = "la contrasena no puede superar los 72 caracteres")
                String password,
        @NotBlank(message = "el nombre es obligatorio") @Size(max = 120, message = "el nombre no puede superar los 120 caracteres")
                String fullName) {}
