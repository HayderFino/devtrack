/**
 * Contexto acotado de tareas: ciclo de vida de las tareas dentro de un proyecto.
 *
 * <p>Depende de {@code project} para validar la pertenencia del asignado, pero
 * {@code project} no conoce a {@code task} (regla R-D4 de {@code docs/architecture.md}).
 */
package com.devtrack.task;
