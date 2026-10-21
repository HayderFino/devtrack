/**
 * Seguridad: emision y validacion de JWT, filtro de autenticacion, handlers 401/403 y
 * el guard de autorizacion por membresia de proyecto.
 *
 * <p>Segun ADR-0004, la autorizacion por rol de proyecto se resuelve con
 * {@code ProjectAccessGuard} y no con anotaciones {@code @PreAuthorize}, porque el rol
 * depende del recurso concreto.
 */
package com.devtrack.shared.security;
