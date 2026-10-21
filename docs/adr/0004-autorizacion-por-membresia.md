# ADR-0004 · Autorización por membresía de proyecto, con 404 para no-miembros

- **Estado:** **Aceptado** (2026-10-21)
- **Fecha:** 2026-10-21
- **Decide sobre:** modelo de permisos y traducción de "no autorizado" a código HTTP
- **Relacionado:** `docs/architecture.md` §5.3

## Contexto

DevTrack tiene recursos (proyectos, tareas) que pertenecen a una organización (el proyecto).
Cada usuario tiene un **rol dentro de cada proyecto**: `OWNER`, `EDITOR` o `VIEWER`.

El requisito explícito del encargo es:

> "Aplicar autorización en el servidor: un usuario no debe acceder a proyectos o tareas solo
> por conocer sus IDs."

Esto descarta por completo el modelo "IDOR por descuido" y obliga a una comprobación
**en cada operación** que toque un recurso protegido.

Hay dos decisiones que tomar aquí:

1. **Dónde vive la comprobación** (componente de seguridad dedicado vs. anotaciones).
2. **Qué código HTTP se devuelve** cuando el usuario no tiene permiso (403 vs. 404).

## Decisión

### 1. Componente dedicado: `ProjectAccessGuard`

La comprobación de permisos vive en **`shared.security.ProjectAccessGuard`**, un componente
Spring con métodos explícitos:

```java
void requireMember(Long projectId, Long userId);          // → 404 si no es miembro
void requireEditor(Long projectId, Long userId);          // → 404 si no es miembro, 403 si es VIEWER
void requireOwner(Long projectId, Long userId);           // → 404 si no es miembro, 403 si no es OWNER
void requireOwnerOrAdmin(Long projectId, Long userId);    // además acepta platformRole = ADMIN
```

**La fuente de verdad es la tabla `project_members`.** No hay listas de permisos en el token,
ni campos de permisos en la entidad `Project`, ni caché.

Los **roles de proyecto nunca viajan en el JWT**: cambian con frecuencia (una tarea tan
frecuente como añadir un miembro invalidaría los tokens emitidos) y no deben ser cacheables
en el cliente.

### 2. 404 para no-miembros, 403 para rol insuficiente

| Situación | Respuesta | Razón |
|---|---|---|
| El recurso **no existe** | `404 PROJECT_NOT_FOUND` / `TASK_NOT_FOUND` | Recurso ausente |
| El recurso **existe** pero el llamador **no es miembro** | `404` **con el mismo `code` y el mismo `detail`** | No se revela la existencia |
| El llamador **es miembro** pero su rol no basta | `403 FORBIDDEN` | Ya sabe que el recurso existe: es miembro |

El `detail` de ambos 404 es **idéntico**
(p. ej. `"No existe un proyecto con id 42"`), de modo que un atacante no puede
distinguir "el proyecto no existe" de "no tienes permiso" probando IDs.

### 3. Matriz de permisos

| Acción | VIEWER | EDITOR | OWNER | ADMIN |
|---|:---:|:---:|:---:|:---:|
| Leer proyecto / miembros / tareas | ✅ | ✅ | ✅ | ✅ |
| Crear tarea | ❌ | ✅ | ✅ | ✅ |
| Editar tarea / cambiar estado / asignar | ❌ | ✅ | ✅ | ✅ |
| Borrar tarea | ❌ | ⚠️ solo si la creó | ✅ | ✅ |
| Editar / archivar proyecto | ❌ | ❌ | ✅ | ✅ |
| Añadir / quitar / cambiar rol de miembro | ❌ | ❌ | ✅ | ✅ |
| Transferir ownership | ❌ | ❌ | ✅ | ✅ |

`ADMIN` es un rol **de plataforma** (`users.platform_role`), para soporte administrativo del
sistema. Puede operar sobre cualquier proyecto, pero **no aparece en los listados normales**
del usuario (`GET /projects` sigue devolviendo solo los proyectos donde es miembro).

## Justificación

### Por qué un componente dedicado y no `@PreAuthorize`

`@PreAuthorize("hasRole('OWNER')")` evalúa un rol **global**, no un rol *en un recurso
concreto*. Para expresar "el llamador es OWNER **del proyecto 42**" harían falta dos cosas:

1. Un bean de expresiones SpEL (`@projectAuth.canManage(#projectId)`), que es la
   @PreAuthorize con SPEL — es decir, el mismo componente, pero **disfrazado de anotación**.
2. Que el método del controller reciba el `projectId` como argumento, lo que ata la
   autorización a la forma concreta de cada método.

El componente explícito:

- **Es legible**: `projectAccessGuard.requireOwner(projectId, currentUser.userId())` dice
  exactamente qué se está comprobando.
- **Es testeable sin Spring**: un `@Mock ProjectMemberRepository` y una aserción.
- **Centraliza la política 404/403** en un único sitio. Con anotaciones, esa política se
  dispersa entre métodos y es fácil que uno devuelva 403 donde debía devolver 404.
- **No depende de la semántica de SpEL** ni de cambios en la API de Spring Security entre
  versiones (que ya ha cambiado dos veces en este punto: `AccessDeniedException` →
  `AuthorizationDeniedException`).

`@PreAuthorize` **sí** se usa, pero donde encaja: `@PreAuthorize("hasRole('ADMIN')")` en
los endpoints de administración de usuarios.

### Por qué 404 en lugar de 403 para los no-miembros

Un `403` en respuesta a `GET /projects/42` revela que "el proyecto 42 existe". Con un sistema
de IDs secuenciales (`BIGSERIAL`), un atacante puede enumerar el espacio de IDs y descubrir
la cantidad y el ritmo de creación de proyectos de la plataforma. Es una fuga de metadatos
real, aunque modesta.

El coste del 404 es unaSemantic ambigüedad para el cliente: "no existe" vs. "no tienes
acceso". Es aceptable porque **el cliente legítimo solo ve el 404 cuando efectivamente no
tiene acceso** (en ese caso no puede hacer nada con la respuesta de todas formas).

### Por qué el `ADMIN` de plataforma no ve todo en los listados

Si `GET /projects` devolviera todos los proyectos para un ADMIN, cualquier uso indebido de
un token de soporte filtraría la estructura de la organización de todos los clientes. La
decisión es: el ADMIN tiene **permisos**para operar sobre cualquier recurso (que es lo que
necesita para soporte), pero el **acceso a los datos** sigue requiriendo membresía. El
`404` para no-miembros se mantiene también para el ADMIN: opera donde tiene membresía o donde
necesita una acción de soporte explícita y auditada.

## Alternativas descartadas

| Alternativa | Por qué no |
|---|---|
| `@PreAuthorize` con roles de proyecto | Roles de proyecto son contextuales, no globales. No encaja en el modelo de Spring Security por defecto |
| Guardar el rol de proyecto en el JWT | Se queda obsoleto al cambiar la membresía; obliga a re-emitir tokens; complica la revocación |
| Cachear los permisos en sesión | Introduce estado en servidor y una caché que invalidar. La consulta a `project_members` por índice es barata |
| `403` para no-miembros | Fuga la existencia del recurso y permite enumeración de IDs |
| `404` también para rol insuficiente | Imposible: el usuario ya sabe que el recurso existe porque es miembro. Ocultaría el error real y dificultaría el diagnóstico. Rompe las expectativas HTTP |
| Row-level security de PostgreSQL (RLS) | Es la alternativa más robusta para multi-tenancy, pero requiere un esquema de sesión/contexto de usuario que Spring Boot no gestiona de serie, y es una mayor fuente de complexity. Se reevalúa en `F3-ARCH` |
| Permitir que el `OWNER` se degrade a sí mismo | Rompe la invariante "exactamente un OWNER por proyecto". Se exige transferencia explícita |

## Consecuencias

### Positivas

- Un solo punto de control de la política de permisos, verificable por tests.
- La matriz de permisos es un artefacto explícito y testeable, no una inferencia.
- Superficie de enumeración de recursos reducida.

### Negativas

- **Una consulta extra a la base de datos por petición protegida.** Aceptable: es un índice
  sobre `(project_id, user_id)` y el conjunto de proyectos de un usuario es pequeño.
- El `404` puede confundir al usuario final ("¿no existe o no me dejan?"). Mitigación:
  documentar el comportamiento en el README y en la descripción de OpenAPI.
- La comprobación de "el asignado es miembro del proyecto" requiere leer `project_members`
  desde `task`, lo que crea la dependencia `task → project` (regla R-D4 de
  `architecture.md` §3.3).

## Fecha de revisión

Al cerrar el Sprint 3 (tareas T-14 a T-16), con la matriz de permisos ya cubierta por tests.
Reevaluar RLS de PostgreSQL en la fase `F3-ARCH`.

## Resultado de la implementacion

- **Aceptado**: `ProjectAccessGuard` explicito, fuente de verdad en `project_members`.
- El 404 para no-miembros usa **la misma plantilla de mensaje** que el 404 de recurso
  inexistente: `"No existe un proyecto con id " + id`. El unico identificador que
  aparece es el que pidio el cliente, de modo que la respuesta no revela la existencia
  del recurso. Hay un test que lo verifica comparando ambos cuerpos.
- La matriz de permisos esta cubierta por tests **parametrizados** (rol x operacion), de
  modo que un cambio en la politica rompe la compilacion de las pruebas.
- **Ajuste detectado durante la implementacion**: el borrado de una tarea usa
  `requireEditor` y no `requireMember`. Con `requireMember`, un VIEWER que hubiera creado
  una tarea (imposible hoy, porque solo un EDITOR puede crearla) podria borrarla. Es una
  defensa en profundidad, no un fallo explotable.
