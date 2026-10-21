package com.devtrack.project.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Peticion de transferencia de propiedad del proyecto.
 *
 * @param newOwnerId usuario que pasa a ser propietario
 */
public record TransferOwnershipRequest(
        @NotNull(message = "el nuevo propietario es obligatorio")
                @Positive(message = "el nuevo propietario no es valido")
                Long newOwnerId) {}
