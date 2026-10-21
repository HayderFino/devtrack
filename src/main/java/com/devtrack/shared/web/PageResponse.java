package com.devtrack.shared.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Envoltorio de paginacion para las respuestas de la API.
 *
 * <p>No se expone {@code org.springframework.data.domain.Page} directamente: su estructura
 * JSON no es estable entre versiones de Spring Data y produce avisos de serializacion.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean first, boolean last) {

    /**
     * Construye la respuesta a partir de una pagina de Spring Data.
     *
     * @param page pagina de origen
     * @param mapper funcion que convierte cada elemento de la entidad al DTO de salida
     */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
