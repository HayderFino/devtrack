package com.devtrack.project.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Peticion de transferencia de propiedad del proyecto.
 *
 * @param newOwnerId usuario que pasa a ser propietario
 */
public record TransferOwnershipRequest(
        @Schema(description = "Miembro que pasa a ser OWNER; debe coincidir con el userId de la ruta", example = "42", minimum = "1")
                @NotNull(message = "el nuevo propietario es obligatorio")
                @Positive(message = "el nuevo propietario no es valido")
                Long newOwnerId) {}
