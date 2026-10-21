package com.devtrack.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.devtrack.shared.config.DevTrackProperties;
import com.devtrack.user.domain.PlatformRole;
import com.devtrack.user.domain.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Emision y validacion de tokens JWT (ADR-0003).
 *
 * <p>Tokens stateless firmados con HS256. El payload solo contiene identificadores: los
 * roles <em>de proyecto</em> nunca viajan en el token, porque cambian con frecuencia y
 * obligarian a reemitirlo (ADR-0004).
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtService(DevTrackProperties properties, Clock clock) {
        this.signingKey = buildSigningKey(properties.jwt().secret());
        this.issuer = properties.jwt().issuer();
        this.accessTokenTtl = properties.jwt().accessTokenTtl();
        this.clock = clock;
    }

    /**
     * Emite un token de acceso para el usuario indicado.
     *
     * @param user usuario autenticado
     * @return token JWT en formato compacto
     */
    public String issue(User user) {
        return issue(user, accessTokenTtl);
    }

    /**
     * Emite un token con una vigencia concreta.
     *
     * <p>Existe para poder probar la expiracion sin esperar al reloj real.
     *
     * @param user usuario autenticado
     * @param ttl vigencia del token
     * @return token JWT en formato compacto
     */
    public String issue(User user, Duration ttl) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getPlatformRole().name())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Valida un token y extrae la identidad del llamador.
     *
     * @param token token compacto
     * @return la identidad, o {@link Optional#empty()} si el token no es valido
     */
    public Optional<CurrentUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new CurrentUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    PlatformRole.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Token JWT rechazado: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Vigencia configurada del token de acceso.
     */
    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    private static SecretKey buildSigningKey(String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_SECRET_BYTES) {
            // Fail-fast: preferible a arrancar con una clave debil (riesgo R-04).
            throw new IllegalStateException(
                    "devtrack.jwt.secret debe tener al menos " + MINIMUM_SECRET_BYTES + " bytes; tiene " + keyBytes.length);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
