/**
 * Manejo centralizado de excepciones y catalogo de codigos de error.
 *
 * <p>Las excepciones se traducen a respuestas {@code application/problem+json} (RFC 7807)
 * con un codigo de negocio estable, segun ADR-0005.
 */
package com.devtrack.shared.exception;
