package com.devtrack.task.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.devtrack.task.domain.Task;

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

    /** Numero de tareas asignadas a un usuario dentro de un proyecto. */
    long countByProjectIdAndAssigneeId(Long projectId, Long assigneeId);
}
