package com.devtrack.project.api.dto;

import java.time.Instant;

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
public record ProjectMemberResponse(Long userId, String email, String fullName, ProjectRole role, Instant addedAt) {

    public static ProjectMemberResponse from(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getFullName(),
                member.getRole(),
                member.getCreatedAt());
    }
}
