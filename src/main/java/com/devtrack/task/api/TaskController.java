package com.devtrack.task.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.devtrack.shared.security.CurrentUser;
import com.devtrack.shared.web.PageResponse;
import com.devtrack.task.api.dto.AssignTaskRequest;
import com.devtrack.task.api.dto.CreateTaskRequest;
import com.devtrack.task.api.dto.TaskResponse;
import com.devtrack.task.api.dto.UpdateTaskRequest;
import com.devtrack.task.domain.TaskPriority;
import com.devtrack.task.domain.TaskStatus;
import com.devtrack.task.service.TaskFilter;
import com.devtrack.task.service.TaskService;

/**
 * Endpoints de tareas, anidados bajo el proyecto.
 *
 * <p>El proyecto es la frontera de autorizacion: conocer el identificador de una tarea no
 * da acceso a ella (ADR-0004).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Lista las tareas del proyecto con los filtros aplicados.
     */
    @GetMapping
    public PageResponse<TaskResponse> list(
            @AuthenticationPrincipal CurrentUser caller,
            @PathVariable Long projectId,
            @RequestParam(required = false) List<TaskStatus> status,
            @RequestParam(required = false) List<TaskPriority> priority,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false, defaultValue = "false") boolean unassigned,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueBefore,
            @RequestParam(required = false, defaultValue = "false") boolean overdue,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        TaskFilter filter = new TaskFilter(
                status == null || status.isEmpty() ? null : java.util.Set.copyOf(status),
                priority == null || priority.isEmpty() ? null : java.util.Set.copyOf(priority),
                assigneeId,
                unassigned,
                dueBefore,
                overdue,
                q);

        return taskService.list(projectId, caller, filter, pageable);
    }

    /**
     * Crea una tarea en el proyecto. Requiere rol EDITOR o superior.
     */
    @PostMapping
    public ResponseEntity<TaskResponse> create(
            @AuthenticationPrincipal CurrentUser caller,
            @PathVariable Long projectId,
            @Valid @RequestBody CreateTaskRequest request) {

        TaskResponse created = taskService.create(projectId, caller, request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/tasks/" + created.id()))
                .body(created);
    }

    /**
     * Detalle de una tarea.
     */
    @GetMapping("/{taskId}")
    public TaskResponse get(
            @AuthenticationPrincipal CurrentUser caller, @PathVariable Long projectId, @PathVariable Long taskId) {
        return taskService.findById(projectId, taskId, caller);
    }

    /**
     * Actualiza una tarea. Requiere rol EDITOR o superior.
     */
    @PatchMapping("/{taskId}")
    public TaskResponse update(
            @AuthenticationPrincipal CurrentUser caller,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskRequest request) {

        return taskService.update(projectId, taskId, caller, request);
    }

    /**
     * Asigna o desasigna una tarea. Idempotente.
     */
    @PutMapping("/{taskId}/assignee")
    public TaskResponse assign(
            @AuthenticationPrincipal CurrentUser caller,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody AssignTaskRequest request) {

        return taskService.assign(projectId, taskId, caller, request);
    }

    /**
     * Elimina una tarea.
     */
    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CurrentUser caller, @PathVariable Long projectId, @PathVariable Long taskId) {

        taskService.delete(projectId, taskId, caller);
        return ResponseEntity.noContent().build();
    }
}
