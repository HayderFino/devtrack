package com.devtrack.task.domain;

/**
 * Estado de una tarea.
 *
 * <p>Las transiciones validas entre estados estan definidas en
 * {@code com.devtrack.task.service.TaskStatusPolicy}.
 */
public enum TaskStatus {

    /** Estado inicial. */
    TODO,

    /** En curso. */
    IN_PROGRESS,

    /** Completada. */
    DONE,

    /** Cancelada. Solo se puede reabrir hacia TODO. */
    CANCELLED
}
