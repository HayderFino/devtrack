package com.devtrack.shared.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.devtrack.user.repository.UserRepository;

/**
 * Filtro que valida el token Bearer y establece la identidad en el contexto de seguridad.
 *
 * <p>Un token ausente, invalido o expirado no produce un error aqui: simplemente no se
 * establece ninguna identidad, y la cadena de seguridad responde 401 al llegar al
 * recurso protegido. Asi se distingue "no autenticado" (401) de "autenticado pero sin
 * permiso" (403).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null) {
            jwtService
                    .parse(token)
                    // Un token emitido antes de deshabilitar la cuenta deja de ser valido.
                    .filter(currentUser -> userRepository.existsByIdAndEnabledTrue(currentUser.userId()))
                    .ifPresent(currentUser -> authenticate(currentUser, token, request));
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(CurrentUser currentUser, String token, HttpServletRequest request) {
        var authentication = new UsernamePasswordAuthenticationToken(
                currentUser,
                token,
                List.of(new SimpleGrantedAuthority("ROLE_" + currentUser.platformRole().name())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
