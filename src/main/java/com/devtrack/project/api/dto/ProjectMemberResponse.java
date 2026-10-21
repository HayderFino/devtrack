package com.devtrack.project.api.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.devtrack.project.domain.ProjectMember;
import com.devtrack.project.domain.ProjectRole;

/**
 * Respuesta de membresia de proyecto.
 *
 * @param userId identificador del usuario
 * @param email email del usuario
 * @param fullName nombre completo del usuario
 * @param role rol dentro del proyecto
 * @param addedAt momento en que el usuario se unio al proyecto
 */
public record ProjectMemberResponse(
        @Schema(description = "Identificador del usuario", example = "42") Long userId,
        @Schema(description = "Email del usuario", example = "ada@devtrack.test") String email,
        @Schema(description = "Nombre completo", example = "Ada Lovelace") String fullName,
        @Schema(description = "Rol dentro del proyecto", example = "EDITOR") ProjectRole role,
        @Schema(description = "Fecha en que se unio al proyecto", example = "2026-10-21T12:00:00Z") Instant addedAt) {

    public static ProjectMemberResponse from(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getFullName(),
                member.getRole(),
                member.getCreatedAt());
    }
}
