package com.devtrack.project.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtrack.project.api.dto.CreateProjectRequest;
import com.devtrack.project.api.dto.ProjectResponse;
import com.devtrack.project.api.dto.UpdateProjectRequest;
import com.devtrack.project.domain.Project;
import com.devtrack.project.domain.ProjectMember;
import com.devtrack.project.domain.ProjectRole;
import com.devtrack.project.repository.ProjectMemberRepository;
import com.devtrack.project.repository.ProjectRepository;
import com.devtrack.shared.exception.ApiException;
import com.devtrack.shared.exception.ErrorCode;
import com.devtrack.shared.security.CurrentUser;
import com.devtrack.shared.security.ProjectAccessGuard;
import com.devtrack.shared.web.PageResponse;
import com.devtrack.user.domain.User;
import com.devtrack.user.repository.UserRepository;

/**
 * Casos de uso de proyectos.
 *
 * <p>Toda operacion que toque un proyecto pasa por {@link ProjectAccessGuard} antes de
 * leer o modificar nada: conocer el identificador del proyecto no da acceso (ADR-0004).
 */
@Service
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ProjectAccessGuard accessGuard;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            UserRepository userRepository,
            ProjectAccessGuard accessGuard) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
    }

    /**
     * Crea un proyecto y convierte al creator en miembro con rol {@code OWNER}.
     *
     * <p>Ambas inserciones ocurren en la misma transaccion: si la segunda falla, no queda
     * un proyecto sin propietario.
     *
     * @param caller usuario autenticado
     * @param request datos del proyecto
     * @return el proyecto creado
     */
    public ProjectResponse create(CurrentUser caller, CreateProjectRequest request) {
        User owner = userRepository
                .findById(caller.userId())
                .orElseThrow(() -> new ApiException.ResourceNotFound(
                        ErrorCode.USER_NOT_FOUND, "No existe un usuario con id " + caller.userId()));

        Project project = projectRepository.save(new Project(request.name().strip(), request.description(), owner));
        projectMemberRepository.save(new ProjectMember(project, owner, ProjectRole.OWNER));

        return ProjectResponse.from(project, ProjectRole.OWNER, 1);
    }

    /**
     * Detalle de un proyecto visible para el llamador.
     *
     * @param projectId identificador del proyecto
     * @param caller usuario autenticado
     * @return el proyecto
     */
    @Transactional(readOnly = true)
    public ProjectResponse findByIdForMember(Long projectId, CurrentUser caller) {
        ProjectMember membership = accessGuard.requireMember(projectId, caller);
        Project project = findProject(projectId);
        return ProjectResponse.from(project, membership.getRole(), projectMemberRepository.countByProjectId(projectId));
    }

    /**
     * Lista los proyectos de los que el llamador es miembro.
     *
     * @param caller usuario autenticado
     * @param archivedParam filtro de archivado: {@code true}, {@code false} (por defecto) o {@code all}
     * @param pageable paginacion
     * @return pagina de proyectos
     */
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> listForUser(CurrentUser caller, String archivedParam, Pageable pageable) {
        Boolean archived = parseArchivedFilter(archivedParam);

        Page<Project> page = projectRepository.findAllForUser(caller.userId(), archived, pageable);
        if (page.isEmpty()) {
            return PageResponse.from(page, project -> ProjectResponse.from(project, null, 0));
        }

        List<Long> projectIds = page.getContent().stream().map(Project::getId).toList();

        Map<Long, ProjectRole> rolesByProject = new HashMap<>();
        for (ProjectMember membership : projectMemberRepository.findByUserIdAndProjectIdIn(caller.userId(), projectIds)) {
            rolesByProject.put(membership.getProject().getId(), membership.getRole());
        }

        Map<Long, Long> memberCounts = new HashMap<>();
        for (Object[] row : projectMemberRepository.countMembersByProjectIds(projectIds)) {
            memberCounts.put((Long) row[0], (Long) row[1]);
        }

        return PageResponse.from(
                page,
                project -> ProjectResponse.from(
                        project, rolesByProject.get(project.getId()), memberCounts.getOrDefault(project.getId(), 0L)));
    }

    /**
     * Actualiza los datos de un proyecto. Solo el propietario.
     *
     * @param projectId identificador del proyecto
     * @param caller usuario autenticado
     * @param request campos a modificar; los ausentes se ignoran
     * @return el proyecto actualizado
     */
    @Transactional
    public ProjectResponse update(Long projectId, CurrentUser caller, UpdateProjectRequest request) {
        accessGuard.requireOwnerOrAdmin(projectId, caller);
        Project project = findProject(projectId);

        if (request.name() != null) {
            project.setName(request.name().strip());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }

        ProjectRole callerRole = caller.isAdmin()
                ? projectMemberRepository
                        .findByProjectIdAndUserId(projectId, caller.userId())
                        .map(ProjectMember::getRole)
                        .orElse(null)
                : ProjectRole.OWNER;

        return ProjectResponse.from(project, callerRole, projectMemberRepository.countByProjectId(projectId));
    }

    /**
     * Archiva un proyecto (borrado logico, ADR-0006). Solo el propietario.
     *
     * @param projectId identificador del proyecto
     * @param caller usuario autenticado
     */
    @Transactional
    public void archive(Long projectId, CurrentUser caller) {
        accessGuard.requireOwnerOrAdmin(projectId, caller);
        findProject(projectId).archive();
    }

    private Project findProject(Long projectId) {
        return projectRepository
                .findById(projectId)
                .orElseThrow(() -> new ApiException.ResourceNotFound(
                        ErrorCode.PROJECT_NOT_FOUND, "No existe un proyecto con id " + projectId));
    }

    private static Boolean parseArchivedFilter(String archivedParam) {
        String value = archivedParam == null ? "false" : archivedParam.strip().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "all" -> null;
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> throw new ApiException(ErrorCode.INVALID_PARAMETER, "El parametro 'archived' solo admite true, false o all");
        };
    }
}
