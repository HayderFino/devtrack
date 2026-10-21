# ADR-0003 · Autenticación por JWT stateless con BCrypt

- **Estado:** PROPUESTO — pendiente de aprobación
- **Fecha:** 2026-10-21
- **Decide sobre:** cómo se autentican los usuarios en la v1
- **Relacionado:** `docs/architecture.md` §5.1, §5.2

## Contexto

DevTrack es una **API backend** consumida por clientes (SPA, móvil, otro servicio). No hay
UI propia ni necesidad de SSO corporativo en la v1. El encargo pide "Spring Security; JWT
cuando se implemente autenticación", y sitúa el refresh token implícitamente en una fase
posterior.

Candidatos habituales:

1. **Sesión con cookie** (`JSESSIONID` + Spring Session).
2. **JWT stateless** en la cabecera `Authorization: Bearer`.
3. **OIDC/OAuth2 contra un proveedor externo.**

## Decisión

**JWT stateless (HS256) en la cabecera `Authorization: Bearer`, con contraseñas hasheadas
con BCrypt. Sin refresh token en la v1.**

| Aspecto | Decisión |
|---|---|
| Mecanismo | JWT HS256, firma simétrica |
| Transporte | Cabecera `Authorization: Bearer <token>` |
| Claims | `sub` (user id), `email`, `role` (rol de plataforma), `iss=devtrack`, `iat`, `exp` |
| TTL de acceso | **15 minutos**, configurable (`devtrack.jwt.access-token-ttl`) |
| Refresh token | **No en la v1** (fase `F2-AUTH`) |
| Hash de contraseña | **BCrypt**, `BCryptPasswordEncoder`, coste 10 (configurable) |
| Sesión HTTP | **Stateless** (`SessionCreationPolicy.STATELESS`), sin cookies |
| CSRF | **Desactivado** (ver justificación abajo) |
| Secreto | Variable de entorno `DEVTRACK_JWT_SECRET`, **mínimo 32 bytes** |
| Comportamiento si falta el secreto | **Fail-fast**: la aplicación no arranca |

## Justificación

### Por qué JWT stateless

- Es el estándar de facto para una API de este tipo y lo que un reclutador espera ver.
- **Escala horizontalmente sin estado compartido**: añadir una instancia no requiere
  sessions compartidas ni Redis.
- El modelo de autorización de DevTrack es *por recurso* ("¿es este usuario miembro de este
  proyecto?"), y esa comprobación **ya requiere ir a la base de datos** en cada petición.
  Añadir una sesión en servidor no aporta cacheo real de permisos.
- El payload del token es pequeño y **no contiene información sensible** (id, email, rol de
  plataforma). Los roles de proyecto **nunca** van en el token: cambian con frecuencia y
  would require re-emisión.

### Por qué BCrypt

- Es la recomendación estándar de Spring Security para contraseñas.
- Incluye **salt** por usuario y es **lento a propósito** por diseño, lo que encarece el
  ataque por fuerza bruta.
- Coste 10 = ~100 ms por verificación en hardware de escritorio. Aceptable para el volumen
  esperado de un portafolio; subirá a 12 si el hardware lo permite.
- Límite de entrada: **72 bytes**. Por eso `password` se valida con `@Size(max = 72)`.
  (Con `@Size` de Spring Boot se cuentan **caracteres**, no bytes; con contraseñas ASCII es
  equivalente. Si se admite Unicode, hay que medir en bytes. **Decisión pendiente menor**:
  la v1 exige longitud en caracteres y documenta el límite en bytes.)

### Por qué HS256 y no RS256

- HS256 es suficiente para un monolito: la misma app emite y verifica.
- RS256 exige generar y custodiar un par de claves RSA, más rotación. Se reserva para
  `F2-SEC`, cuando varios servicios verifiquen tokens sin poder emitirlos.

### Por qué CSRF desactivado

La API es **stateless** y **no usa cookies**: el token viaja en una cabecera `Authorization`
que el navegador **no** adjunta automáticamente. Un atacante externo no puede forzar al
navegador de la víctima a incluir esa cabecera en una petición a la API. El vector clásico
de CSRF (formularios o imágenes que provocan peticiones con credenciales implícitas) **no
existe** aquí.

> **Condición de reversión:** en `F2-AUTH`, si el token pasa a guardarse en una cookie
> `HttpOnly` (para evitar JS y permitir refresh silencioso), **CSRF debe reactivarse** y
> hay que añadir un token anti-CSRF. Este ADR debe actualizarse en ese momento.

### Por qué fail-fast del secreto

Un valor por defecto hardcodeado (típicamente `"change-me"`) es un **fallo de seguridad
que llega a producción** con una alta probabilidad, porque "funciona" en desarrollo.
Si el secreto es débil o missing, la aplicación **no debe arrancar**. Es la opción segura
por defecto.

## Alternativas descartadas

| Alternativa | Por qué no |
|---|---|
| Sesión con cookie (`JSESSIONID`) | Requiere estado en servidor (o Redis), complica el escalado horizontal y obliga a gestionar cookies, CORS con credenciales y CSRF |
| Refresh token en la v1 | Duplica la superficie (tabla de refresh tokens, rotación, revocación, detección de reuse) sin valor para un portafolio. Está planificado en `F2-AUTH` |
| OAuth2/OIDC contra un proveedor externo | Añade una dependencia externa y una complejidad de configuración que no aporta valor propio en la v1. `F2-AUTH` |
| `BCrypt` con coste 4 o inferior | Demasiado rápido: idealmente se puede probar a ~100k contraseñas/s por GPU |
| `NoOpPasswordEncoder` o MD5/SHA-1 | Inaceptable. Sin hashing con salt, las fugas de la BD dan las contraseñas directamente |
| Guardar el token en `localStorage` | Decisión de **cliente**, no del backend. Se documenta en el README: para una SPA, la opción preferible es cookie `HttpOnly` + refresh token (con CSRF reactivado) |

## Consecuencias

### Positivas

- Sin estado en servidor: escalado horizontal trivial.
- Los tests de integración pueden usar el mismo flujo de auth que producción, sin mocks
  especiales.
- El formato de error 401 es uniforme con el resto de la API (`ProblemDetail`).

### Negativas

- **Revocación inmediata imposible.** Un token emitido sigue siendo válido hasta 15 minutos
  aunque se deshabilite la cuenta. Mitigación: `users.enabled=false` se comprueba **en cada
  petición** (no solo en el login), de modo que deshabilitar la cuenta surte efecto
  inmediato aunque el token siga siendo criptográficamente válido.
- Si el secreto se filtra, hay que rotarlo, lo que invalida todos los tokens emitidos.
  En `F2-SEC` se evaluará RS256 con rotación de claves.
- El cliente debe renovar el token cada 15 minutos. En `F2-AUTH` se añade refresh token.

## Fecha de revisión

Al iniciar la fase `F2-AUTH`.
