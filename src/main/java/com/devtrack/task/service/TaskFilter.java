package com.devtrack.task.service;

import java.time.LocalDate;
import java.util.Set;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.devtrack.task.domain.Task;
import com.devtrack.task.domain.TaskPriority;
import com.devtrack.task.domain.TaskStatus;

/**
 * Filtros de listado de tareas.
 *
 * <p>Se traducen a una {@link Specification} unica, de modo que el listado se resuelve
 * en una sola consulta SQL con las relaciones trayendo.
 *
 * @param statuses estados a incluir; {@code null} o vacio significa "sin filtro"
 * @param priorities prioridades a incluir; {@code null} o vacio significa "sin filtro"
 * @param assigneeId usuario asignado; {@code null} significa "sin filtro"
 * @param unassigned filtrar solo las tareas sin asignar
 * @param dueBefore fecha limite maxima (exclusiva)
 * @param overdue filtrar solo las tareas vencidas y no completadas
 * @param text texto libre a buscar en titulo y descripcion
 */
public record TaskFilter(
        Set<TaskStatus> statuses,
        Set<TaskPriority> priorities,
        Long assigneeId,
        boolean unassigned,
        LocalDate dueBefore,
        boolean overdue,
        String text) {

    public static TaskFilter empty() {
        return new TaskFilter(null, null, null, false, null, false, null);
    }

    /**
     * Construye la especificacion para un proyecto.
     *
     * @param projectId proyecto al que pertenecen las tareas
     * @param today fecha de referencia para el filtro de vencidas
     * @return especificacion combinando todos los filtros
     */
    public Specification<Task> toSpecification(Long projectId, LocalDate today) {
        return (root, query, cb) -> {
            // Fetch explicito de las relaciones: evita el N+1 al construir la respuesta.
            root.fetch("assignee", JoinType.LEFT);
            root.fetch("createdBy", JoinType.LEFT);
            query.distinct(true);

            Predicate predicate = cb.equal(root.get("project").get("id"), projectId);

            if (statuses != null && !statuses.isEmpty()) {
                predicate = cb.and(predicate, root.get("status").in(statuses));
            }
            if (priorities != null && !priorities.isEmpty()) {
                predicate = cb.and(predicate, root.get("priority").in(priorities));
            }
            if (assigneeId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("assignee").get("id"), assigneeId));
            }
            if (unassigned) {
                predicate = cb.and(predicate, cb.isNull(root.get("assignee")));
            }
            if (dueBefore != null) {
                predicate = cb.and(predicate, cb.lessThan(root.get("dueDate"), dueBefore));
            }
            if (overdue) {
                predicate = cb.and(
                        predicate,
                        cb.and(
                                cb.isNotNull(root.get("dueDate")),
                                cb.lessThan(root.get("dueDate"), today),
                                cb.notEqual(root.get("status"), TaskStatus.DONE)));
            }
            if (text != null && !text.isBlank()) {
                String pattern = "%" + text.strip().toLowerCase() + "%";
                Predicate byTitle = cb.like(cb.lower(root.get("title")), pattern);
                Predicate byDescription = cb.like(cb.lower(root.get("description")), pattern);
                predicate = cb.and(predicate, cb.or(byTitle, byDescription));
            }

            return predicate;
        };
    }
}
