package com.devtrack.project.domain;

/**
 * Rol de un usuario <em>dentro de un proyecto</em>.
 *
 * <p>No confundir con {@code PlatformRole}, que define permisos sobre toda la plataforma.
 * Los roles de proyecto son jerarquicos: {@code OWNER} ⊃ {@code EDITOR} ⊃ {@code VIEWER}.
 */
public enum ProjectRole {

    /** Propietario del proyecto. exactamente uno por proyecto. */
    OWNER,

    /** Puede crear, editar, reasignar y eliminar tareas. */
    EDITOR,

    /** Solo lectura. */
    VIEWER;

    /** Indica si este rol permite modificar el contenido del proyecto. */
    public boolean canEdit() {
        return this == OWNER || this == EDITOR;
    }
}
