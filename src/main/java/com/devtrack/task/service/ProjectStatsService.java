package com.devtrack.task.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtrack.shared.security.CurrentUser;
import com.devtrack.shared.security.ProjectAccessGuard;
import com.devtrack.task.api.dto.ProjectStatsResponse;
import com.devtrack.task.domain.TaskPriority;
import com.devtrack.task.domain.TaskStatus;
import com.devtrack.task.repository.TaskRepository;

/**
 * Metricas de progreso de un proyecto.
 *
 * <p>Vive en el contexto {@code task} porque todos los datos que agrega son tareas: el
 * contexto {@code project} no conoce a {@code task} y no debe (regla R-D4 de ADR-0001).
 */
@Service
@Transactional(readOnly = true)
public class ProjectStatsService {

    private static final int HORIZON_DAYS = 7;

    private final TaskRepository taskRepository;
    private final ProjectAccessGuard accessGuard;
    private final Clock clock;

    public ProjectStatsService(TaskRepository taskRepository, ProjectAccessGuard accessGuard, Clock clock) {
        this.taskRepository = taskRepository;
        this.accessGuard = accessGuard;
        this.clock = clock;
    }

    /**
     * Calcula las metricas de progreso del proyecto.
     *
     * <p>Cualquier miembro del proyecto puede consultarlas (rol VIEWER incluido).
     *
     * @param projectId identificador del proyecto
     * @param caller usuario autenticado
     * @return las metricas
     */
    public ProjectStatsResponse calculate(Long projectId, CurrentUser caller) {
        accessGuard.requireMember(projectId, caller);

        LocalDate today = LocalDate.now(clock);

        Map<String, Long> byStatus = statusCounts(projectId);
        Map<String, Long> byPriority = priorityCounts(projectId);

        long total = taskRepository.countByProjectId(projectId);
        long done = byStatus.getOrDefault(TaskStatus.DONE.name(), 0L);
        long overdue = taskRepository.countByProjectIdAndDueDateLessThanAndStatusNot(
                projectId, today, TaskStatus.DONE);

        return new ProjectStatsResponse(
                projectId,
                total,
                byStatus,
                byPriority,
                taskRepository.countByProjectIdAndAssigneeIdIsNull(projectId),
                taskRepository.countByProjectIdAndAssigneeId(projectId, caller.userId()),
                overdue,
                taskRepository.countByProjectIdAndDueDateGreaterThanEqualAndDueDateLessThanEqual(
                        projectId, today, today.plusDays(HORIZON_DAYS)),
                // Proporcion de tareas completadas, en el intervalo [0,1] con dos decimales.
                total == 0 ? 0d : Math.round((done * 100d) / total) / 100d);
    }

    /**
     * Recuento por estado. Incluye todos los estados, aunque no haya tareas en ellos, para
     * que el cliente pueda pintar la matriz completa sin tratarlos como nulos.
     */
    private Map<String, Long> statusCounts(Long projectId) {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, 0L);
        }
        for (Object[] row : taskRepository.countByStatus(projectId)) {
            counts.put((TaskStatus) row[0], (Long) row[1]);
        }
        return toStringKeys(counts);
    }

    /** Recuento por prioridad, con el mismo criterio que {@link #statusCounts(Long)}. */
    private Map<String, Long> priorityCounts(Long projectId) {
        Map<TaskPriority, Long> counts = new EnumMap<>(TaskPriority.class);
        for (TaskPriority priority : TaskPriority.values()) {
            counts.put(priority, 0L);
        }
        for (Object[] row : taskRepository.countByPriority(projectId)) {
            counts.put((TaskPriority) row[0], (Long) row[1]);
        }
        return toStringKeys(counts);
    }

    private static <E extends Enum<E>> Map<String, Long> toStringKeys(Map<E, Long> source) {
        Map<String, Long> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key.name(), value));
        return result;
    }
}
