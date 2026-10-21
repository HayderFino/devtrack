package com.devtrack.project.api;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.devtrack.project.api.dto.CreateProjectRequest;
import com.devtrack.project.api.dto.ProjectResponse;
import com.devtrack.project.api.dto.UpdateProjectRequest;
import com.devtrack.project.service.ProjectService;
import com.devtrack.shared.security.CurrentUser;
import com.devtrack.shared.web.PageResponse;

/**
 * Endpoints de proyectos.
 *
 * <p>Sin logica de negocio: validan la entrada, delegan en {@link ProjectService} y
 * traducen la respuesta.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * Crea un proyecto. El creator se convierte en propietario.
     */
    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            @AuthenticationPrincipal CurrentUser caller, @Valid @RequestBody CreateProjectRequest request) {

        ProjectResponse created = projectService.create(caller, request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + created.id())).body(created);
    }

    /**
     * Lista los proyectos de los que el llamador es miembro.
     *
     * @param archived filtro: {@code true}, {@code false} (por defecto) o {@code all}
     */
    @GetMapping
    public PageResponse<ProjectResponse> list(
            @AuthenticationPrincipal CurrentUser caller,
            @RequestParam(required = false) String archived,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {

        return projectService.listForUser(caller, archived, pageable);
    }

    /**
     * Detalle de un proyecto.
     */
    @GetMapping("/{projectId}")
    public ProjectResponse get(@AuthenticationPrincipal CurrentUser caller, @PathVariable Long projectId) {
        return projectService.findByIdForMember(projectId, caller);
    }

    /**
     * Actualiza un proyecto. Solo el propietario.
     */
    @PatchMapping("/{projectId}")
    public ProjectResponse update(
            @AuthenticationPrincipal CurrentUser caller,
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request) {

        return projectService.update(projectId, caller, request);
    }

    /**
     * Archiva un proyecto (borrado logico). Solo el propietario.
     */
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> archive(@AuthenticationPrincipal CurrentUser caller, @PathVariable Long projectId) {
        projectService.archive(projectId, caller);
        return ResponseEntity.noContent().build();
    }
}
