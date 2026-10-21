package com.devtrack.project.api.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.devtrack.project.domain.Project;
import com.devtrack.project.domain.ProjectRole;

/**
 * Respuesta de proyecto.
 *
 * <p>{@code role} es el rol <em>del llamador</em> en ese proyecto: permite al cliente
 * ajustar la interfaz sin una segunda peticion.
 *
 * @param id identificador del proyecto
 * @param name nombre del proyecto
 * @param description descripcion
 * @param ownerId identificador del propietario
 * @param archived si el proyecto esta archivado
 * @param role rol del llamador en este proyecto
 * @param memberCount numero de miembros
 * @param createdAt fecha de creacion
 * @param updatedAt fecha de la ultima modificacion
 */
public record ProjectResponse(
        @Schema(description = "Identificador del proyecto", example = "1") Long id,
        @Schema(description = "Nombre visible", example = "Lanzamiento v1") String name,
        @Schema(description = "Descripcion", example = "Plan de lanzamiento") String description,
        @Schema(description = "Usuario propietario", example = "7") Long ownerId,
        @Schema(description = "Si el proyecto esta archivado", example = "false") boolean archived,
        @Schema(description = "Rol del llamador en este proyecto", example = "OWNER") ProjectRole role,
        @Schema(description = "Numero de miembros", example = "3") long memberCount,
        @Schema(description = "Fecha de creacion en UTC", example = "2026-10-21T12:00:00Z") Instant createdAt,
        @Schema(description = "Fecha de ultima modificacion en UTC", example = "2026-10-21T12:30:00Z") Instant updatedAt) {

    public static ProjectResponse from(Project project, ProjectRole role, long memberCount) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getOwner().getId(),
                project.isArchived(),
                role,
                memberCount,
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
