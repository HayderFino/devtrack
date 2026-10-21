package com.devtrack.project.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.devtrack.project.api.dto.ProjectMemberResponse;
import com.devtrack.project.api.dto.ProjectResponse;
import com.devtrack.project.api.dto.TransferOwnershipRequest;
import com.devtrack.project.api.dto.UpdateMemberRoleRequest;
import com.devtrack.project.api.dto.UpsertMemberRequest;
import com.devtrack.project.service.ProjectMemberService;
import com.devtrack.shared.exception.ApiException;
import com.devtrack.shared.exception.ErrorCode;
import com.devtrack.shared.security.CurrentUser;

/**
 * Endpoints de membresia de proyecto.
 *
 * <p>El miembro se identifica por {@code userId}, no por el identificador interno de la
 * fila de membresia: ese identificador no significa nada para el cliente.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    public ProjectMemberController(ProjectMemberService projectMemberService) {
        this.projectMemberService = projectMemberService;
    }

    /**
     * Lista los miembros del proyecto. Cualquier miembro puede consultarlos.
     */
    @GetMapping
    public List<ProjectMemberResponse> list(@AuthenticationPrincipal CurrentUser caller, @PathVariable Long projectId) {
        return projectMemberService.list(projectId, caller);
    }

    /**
     * Anade un miembro al proyecto. Solo el propietario.
     */
    @PostMapping
    public ResponseEntity<ProjectMemberResponse> add(
            @AuthenticationPrincipal CurrentUser caller,
            @PathVariable Long projectId,
            @Valid @RequestBody UpsertMemberRequest request) {

        ProjectMemberResponse created = projectMemberService.add(projectId, caller, request);
        URI location = URI.create("/api/v1/projects/" + projectId + "/members/" + created.userId());
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Cambia el rol de un miembro. Solo el propietario.
     */
    @PatchMapping("/{userId}")
    public ProjectMemberResponse changeRole(
            @AuthenticationPrincipal CurrentUser caller,
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {

        return projectMemberService.changeRole(projectId, userId, caller, request);
    }

    /**
     * Transfiere la propiedad del proyecto a otro miembro. Solo el propietario actual.
     */
    @PutMapping("/{userId}/ownership")
    public ProjectResponse transferOwnership(
            @AuthenticationPrincipal CurrentUser caller,
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @Valid @RequestBody TransferOwnershipRequest request) {

        if (!userId.equals(request.newOwnerId())) {
            throw new ApiException(
                    ErrorCode.INVALID_PARAMETER,
                    "El 'userId' de la ruta debe coincidir con 'newOwnerId' del cuerpo");
        }
        return projectMemberService.transferOwnership(projectId, caller, request);
    }

    /**
     * Quita un miembro del proyecto. Solo el propietario.
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal CurrentUser caller, @PathVariable Long projectId, @PathVariable Long userId) {

        projectMemberService.remove(projectId, userId, caller);
        return ResponseEntity.noContent().build();
    }
}
