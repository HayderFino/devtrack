package com.devtrack.task.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devtrack.shared.security.CurrentUser;
import com.devtrack.task.api.dto.ProjectStatsResponse;
import com.devtrack.task.service.ProjectStatsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Metricas de progreso de un proyecto.
 *
 * <p>El controlador vive en el contexto {@code task} (y no en {@code project}) porque las
 * metricas se calculan sobre tareas, y el contexto {@code project} no debe conocer al
 * contexto {@code task}: la dependencia entre contextos es unidireccional (regla R-D4).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/stats")
@Tag(name = "Metricas", description = "Metricas de progreso de un proyecto")
public class ProjectStatsController {

    private final ProjectStatsService projectStatsService;

    public ProjectStatsController(ProjectStatsService projectStatsService) {
        this.projectStatsService = projectStatsService;
    }

    /**
     * Metricas de progreso del proyecto. Cualquier miembro puede consultarlas.
     */
    @GetMapping
    @Operation(summary = "Metricas de progreso del proyecto (tareas por estado y prioridad)")
    @ApiResponse(responseCode = "200", description = "Las metricas del proyecto")
    @ApiResponse(responseCode = "404", description = "El proyecto no existe o el llamador no es miembro")
    public ProjectStatsResponse stats(@AuthenticationPrincipal CurrentUser caller, @PathVariable Long projectId) {
        return projectStatsService.calculate(projectId, caller);
    }
}
