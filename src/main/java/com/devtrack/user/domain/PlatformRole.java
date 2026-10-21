package com.devtrack.user.domain;

/**
 * Rol de <em>plataforma</em>: define permisos que trascienden a un proyecto concreto.
 *
 * <p>No confundir con {@code ProjectRole}, que define el papel de un usuario dentro de un
 * proyecto. Un {@code ADMIN} de plataforma puede operar sobre cualquier proyecto, pero
 * no aparece en los listados de proyectos de los demas usuarios.
 */
public enum PlatformRole {

    /** Usuario normal. */
    USER,

    /** Administrador de la plataforma, para tareas de soporte. */
    ADMIN
}
