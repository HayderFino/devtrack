package com.devtrack.task.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtrack.project.domain.Project;
import com.devtrack.project.domain.ProjectMember;
import com.devtrack.project.repository.ProjectRepository;
import com.devtrack.shared.exception.ApiException;
import com.devtrack.shared.exception.ErrorCode;
import com.devtrack.shared.security.CurrentUser;
import com.devtrack.shared.security.ProjectAccessGuard;
import com.devtrack.shared.web.PageResponse;
import com.devtrack.task.api.dto.AssignTaskRequest;
import com.devtrack.task.api.dto.CreateTaskRequest;
import com.devtrack.task.api.dto.TaskResponse;
import com.devtrack.task.api.dto.UpdateTaskRequest;
import com.devtrack.task.domain.Task;
import com.devtrack.task.domain.TaskStatus;
import com.devtrack.task.repository.TaskRepository;
import com.devtrack.user.domain.User;
import com.devtrack.user.repository.UserRepository;

/**
 * Casos de uso de tareas.
 *
 * <p>El proyecto es la frontera de autorizacion: toda operacion comprueba primero la
 * membresia del llamador en el proyecto (ADR-0004). La asignacion exige, ademas, que el
 * asignado sea miembro del proyecto.
 */
@Service
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectAccessGuard accessGuard;
    private final TaskStatusPolicy statusPolicy;
    private final Clock clock;

    public TaskService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            ProjectAccessGuard accessGuard,
            TaskStatusPolicy statusPolicy,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
        this.statusPolicy = statusPolicy;
        this.clock = clock;
    }

    /**
     * Crea una tarea en el proyecto. Requiere rol {@code EDITOR} o superior.
     *
     * @param projectId proyecto destino
     * @param caller usuario autenticado
     * @param request datos de la tarea
     * @return la tarea creada
     */
    public TaskResponse create(Long projectId, CurrentUser caller, CreateTaskRequest request) {
        accessGuard.requireEditor(projectId, caller);

        Project project = requireProject(projectId);
        User creator = requireUser(caller.userId());

        User assignee = null;
        if (request.assigneeId() != null) {
            assignee = requireMemberOfProject(projectId, request.assigneeId());
        }

        Task task = new Task(project, request.title().strip(), request.description(), creator);
        task.setStatus(request.statusOrDefault());
        task.setPriority(request.priorityOrDefault());
        task.setDueDate(request.dueDate());
        task.setAssignee(assignee);

        return TaskResponse.from(taskRepository.save(task), today());
    }

    /**
     * Detalle de una tarea. Requiere ser miembro del proyecto.
     */
    @Transactional(readOnly = true)
    public TaskResponse findById(Long projectId, Long taskId, CurrentUser caller) {
        accessGuard.requireMember(projectId, caller);
        return TaskResponse.from(requireTask(projectId, taskId), today());
    }

    /**
     * Lista las tareas del proyecto con los filtros aplicados. Requiere ser miembro.
     */
    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(
            Long projectId, CurrentUser caller, TaskFilter filter, Pageable pageable) {

        accessGuard.requireMember(projectId, caller);

        Page<Task> page = taskRepository.findAll(filter.toSpecification(projectId, today()), pageable);
        LocalDate today = today();
        return PageResponse.from(page, task -> TaskResponse.from(task, today));
    }

    /**
     * Actualiza una tarea. Requiere rol {@code EDITOR} o superior.
     */
    public TaskResponse update(Long projectId, Long taskId, CurrentUser caller, UpdateTaskRequest request) {
        accessGuard.requireEditor(projectId, caller);
        Task task = requireTask(projectId, taskId);

        if (request.title() != null) {
            task.setTitle(request.title().strip());
        }
        if (request.description() != null) {
            task.setDescription(request.description());
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }
        if (request.status() != null && request.status() != task.getStatus()) {
            statusPolicy.validate(task.getStatus(), request.status());
            task.setStatus(request.status());
        }

        // Fuerza el UPDATE para que la version incrementada llegue al cliente y un conflicto
        // de locking se traduzca aqui, no al cerrar la transaccion.
        taskRepository.flush();
        return TaskResponse.from(task, today());
    }

    /**
     * Asigna o desasigna una tarea. Idempotente.
     *
     * @param projectId proyecto de la tarea
     * @param taskId tarea
     * @param caller usuario autenticado
     * @param request usuario asignado, o {@code null} para desasignar
     * @return la tarea actualizada
     */
    public TaskResponse assign(Long projectId, Long taskId, CurrentUser caller, AssignTaskRequest request) {
        accessGuard.requireEditor(projectId, caller);
        Task task = requireTask(projectId, taskId);

        if (request.assigneeId() == null) {
            task.setAssignee(null);
        } else {
            task.setAssignee(requireMemberOfProject(projectId, request.assigneeId()));
        }

        taskRepository.flush();
        return TaskResponse.from(task, today());
    }

    /**
     * Elimina una tarea.
     *
     * <p>Un {@code EDITOR} solo puede eliminar las tareas que ha creado; el
     * {@code OWNER} puede eliminar cualquiera.
     */
    public void delete(Long projectId, Long taskId, CurrentUser caller) {
        ProjectMember membership = accessGuard.requireEditor(projectId, caller);
        Task task = requireTask(projectId, taskId);

        boolean owner = membership.getRole() == com.devtrack.project.domain.ProjectRole.OWNER;
        boolean creator = task.getCreatedBy() != null && task.getCreatedBy().getId().equals(caller.userId());

        if (!owner && !creator) {
            throw new ApiException.Unprocessable(
                    ErrorCode.TASK_DELETION_NOT_ALLOWED,
                    "Solo puedes eliminar las tareas que has creado. Pide al propietario del proyecto que la elimine.");
        }

        taskRepository.delete(task);
    }

    // ---------------------------------------------------------------------------------------------

    private Task requireTask(Long projectId, Long taskId) {
        return taskRepository
                .findByIdAndProjectId(taskId, projectId)
                .orElseThrow(() -> new ApiException.ResourceNotFound(
                        ErrorCode.TASK_NOT_FOUND, "No existe una tarea con id " + taskId + " en el proyecto " + projectId));
    }

    private Project requireProject(Long projectId) {
        return projectRepository
                .findById(projectId)
                .orElseThrow(() -> new ApiException.ResourceNotFound(
                        ErrorCode.PROJECT_NOT_FOUND, "No existe un proyecto con id " + projectId));
    }

    private User requireUser(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException.ResourceNotFound(
                        ErrorCode.USER_NOT_FOUND, "No existe un usuario con id " + userId));
    }

    /**
     * Resuelve un usuario exigiendo que sea miembro del proyecto.
     *
     * @throws ApiException.Unprocessable si el usuario no pertenece al proyecto
     */
    private User requireMemberOfProject(Long projectId, Long userId) {
        User user = requireUser(userId);
        if (!accessGuard.isMember(projectId, userId)) {
            throw new ApiException.Unprocessable(
                    ErrorCode.ASSIGNEE_NOT_A_MEMBER, "El usuario con id " + userId + " no es miembro del proyecto");
        }
        return user;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
