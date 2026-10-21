package com.devtrack.project.api.dto;

import java.time.Instant;

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
        Long id,
        String name,
        String description,
        Long ownerId,
        boolean archived,
        ProjectRole role,
        long memberCount,
        Instant createdAt,
        Instant updatedAt) {

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
