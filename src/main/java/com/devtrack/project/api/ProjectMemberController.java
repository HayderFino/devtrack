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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.devtrack.shared.config.OpenApiConfig;

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
@Tag(name = "Miembros", description = "Gestion de membresias; solo OWNER puede modificarlas")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    public ProjectMemberController(ProjectMemberService projectMemberService) {
        this.projectMemberService = projectMemberService;
    }

    /**
     * Lista los miembros del proyecto. Cualquier miembro puede consultarlos.
     */
    @GetMapping
    @Operation(summary = "Listar miembros", description = "Cualquier miembro del proyecto puede consultar la lista.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de miembros"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "404", description = "Inexistente o no miembro")
    })
    public List<ProjectMemberResponse> list(@AuthenticationPrincipal CurrentUser caller, @PathVariable Long projectId) {
        return projectMemberService.list(projectId, caller);
    }

    /**
     * Anade un miembro al proyecto. Solo el propietario.
     */
    @PostMapping
    @Operation(summary = "Anadir miembro", description = "Solo OWNER; el usuario debe existir y no ser ya miembro.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Miembro anadido; Location con su URI"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Miembro sin rol suficiente"),
            @ApiResponse(responseCode = "404", description = "Proyecto o usuario inexistente"),
            @ApiResponse(responseCode = "409", description = "El usuario ya es miembro"),
            @ApiResponse(responseCode = "422", description = "Rol no permitido")
    })
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
    @Operation(summary = "Cambiar rol de miembro", description = "Solo OWNER; el rol OWNER no se asigna por esta ruta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Miembro sin rol suficiente"),
            @ApiResponse(responseCode = "404", description = "Proyecto o miembro inexistente"),
            @ApiResponse(responseCode = "409", description = "Cambio de rol no permitido")
    })
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
    @Operation(summary = "Transferir propiedad", description = "Solo el OWNER actual; el userId de la ruta debe coincidir con newOwnerId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Propiedad transferida"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos o ids incoherentes"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Miembro sin rol suficiente"),
            @ApiResponse(responseCode = "404", description = "Proyecto o miembro inexistente"),
            @ApiResponse(responseCode = "422", description = "Destino no es miembro")
    })
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
    @Operation(summary = "Quitar miembro", description = "Solo OWNER; no se puede quitar al propietario.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Miembro eliminado"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Miembro sin rol suficiente"),
            @ApiResponse(responseCode = "404", description = "Proyecto o miembro inexistente"),
            @ApiResponse(responseCode = "409", description = "No se puede quitar al OWNER")
    })
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal CurrentUser caller, @PathVariable Long projectId, @PathVariable Long userId) {

        projectMemberService.remove(projectId, userId, caller);
        return ResponseEntity.noContent().build();
    }
}
