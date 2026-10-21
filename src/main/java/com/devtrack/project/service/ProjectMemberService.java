package com.devtrack.project.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtrack.project.api.dto.ProjectMemberResponse;
import com.devtrack.project.api.dto.ProjectResponse;
import com.devtrack.project.api.dto.TransferOwnershipRequest;
import com.devtrack.project.api.dto.UpdateMemberRoleRequest;
import com.devtrack.project.api.dto.UpsertMemberRequest;
import com.devtrack.project.domain.Project;
import com.devtrack.project.domain.ProjectMember;
import com.devtrack.project.domain.ProjectRole;
import com.devtrack.project.repository.ProjectMemberRepository;
import com.devtrack.project.repository.ProjectRepository;
import com.devtrack.shared.exception.ApiException;
import com.devtrack.shared.exception.ErrorCode;
import com.devtrack.shared.security.CurrentUser;
import com.devtrack.shared.security.ProjectAccessGuard;
import com.devtrack.user.domain.User;
import com.devtrack.user.repository.UserRepository;

/**
 * Casos de uso de membresia de proyecto.
 *
 * <p>Mantiene la invariante "un proyecto tiene exactamente un miembro {@code OWNER}":
 * el propietario no se puede degradar ni eliminar, y la propiedad se transfiere de forma
 * atomica.
 */
@Service
@Transactional
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectAccessGuard accessGuard;

    public ProjectMemberService(
            ProjectMemberRepository projectMemberRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            ProjectAccessGuard accessGuard) {
        this.projectMemberRepository = projectMemberRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
    }

    /**
     * Lista los miembros de un proyecto. Cualquier miembro puede consultarlos.
     *
     * @param projectId identificador del proyecto
     * @param caller usuario autenticado
     * @return miembros del proyecto
     */
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> list(Long projectId, CurrentUser caller) {
        accessGuard.requireMember(projectId, caller);
        return projectMemberRepository.findAllByProjectIdWithUser(projectId).stream()
                .map(ProjectMemberResponse::from)
                .toList();
    }

    /**
     * Anade un miembro al proyecto. Solo el propietario (o un administrador de plataforma).
     *
     * @param projectId identificador del proyecto
     * @param caller usuario autenticado
     * @param request usuario y rol
     * @return la membresia creada
     */
    public ProjectMemberResponse add(Long projectId, CurrentUser caller, UpsertMemberRequest request) {
        accessGuard.requireOwnerOrAdmin(projectId, caller);

        Project project = projectRepository
                .findById(projectId)
                .orElseThrow(() -> new ApiException.ResourceNotFound(
                        ErrorCode.PROJECT_NOT_FOUND, "No existe un proyecto con id " + projectId));

        User user = userRepository
                .findById(request.userId())
                .orElseThrow(() -> new ApiException.ResourceNotFound(
                        ErrorCode.USER_NOT_FOUND, "No existe un usuario con id " + request.userId()));

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, request.userId())) {
            throw new ApiException.Conflict(ErrorCode.MEMBER_ALREADY_EXISTS, "El usuario ya es miembro del proyecto");
        }

        ProjectMember member =
                projectMemberRepository.save(new ProjectMember(project, user, request.role().toDomain()));
        return ProjectMemberResponse.from(member);
    }

    /**
     * Cambia el rol de un miembro. Solo el propietario (o un administrador de plataforma).
     *
     * @param projectId identificador del proyecto
     * @param userId usuario cuyo rol cambia
     * @param caller usuario autenticado
     * @param request nuevo rol
     * @return la membresia actualizada
     */
    public ProjectMemberResponse changeRole(Long projectId, Long userId, CurrentUser caller, UpdateMemberRoleRequest request) {
        accessGuard.requireOwnerOrAdmin(projectId, caller);

        ProjectMember member = requireMembership(projectId, userId);
        if (member.getRole() == ProjectRole.OWNER) {
            throw new ApiException.Conflict(
                    ErrorCode.OWNER_CANNOT_BE_REMOVED,
                    "El propietario no puede ser degradado. Transfiere la propiedad del proyecto en su lugar.");
        }

        member.setRole(request.role().toDomain());
        return ProjectMemberResponse.from(member);
    }

    /**
     * Quita un miembro del proyecto. Solo el propietario (o un administrador de plataforma).
     *
     * @param projectId identificador del proyecto
     * @param userId usuario que se elimina
     * @param caller usuario autenticado
     */
    public void remove(Long projectId, Long userId, CurrentUser caller) {
        accessGuard.requireOwnerOrAdmin(projectId, caller);

        ProjectMember member = requireMembership(projectId, userId);
        if (member.getRole() == ProjectRole.OWNER) {
            throw new ApiException.Conflict(
                    ErrorCode.OWNER_CANNOT_BE_REMOVED,
                    "El propietario no puede ser eliminado. Transfiere la propiedad del proyecto en su lugar.");
        }

        projectMemberRepository.delete(member);
    }

    /**
     * Transfiere la propiedad del proyecto a otro miembro.
     *
     * <p>Todo ocurre en la misma transaccion: el propietario actual pasa a {@code EDITOR} y
     * el receptor a {@code OWNER}. Si algo falla, no hay ni cero ni dos propietarios.
     *
     * @param projectId identificador del proyecto
     * @param caller usuario autenticado
     * @param request nuevo propietario
     * @return el proyecto con el nuevo propietario
     */
    public ProjectResponse transferOwnership(Long projectId, CurrentUser caller, TransferOwnershipRequest request) {
        accessGuard.requireOwnerOrAdmin(projectId, caller);

        ProjectMember currentOwner = projectMemberRepository
                .findByProjectIdAndRole(projectId, ProjectRole.OWNER)
                .orElseThrow(() -> new ApiException.Conflict(
                        ErrorCode.OWNER_CANNOT_BE_REMOVED, "El proyecto no tiene propietario"));

        ProjectMember newOwner = requireMembership(projectId, request.newOwnerId());
        if (newOwner.getRole() == ProjectRole.OWNER) {
            throw new ApiException.Conflict(
                    ErrorCode.OWNER_CANNOT_BE_REMOVED, "El usuario indicado ya es propietario del proyecto");
        }

        currentOwner.setRole(ProjectRole.EDITOR);
        newOwner.setRole(ProjectRole.OWNER);

        Project project = projectRepository
                .findById(projectId)
                .orElseThrow(() -> new ApiException.ResourceNotFound(
                        ErrorCode.PROJECT_NOT_FOUND, "No existe un proyecto con id " + projectId));
        project.setOwner(newOwner.getUser());

        return ProjectResponse.from(project, ProjectRole.OWNER, projectMemberRepository.countByProjectId(projectId));
    }

    private ProjectMember requireMembership(Long projectId, Long userId) {
        return projectMemberRepository
                .findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ApiException.ResourceNotFound(
                        ErrorCode.PROJECT_MEMBER_NOT_FOUND, "El usuario no es miembro del proyecto"));
    }
}
