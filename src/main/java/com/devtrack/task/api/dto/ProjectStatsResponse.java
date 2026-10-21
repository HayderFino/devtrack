package com.devtrack.task.api.dto;

import java.util.Map;

/**
 * Metricas de progreso de un proyecto.
 *
 * <p>El calculo vive en el contexto {@code task} porque todos los datos agregados son
 * tareas: el contexto {@code project} no conoce a {@code task} (regla R-D4 de ADR-0001).
 *
 * @param projectId proyecto al que corresponden las metricas
 * @param totalTasks numero total de tareas del proyecto
 * @param byStatus numero de tareas por estado
 * @param byPriority numero de tareas por prioridad
 * @param unassigned numero de tareas sin asignado
 * @param assignedToMe numero de tareas asignadas al llamador
 * @param overdue numero de tareas vencidas y no completadas
 * @param dueWithinNext7Days numero de tareas con vencimiento en los proximos 7 dias
 * @param completionRate porcentaje de tareas completadas (0..1)
 */
public record ProjectStatsResponse(
        Long projectId,
        long totalTasks,
        Map<String, Long> byStatus,
        Map<String, Long> byPriority,
        long unassigned,
        long assignedToMe,
        long overdue,
        long dueWithinNext7Days,
        double completionRate) {}
