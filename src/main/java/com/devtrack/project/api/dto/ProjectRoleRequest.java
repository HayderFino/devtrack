package com.devtrack.project.api.dto;

import com.devtrack.project.domain.ProjectRole;

/**
 * Rol de proyecto admitido en las peticiones.
 *
 * <p>Es un enum separado del dominio a proposito: {@code OWNER} no es asignable por las
 * rutas de membresia (la propiedad del proyecto se concede al crearlo o por transferencia
 * explicita), y asi el contrato de la API lo refleja sin tener que validarlo en el
 * servicio.
 */
public enum ProjectRoleRequest {

    EDITOR,
    VIEWER;

    public ProjectRole toDomain() {
        return ProjectRole.valueOf(name());
    }
}
