package com.devtrack.shared.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Activa la auditoria JPA, que rellena {@code createdAt} y {@code updatedAt} en todas las
 * entidades que extienden {@link BaseEntity}.
 *
 * <p>Se declara en una clase propia (y no en la aplicacion) para que los tests de
 * integracion puedan desactivarla si lo necesitan.
 */
@Configuration
@EnableJpaAuditing
public class AuditingConfig {}
