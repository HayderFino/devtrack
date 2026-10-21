package com.devtrack.task.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.devtrack.shared.exception.ApiException;
import com.devtrack.shared.exception.ErrorCode;
import com.devtrack.task.domain.TaskStatus;

/**
 * Politica de transiciones de estado de una tarea.
 *
 * <p>Componente sin dependencias de Spring en su logica, para poder probarlo de forma
 * aislada. Define la matriz de transiciones permitidas:
 *
 * <pre>
 *   TODO        &lt;-&gt; IN_PROGRESS &lt;-&gt; DONE
 *     |  ^            |                  |
 *     v  |            v                  v
 *   CANCELLED    CANCELLED          CANCELLED
 * </pre>
 *
 * <p>Reglas:
 * <ul>
 *   <li>Una transicion al <strong>mismo</strong> estado es un no-op permitido.</li>
 *   <li>{@code DONE -> TODO} esta <strong>prohibida</strong>: una tarea completada se
 *       reabre a {@code IN_PROGRESS}, nunca vuelve al backlog.</li>
 *   <li>{@code CANCELLED} solo se reabre hacia {@code TODO}.</li>
 * </ul>
 */
@Component
public class TaskStatusPolicy {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = buildTransitions();

    private static Map<TaskStatus, Set<TaskStatus>> buildTransitions() {
        Map<TaskStatus, Set<TaskStatus>> transitions = new EnumMap<>(TaskStatus.class);

        transitions.put(
                TaskStatus.TODO,
                EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.DONE, TaskStatus.CANCELLED));
        transitions.put(
                TaskStatus.IN_PROGRESS,
                EnumSet.of(TaskStatus.TODO, TaskStatus.DONE, TaskStatus.CANCELLED));
        transitions.put(
                TaskStatus.DONE,
                EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED));
        transitions.put(
                TaskStatus.CANCELLED,
                EnumSet.of(TaskStatus.TODO));

        return Map.copyOf(transitions);
    }

    /**
     * Indica si la transicion esta permitida.
     *
     * @param from estado actual
     * @param to estado destino
     * @return {@code true} si la transicion es valida o es un no-op
     */
    public boolean isAllowed(TaskStatus from, TaskStatus to) {
        if (from == to) {
            return true;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Valida la transicion y falla si no esta permitida.
     *
     * @param from estado actual
     * @param to estado destino
     * @throws ApiException.Unprocessable con codigo {@code INVALID_STATUS_TRANSITION}
     */
    public void validate(TaskStatus from, TaskStatus to) {
        if (!isAllowed(from, to)) {
            throw new ApiException.Unprocessable(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "No se puede pasar de " + from + " a " + to + ". Transiciones permitidas desde "
                            + from + ": " + ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()));
        }
    }
}
