package com.devtrack.task.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devtrack.task.domain.Task;
import com.devtrack.task.domain.TaskPriority;
import com.devtrack.task.domain.TaskStatus;

/**
 * Repositorio de tareas.
 *
 * <p>El filtrado se resuelve con {@link JpaSpecificationExecutor} para mantener una sola
 * consulta con las relaciones trayendo (evita el N+1 al montar la respuesta).
 */
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /** Busca una tarea dentro de un proyecto concreto. */
    Optional<Task> findByIdAndProjectId(Long id, Long projectId);

    long countByProjectId(Long projectId);

    long countByProjectIdAndAssigneeIdIsNull(Long projectId);

    long countByProjectIdAndAssigneeId(Long projectId, Long assigneeId);

    /** Tareas vencidas (fecha limite pasada) que no estan completadas. */
    long countByProjectIdAndDueDateLessThanAndStatusNot(Long projectId, LocalDate date, TaskStatus status);

    /** Tareas con vencimiento dentro del rango indicado (ambos extremos incluidos). */
    long countByProjectIdAndDueDateGreaterThanEqualAndDueDateLessThanEqual(
            Long projectId, LocalDate from, LocalDate to);

    /** Recuento de tareas por estado. */
    @Query("select t.status, count(t) from Task t where t.project.id = :projectId group by t.status")
    List<Object[]> countByStatus(@Param("projectId") Long projectId);

    /** Recuento de tareas por prioridad. */
    @Query("select t.priority, count(t) from Task t where t.project.id = :projectId group by t.priority")
    List<Object[]> countByPriority(@Param("projectId") Long projectId);
}
