package com.devtrack.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;

import com.devtrack.shared.exception.ApiException;
import com.devtrack.shared.exception.ErrorCode;

/**
 * Rechaza tamanos de pagina fuera del rango permitido.
 *
 * <p>{@code spring.data.web.pageable.max-page-size} recorta en silencio los valores grandes;
 * el contrato exige un 400 explicito para que el cliente sepa que su peticion no se atendio
 * como pidio.
 */
public class PageSizeInterceptor implements HandlerInterceptor {

    static final int MAX_PAGE_SIZE = 100;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String rawSize = request.getParameter("size");
        if (rawSize == null || rawSize.isBlank()) {
            return true;
        }

        int size;
        try {
            size = Integer.parseInt(rawSize.strip());
        } catch (NumberFormatException ex) {
            throw invalidSize();
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw invalidSize();
        }
        return true;
    }

    private static ApiException invalidSize() {
        return new ApiException(
                ErrorCode.INVALID_PARAMETER,
                "El parametro 'size' debe ser un entero entre 1 y " + MAX_PAGE_SIZE);
    }
}
