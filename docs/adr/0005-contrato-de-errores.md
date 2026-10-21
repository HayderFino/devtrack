# ADR-0005 · Contrato de errores: `ProblemDetail` (RFC 7807) con código de negocio estable

- **Estado:** PROPUESTO — pendiente de aprobación
- **Fecha:** 2026-10-21
- **Decide sobre:** formato de las respuestas de error
- **Relacionado:** `docs/architecture.md` §7 (punto 7 de `api-plan.md`), `docs/api-plan.md` §1.2, §2

## Contexto

Una API sin un contrato de errores definido produce tres problemas:

1. Cada endpoint inventa su propio formato (`{"message": "..."}`, `{"error": "..."}`,
   `{"timestamp": ..., "msg": ...}`).
2. El cliente no puede ramificar de forma fiable: tiene que parsear texto libre.
3. Los errores de seguridad (401/403) suelen devolver un cuerpo completamente distinto
   —o vacío— porque los genera la cadena de filtros de Spring Security, no los controladores.

## Decisión

**Todos los errores de la API se expresan como `application/problem+json`
(`org.springframework.problemdetail.ProblemDetail`, RFC 7807 / RFC 9457), con una extensión
propia `code` y un catálogo centralizado.**

### Formato

```json
{
  "type": "https://devtrack.dev/problems/resource-not-found",
  "title": "Recurso no encontrado",
  "status": 404,
  "detail": "No existe un proyecto con id 42",
  "instance": "/api/v1/projects/42",
  "code": "PROJECT_NOT_FOUND",
  "timestamp": "2026-10-21T14:30:00.000Z",
  "errors": [
    { "field": "title", "message": "no debe estar en blanco", "rejectedValue": "" }
  ]
}
```

- `errors[]` **solo** aparece en errores de validación (400).
- `code` es un **identificador estable de negocio**, separado del código HTTP. Permite que el
  cliente ramifique (`code == "OPTIMISTIC_LOCK_CONFLICT"` → "reintentar") sin parsear `detail`.
- `detail` es un mensaje **legible y en español**, y **nunca** contiene datos de otros
  usuarios, stack traces ni nombres de tablas.
- `type` es un URI de documentación del problema (no tiene que resolver a un documento
  real, pero debe ser estable).

### Catálogo centralizado

`shared.exception.ErrorCatalog` es la única fuente de la correspondencia
`(code, httpStatus, title)`. Ni el controlador ni el servicio inventan códigos.

### Traducción de excepciones a respuestas

| Excepción | HTTP | `code` |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` (+ `errors[]`) |
| `HttpMessageNotReadableException` | 400 | `MALFORMED_JSON` |
| `BadCredentialsException` | 401 | `INVALID_CREDENTIALS` |
| Sin token / token inválido / expirado | 401 | `UNAUTHENTICATED` |
| `AccountDisabledException` | 403 | `ACCOUNT_DISABLED` |
| Rol insuficiente | 403 | `FORBIDDEN` |
| `ResourceNotFoundException` | 404 | `PROJECT_NOT_FOUND` / `TASK_NOT_FOUND` / … |
| `ConflictException` | 409 | `EMAIL_ALREADY_REGISTERED` / `MEMBER_ALREADY_EXISTS` / … |
| `OptimisticLockingFailureException` | 409 | `OPTIMISTIC_LOCK_CONFLICT` |
| `UnprocessableEntityException` | 422 | `INVALID_STATUS_TRANSITION` / `ASSIGNEE_NOT_A_MEMBER` / … |
| No previsto | 500 | `INTERNAL_ERROR` |

### Un solo punto de manejo

- **`GlobalExceptionHandler`** (`@RestControllerAdvice`) maneja las excepciones de aplicación.
- **`RestAuthenticationEntryPoint`** y **`RestAccessDeniedHandler`** manejan los errores
  generados **dentro de la cadena de filtros** de Spring Security, que **no pasan por el
  advice**. Ambos construyen el mismo `ProblemDetail`, con el mismo catálogo.

## Justificación

### Por qué `ProblemDetail` y no un formato propio

- Es el **estándar** (RFC 7807, actualizado a RFC 9457). Spring Boot lo soporta de forma
  nativa: `ProblemDetail` se serializa correctamente sin código extra, y Spring Security 6+
  ya devuelve `ProblemDetail` en sus respuestas por defecto.
- **No se inventa un formato**: un cliente ya sabe cómo interpretarlo.
- `ResponseEntityExceptionHandler` (de Spring) resuelve de antemano el mapeo de las
  excepciones estándar de Spring MVC (`@RestControllerAdvice` que lo extiende), lo que
  garantiza que un `400` por JSON malformado tenga el mismo formato que un `400` por
  validación.
- El soporte de OpenAPI: `ProblemDetail` se puede declarar como esquema de respuesta y
  springdoc lo refleja en el documento.

### Por qué `code` además de `status`

- El status HTTP es demasiado grueso: `409` significa cinco cosas distintas en DevTrack
  (email duplicado, miembro duplicado, owner inmutable, conflicto de versión, y en el
  futuro más).
- `code` es estable y testeable: el test comprueba `jsonPath("$.code").value("...")`, no una
  cadena de texto libre.

### Por qué un catálogo centralizado

- Evita que dos endpoints usen el mismo `code` para cosas distintas.
- Permite documentar el catálogo en un solo sitio y generar la tabla del README y de
  OpenAPI a partir de ella.
- Hace trivial añadir un código nuevo: se añade al enum y ya.

## Consecuencias

### Positivas

- Formato uniforme en toda la API, **incluidos los errores de seguridad**, que es donde
  la mayoría de APIs fallan.
- El cliente puede ramificar de forma fiable.
- Tests simples: `status` + `$.code` + `$.content-type`.
- El `detail` genérico en el 500 evita filtrar información interna.

### Negativas

- Un poco más de código que un `Map<String, Object>`: hay que construir el `ProblemDetail`
  con los campos de extensión. Se mitiga con un helper (`ErrorCatalog.toProblemDetail(code, detail)`).
- `ProblemDetail` en Spring MVC produce un `application/problem+json` que algunos clientes
  legacy no saben interpretar. Es un coste aceptado y documentado.
- El `detail` en español implica que el mensaje está **hardcodeado** en el catálogo y no se
  puede i18n-izar sin cambiarlo. Aceptado en la v1 (supuesto A-14).

## Fecha de revisión

En la fase `F3-FEAT`, si se introduce i18n: habrá que decidir si `detail` se externaliza a
un bundle de mensajes o se mantiene fijo y solo se traduce `title`.
