package com.devtrack.shared.security;

import org.springframework.stereotype.Component;

import com.devtrack.project.domain.ProjectMember;
import com.devtrack.project.domain.ProjectRole;
import com.devtrack.project.repository.ProjectMemberRepository;
import com.devtrack.shared.exception.ApiException;
import com.devtrack.shared.exception.ErrorCode;

/**
 * Guard de autorizacion por membresia de proyecto (ADR-0004).
 *
 * <p>Unico punto donde se decide si el llamador puede operar sobre un proyecto. Se usa un
 * componente explicito en lugar de {@code @PreAuthorize} porque el rol depende del
 * recurso concreto y la expresion SpEL no puede expresarlo con claridad.
 *
 * <p>Politica de codigos HTTP:
 * <ul>
 *   <li>El llamador <strong>no es miembro</strong> (o el proyecto no existe) → <strong>404</strong>,
 *       con un mensaje que no revela si el recurso existe.</li>
 *   <li>El llamador <strong>es miembro</strong> pero su rol no basta → <strong>403</strong>.</li>
 * </ul>
 *
 * <p>Se elige un componente explicito y no anotaciones porque centraliza esa politica en un
 * unico sitio y es trivialmente testeable sin contexto HTTP.
 */
@Component
public class ProjectAccessGuard {

    private final ProjectMemberRepository projectMemberRepository;

    public ProjectAccessGuard(ProjectMemberRepository projectMemberRepository) {
        this.projectMemberRepository = projectMemberRepository;
    }

    /**
     * Exige que el llamador sea miembro del proyecto (cualquier rol).
     *
     * @param projectId identificador del proyecto
     * @param caller llamador autenticado
     * @return la membresia del llamador
     */
    public ProjectMember requireMember(Long projectId, CurrentUser caller) {
        return projectMemberRepository
                .findByProjectIdAndUserId(projectId, caller.userId())
                .orElseThrow(() -> new ApiException.ResourceNotFound(
                        ErrorCode.PROJECT_NOT_FOUND, "El proyecto no existe o no tienes acceso"));
    }

    /**
     * Indica si un usuario es miembro del proyecto, sin lanzar excepcion.
     *
     * <p>Se usa para validar reglas que no son de autorizacion del llamador, sino de
     * integridad del dato (por ejemplo, que el asignado de una tarea pertenezca al
     * proyecto).
     *
     * @param projectId identificador del proyecto
     * @param userId identificador del usuario
     * @return {@code true} si el usuario es miembro
     */
    public boolean isMember(Long projectId, Long userId) {
        return projectMemberRepository.existsByProjectIdAndUserId(projectId, userId);
    }

    /**
     * Exige rol {@code EDITOR} o superior.
     *
     * @param projectId identificador del proyecto
     * @param caller llamador autenticado
     * @return la membresia del llamador
     */
    public ProjectMember requireEditor(Long projectId, CurrentUser caller) {
        ProjectMember member = requireMember(projectId, caller);
        if (!member.getRole().canEdit()) {
            throw forbidden("Necesitas permiso de editor en este proyecto para realizar esta operacion");
        }
        return member;
    }

    /**
     * Exige rol {@code OWNER}.
     *
     * @param projectId identificador del proyecto
     * @param caller llamador autenticado
     * @return la membresia del llamador
     */
    public ProjectMember requireOwner(Long projectId, CurrentUser caller) {
        ProjectMember member = requireMember(projectId, caller);
        if (member.getRole() != ProjectRole.OWNER) {
            throw forbidden("Solo el propietario del proyecto puede realizar esta operacion");
        }
        return member;
    }

    /**
     * Exige rol {@code OWNER} o que el llamador sea administrador de plataforma.
     *
     * <p>El administrador de plataforma puede operar sobre cualquier proyecto sin necesidad
     * de ser miembro (ADR-0004), pero no aparece en los listados normales de los demas.
     *
     * @param projectId identificador del proyecto
     * @param caller llamador autenticado
     */
    public void requireOwnerOrAdmin(Long projectId, CurrentUser caller) {
        if (caller.isAdmin()) {
            return;
        }
        requireOwner(projectId, caller);
    }

    private static ApiException.Forbidden forbidden(String detail) {
        return new ApiException.Forbidden(ErrorCode.FORBIDDEN, detail);
    }
}
