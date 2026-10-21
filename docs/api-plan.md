# DevTrack — Plan de API REST (propuesta inicial)

> **Estado:** Implementado. Refleja el contrato real; las divergencias respecto a la
> propuesta inicial están recogidas en §12.
> Base: `/api/v1`. Formato: `application/json;charset=UTF-8`. OpenAPI 3.1 (springdoc).
> Referencia: `docs/architecture.md` §3 (paquetes), §4 (modelo), §5 (seguridad).

---

## 1. Convenciones generales

| Aspecto | Regla |
|---|---|
| Prefijo | Todas las rutas de negocio bajo `/api/v1` |
| Versionado | En la URL (`/api/v1`), no por cabecera. Cambiar la URL es un cambio incompatible → carpeta `v2` en el futuro |
| Nombres de recurso | Plurales, kebab-case en la URL (`/project-members` no aplica: se anida) |
| Identificadores | `Long` (BIGSERIAL)-exposed como número en el JSON |
| Timestamps | ISO-8601 UTC con milisegundos: `2026-10-21T14:30:00.000Z` |
| Fechas de vencimiento | `LocalDate` → `"2026-12-31"` (sin hora ni zona) |
| Nulos | `spring.jackson.default-property-inclusion=non_null` → los campos nulos **se omiten** del JSON |
| Paginación | `?page=0&size=20` (base 0), `size` por defecto 20, máximo 100 |
| Ordenación | Orden estable por defecto (id asc) para que la paginación sea determinista |
| Idempotencia | `POST` de creación no es idempotente; los endpoints de asignación (`PUT`) sí lo son |
| Filtros | Parámetros de query opcionales, no filtros en el body |

### 1.1 Encabezados

**Petición autenticada:**

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOjE...
```

**Petición de idempotencia (fase posterior, opcional):**
`Idempotency-Key: <uuid>` — no implementado en la v1.

### 1.2 Formato de error (RFC 7807 `ProblemDetail`)

Todos los errores usan `application/problem+json`.

```json
{
  "type": "https://devtrack.dev/problems/resource-not-found",
  "title": "Recurso no encontrado",
  "status": 404,
  "detail": "El proyecto no existe o no tienes acceso",
  "instance": "/api/v1/projects/42",
  "code": "PROJECT_NOT_FOUND",
  "timestamp": "2026-10-21T14:30:00.000Z",
  "errors": [
    { "field": "title", "message": "no debe estar en blanco", "rejectedValue": "" }
  ]
}
```

| Campo | Presente | Notas |
|---|---|---|
| `type` | Sí | URI de la documentación del problema |
| `title` | Sí | Resumen corto, estable |
| `status` | Sí | Código HTTP |
| `detail` | Sí | Explicación concreta. **No debe filtrar datos de otros usuarios** |
| `instance` | Sí | Ruta que falló |
| `code` | Sí | Código de error de negocio, estable y testeable |
| `timestamp` | Sí | ISO-8601 UTC |
| `errors` | Solo en 400 | Detalle de validación por campo |

> `code` es un identificador estable para que el cliente pueda ramificar sin parsear texto.
> Es el campo que los tests deben comprobar.

### 1.3 Formato de paginación

```json
{
  "content": [ /* ... */ ],
  "page": 0,
  "size": 20,
  "totalElements": 57,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

> Se usa un `PageResponse<T>` propio, **no** `org.springframework.data.domain.Page`, cuya
> estructura JSON es inestable entre versiones y produce avisos de serialización en Spring Boot.

---

## 2. Catálogo de códigos de error

| `code` | HTTP | Cuándo |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Falla la validación de Bean Validation |
| `MALFORMED_JSON` | 400 | JSON ilegible |
| `INVALID_CREDENTIALS` | 401 | Email o contraseña incorrectos |
| `UNAUTHENTICATED` | 401 | Token ausente, inválido o expirado |
| `ACCOUNT_DISABLED` | 403 | `users.enabled = false` |
| `FORBIDDEN` | 403 | Miembro con rol insuficiente |
| `PROJECT_NOT_FOUND` | 404 | Proyecto inexistente **o** no visible para el llamador |
| `TASK_NOT_FOUND` | 404 | Tarea inexistente **o** no visible para el llamador |
| `USER_NOT_FOUND` | 404 | Usuario inexistente |
| `PROJECT_MEMBER_NOT_FOUND` | 404 | El usuario no es miembro del proyecto |
| `EMAIL_ALREADY_REGISTERED` | 409 | Email duplicado |
| `MEMBER_ALREADY_EXISTS` | 409 | El usuario ya es miembro del proyecto |
| `OWNER_CANNOT_BE_REMOVED` | 409 | Intento de quitar o degradar al owner |
| `OPTIMISTIC_LOCK_CONFLICT` | 409 | Conflicto de versión (`@Version`) |
| `INVALID_STATUS_TRANSITION` | 422 | Transición de estado no permitida |
| `ASSIGNEE_NOT_A_MEMBER` | 422 | El asignado no pertenece al proyecto |
| `TASK_DELETION_NOT_ALLOWED` | 422 | Un EDITOR intenta borrar una tarea que no creó |
| `INTERNAL_ERROR` | 500 | Fallo no previsto (mensaje genérico, detalle en logs) |

> El `detail` de un 404 **no distingue** entre "no existe" y "no tienes permiso": ambos
> devuelven **exactamente el mismo texto** ("El proyecto no existe o no tienes acceso").
> Esto es deliberado (R-01) y hay un test que compara ambos cuerpos.

---

## 3. Autenticación y usuarios

### 3.1 `POST /api/v1/auth/register` — Registrar usuario

**Público.** Crea la cuenta y devuelve el perfil (sin token: el cliente hace login después).
**No** lleva cabecera `Location`: el recurso creado se consulta en `/api/v1/users/me`.

**Request**

```json
{
  "email": "ada@devtrack.dev",
  "password": "Str0ng-Passphrase!",
  "fullName": "Ada Lovelace"
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `email` | string | `@NotBlank`, `@Email`, `@Size(max=320)` |
| `password` | string | `@NotBlank`, `@Size(min=10, max=72)` — 72 = límite de BCrypt en bytes |
| `fullName` | string | `@NotBlank`, `@Size(max=120)` |

**201 Created**

```json
{
  "id": 1,
  "email": "ada@devtrack.dev",
  "fullName": "Ada Lovelace",
  "platformRole": "USER",
  "createdAt": "2026-10-21T14:00:00.000Z"
}
```

**Errores:** `400 VALIDATION_ERROR`, `409 EMAIL_ALREADY_REGISTERED`.

> Nota: `password` no tiene `max` en caracteres sino en bytes effectively; 72 es el máximo
> de entrada de BCrypt. No se recorta silenciosamente: se rechaza.

### 3.2 `POST /api/v1/auth/login` — Iniciar sesión

**Público.**

**Request**

```json
{ "email": "ada@devtrack.dev", "password": "Str0ng-Passphrase!" }
```

**200 OK**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 1,
    "email": "ada@devtrack.dev",
    "fullName": "Ada Lovelace",
    "platformRole": "USER",
    "createdAt": "2026-10-21T14:00:00.000Z"
  }
}
```

**Errores:** `400 VALIDATION_ERROR`, `401 INVALID_CREDENTIALS`, `403 ACCOUNT_DISABLED`.

> El mensaje de error es **idéntico** para email inexistente y contraseña incorrecta:
> evita enumerar cuentas registradas.

### 3.3 `GET /api/v1/users/me` — Perfil propio

**Autenticado.**

**200 OK** → mismo cuerpo que `user` en el login.

**Errores:** `401 UNAUTHENTICATED`.

### 3.4 `GET /api/v1/users` — Listar usuarios *(fase posterior)*

**Solo `ADMIN`.** `?query=&page=&size=`. Se deja fuera de la v1; se documenta para reservar
la ruta.

---

## 4. Proyectos

### 4.1 `POST /api/v1/projects` — Crear proyecto

**Autenticado.** El creator se convierte en miembro con rol `OWNER` **en la misma transacción**.

**Request**

```json
{ "name": "DevTrack v1", "description": "API de gestión de proyectos" }
```

| Campo | Tipo | Validación |
|---|---|---|
| `name` | string | `@NotBlank`, `@Size(max=120)` |
| `description` | string | `@Size(max=5000)`, opcional |

**201 Created**

```json
{
  "id": 10,
  "name": "DevTrack v1",
  "description": "API de gestión de proyectos",
  "ownerId": 1,
  "archived": false,
  "role": "OWNER",
  "memberCount": 1,
  "createdAt": "2026-10-21T14:10:00.000Z",
  "updatedAt": "2026-10-21T14:10:00.000Z"
}
```

> `role` es el rol **del llamador** en ese proyecto. Permite al cliente pintar la UI sin
> segunda llamada. `memberCount` se calcula con una única consulta agregada para todos los
> proyectos de la página (sin N+1).
>
> **No incluye `taskCount`**: calcularlo exigiría que el contexto `project` conociera al
> contexto `task`, rompiendo la dependencia unidireccional entre contextos (ADR-0001,
> regla R-D4). El número de tareas se expone desde el contexto `task`.

**Errores:** `400`, `401`.

### 4.2 `GET /api/v1/projects` — Listar mis proyectos

**Autenticado.** Devuelve **solo** los proyectos donde el llamador es miembro.

**Query**

| Parámetro | Tipo | Default | Notas |
|---|---|---|---|
| `archived` | boolean | `false` | `true` → solo archivados; `all` → ambos |
| `page` | int | `0` | Base 0 |
| `size` | int | `20` | Máximo 100 |
| `sort` | string | `id,asc` | `createdAt,desc` \| `name,asc` \| `name,desc` |

**200 OK** → `PageResponse<ProjectResponse>`.

**Errores:** `400` (parámetro inválido), `401`.

### 4.3 `GET /api/v1/projects/{projectId}` — Detalle del proyecto

**Autenticado + miembro del proyecto.**

**200 OK** → `ProjectResponse`.

**Errores:** `401`, `404 PROJECT_NOT_FOUND` (inexistente **o** no miembro).

### 4.4 `PATCH /api/v1/projects/{projectId}` — Actualizar proyecto

**Autenticado + rol `OWNER` (o `ADMIN`).**

**Request** — al menos un campo. Campos ausentes = no modificar.

```json
{ "name": "DevTrack v1 (renombrado)", "description": "Nueva descripción" }
```

| Campo | Tipo | Validación |
|---|---|---|
| `name` | string | `@Size(max=120)`, opcional |
| `description` | string | `@Size(max=5000)`, opcional |

**200 OK** → `ProjectResponse` actualizado.

**Errores:** `400`, `401`, `403 FORBIDDEN`, `404`.

### 4.5 `DELETE /api/v1/projects/{projectId}` — Archivar proyecto

**Autenticado + rol `OWNER` (o `ADMIN`).** Borrado **lógico**: marca `archived = true`.
No se borran en cascada las tareas ni los miembros; el histórico se conserva.

**204 No Content.**

**Errores:** `401`, `403`, `404`.

> Decisión: `DELETE` archiva en lugar de eliminar. La eliminación física quedaría para un
> endpoint de purga en `F3-ARCH`. Documentado en ADR-0006.

---

## 5. Miembros del proyecto

> Nota de diseño: el recurso se identifica por **`userId`**, no por el id de la fila de
> `project_members`. Evita exponer un id interno que no significa nada para el cliente.

### 5.1 `GET /api/v1/projects/{projectId}/members` — Listar miembros

**Autenticado + miembro.** Orden: `OWNER` primero, luego resto por `addedAt`.

**200 OK** → `List<ProjectMemberResponse>` (sin paginación: el equipo se sirve completo)

```json
[
  {
    "userId": 1,
    "email": "ada@devtrack.dev",
    "fullName": "Ada Lovelace",
    "role": "OWNER",
    "addedAt": "2026-10-21T14:10:00.000Z"
  },
  {
    "userId": 2,
    "email": "linus@devtrack.dev",
    "fullName": "Linus Torvalds",
    "role": "EDITOR",
    "addedAt": "2026-10-21T14:20:00.000Z"
  }
]
```

**Errores:** `401`, `404`.

### 5.2 `POST /api/v1/projects/{projectId}/members` — Añadir miembro

**Autenticado + rol `OWNER` (o `ADMIN`).**

**Request**

```json
{ "userId": 2, "role": "EDITOR" }
```

| Campo | Tipo | Validación |
|---|---|---|
| `userId` | long | `@NotNull`, `@Positive` — el usuario debe existir |
| `role` | enum | `@NotNull`, `EDITOR` o `VIEWER` (no se permite crear `OWNER` por esta ruta) |

**201 Created** → `ProjectMemberResponse`.

**Errores:** `400`, `401`, `403`, `404 USER_NOT_FOUND`, `409 MEMBER_ALREADY_EXISTS`.

> No se puede añadir un `OWNER` por esta ruta: la propiedad se concede al crear el proyecto
> o por transferencia explícita (§5.4). Así se mantiene la invariante "exactamente un OWNER".

### 5.3 `PATCH /api/v1/projects/{projectId}/members/{userId}` — Cambiar rol

**Autenticado + rol `OWNER` (o `ADMIN`).**

**Request**

```json
{ "role": "VIEWER" }
```

**200 OK** → `ProjectMemberResponse`.

**Errores:** `400`, `401`, `403`, `404 PROJECT_MEMBER_NOT_FOUND`,
`409 OWNER_CANNOT_BE_REMOVED` (se intenta degradar al owner).

### 5.4 `PUT /api/v1/projects/{projectId}/members/{userId}/ownership` — Transferir propiedad

**Autenticado + rol `OWNER` (o `ADMIN`).** Transfiere el rol `OWNER` al miembro indicado
dentro de la **misma transacción**: el owner actual pasa a `EDITOR` y el receptor pasa a `OWNER`.
De este modo nunca hay cero ni dos owners.

**Request**

```json
{ "newOwnerId": 2 }
```

**200 OK** → `PageResponse` no; devuelve `ProjectResponse` (el proyecto con el nuevo owner).

**Errores:** `400`, `401`, `403`, `404 PROJECT_MEMBER_NOT_FOUND`,
`409 OWNER_CANNOT_BE_REMOVED` (el receptor ya es owner).

> Sujeto a la respuesta a **Q-04**. Si se decide no incluir la transferencia, esta ruta
> desaparece y el owner es inmutable hasta el borrado del proyecto.

### 5.5 `DELETE /api/v1/projects/{projectId}/members/{userId}` — Quitar miembro

**Autenticado + rol `OWNER` (o `ADMIN`).**

**204 No Content.**

**Errores:** `401`, `403`, `404`, `409 OWNER_CANNOT_BE_REMOVED`.

---

## 6. Tareas

> Las tareas se anidan bajo el proyecto porque **el proyecto es la frontera de autorización**
> (A-09). Conocer un `taskId` sin ser miembro del proyecto no da acceso.

### 6.1 `GET /api/v1/projects/{projectId}/tasks` — Listar tareas

**Autenticado + miembro.**

**Query**

| Parámetro | Tipo | Default | Notas |
|---|---|---|---|
| `status` | enum | — | Repetible: `?status=TODO&status=IN_PROGRESS` |
| `priority` | enum | — | Repetible |
| `assigneeId` | long | — | |
| `unassigned` | boolean | — | `true` → solo sin asignar |
| `dueBefore` | date | — | `YYYY-MM-DD` |
| `overdue` | boolean | — | `true` → `due_date < hoy` y `status != DONE` |
| `q` | string | — | Búsqueda en título/descripción (LIKE) |
| `page` | int | `0` | |
| `size` | int | `20` | Máximo 100 |
| `sort` | string | `createdAt,desc` | `dueDate,asc` \| `priority,desc` \| `title,asc` |

**200 OK** → `PageResponse<TaskResponse>`.

**Errores:** `400`, `401`, `404`.

### 6.2 `POST /api/v1/projects/{projectId}/tasks` — Crear tarea

**Autenticado + rol `EDITOR`/`OWNER`/`ADMIN`.**

**Request**

```json
{
  "title": "Implementar el guard de autorización",
  "description": "Cubrir la matriz de permisos con tests",
  "status": "TODO",
  "priority": "HIGH",
  "assigneeId": 2,
  "dueDate": "2026-12-31"
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `title` | string | `@NotBlank`, `@Size(max=200)` |
| `description` | string | `@Size(max=10000)`, opcional |
| `status` | enum | `TODO` \| `IN_PROGRESS` \| `DONE` \| `CANCELLED`. Default `TODO`. Si se envía `DONE` en la creación, se acepta (no hay transición) |
| `priority` | enum | `LOW` \| `MEDIUM` \| `HIGH` \| `URGENT`. Default `MEDIUM` |
| `assigneeId` | long | Opcional. Si se envía, el usuario **debe ser miembro del proyecto** |
| `dueDate` | date | Opcional. `YYYY-MM-DD` |

**201 Created**

```json
{
  "id": 500,
  "projectId": 10,
  "title": "Implementar el guard de autorización",
  "description": "Cubrir la matriz de permisos con tests",
  "status": "TODO",
  "priority": "HIGH",
  "assigneeId": 2,
  "assigneeName": "Linus Torvalds",
  "dueDate": "2026-12-31",
  "createdBy": 1,
  "createdByName": "Ada Lovelace",
  "overdue": false,
  "version": 0,
  "createdAt": "2026-10-21T14:30:00.000Z",
  "updatedAt": "2026-10-21T14:30:00.000Z"
}
```

**Errores:** `400`, `401`, `403`, `404 PROJECT_NOT_FOUND`, `422 ASSIGNEE_NOT_A_MEMBER`.

> `assigneeName` / `createdByName` se resuelven en la query de lectura para evitar N+1.
> Si el usuario se borra (`ON DELETE SET NULL`), `assigneeId` queda `null` y el nombre se omite.

### 6.3 `GET /api/v1/projects/{projectId}/tasks/{taskId}` — Detalle de la tarea

**Autenticado + miembro.**

**200 OK** → `TaskResponse`.

**Errores:** `401`, `404`.

> Dos 404 posibles, con mensajes distintos:
> - `PROJECT_NOT_FOUND` — el llamador **no es miembro** del proyecto (o el proyecto no existe).
> - `TASK_NOT_FOUND` — el llamador **es miembro**, pero no existe una tarea con ese id en ese
>   proyecto (tarea de otro proyecto, o inexistente).

### 6.4 `PATCH /api/v1/projects/{projectId}/tasks/{taskId}` — Actualizar tarea

**Autenticado + rol `EDITOR`/`OWNER`/`ADMIN`.**
Campos actualizables: `title`, `description`, `status`, `priority`, `dueDate`.
**La asignación NO se modifica aquí** (tiene endpoint propio, §6.5), para no confundir
"campo ausente" con "campo a null".

**Request** (todos opcionales)

```json
{ "title": "Nuevo título", "status": "IN_PROGRESS" }
```

**200 OK** → `TaskResponse` con `version` incrementada.

**Errores:** `400`, `401`, `403`, `404`,
`422 INVALID_STATUS_TRANSITION` (transición no permitida, §4.4 de `architecture.md`),
`409 OPTIMISTIC_LOCK_CONFLICT`.

> El `version` incrementada es la señal para que el cliente detecte que su copia quedó obsoleta.
> El conflicto real (dos escrituras simultáneas) produce `409 OPTIMISTIC_LOCK_CONFLICT`.

### 6.5 `PUT /api/v1/projects/{projectId}/tasks/{taskId}/assignee` — Asignar / desasignar

**Autenticado + rol `EDITOR`/`OWNER`/`ADMIN`.** Endpoint **idempotente**.

**Request**

```json
{ "assigneeId": 2 }
```

**Request para desasignar**

```json
{ "assigneeId": null }
```

**200 OK** → `TaskResponse`.

**Errores:** `400`, `401`, `403`, `404`, `422 ASSIGNEE_NOT_A_MEMBER`.

> `PUT` + `assigneeId: null` desasigna. `assigneeId` **debe** ser miembro del proyecto
> (o `null`). No se permite asignar a un usuario que no pertenece al proyecto.

### 6.6 `DELETE /api/v1/projects/{projectId}/tasks/{taskId}` — Eliminar tarea

**Autenticado +** rol `OWNER`/`ADMIN`, **o** `EDITOR` que sea el `created_by` de la tarea.

**204 No Content.**

**Errores:** `401`, `403`, `404`, `422 TASK_DELETION_NOT_ALLOWED`.

> Un `VIEWER` recibe **403** (rol insuficiente). Un `EDITOR` que no creó la tarea recibe
> **422** (no tiene permiso para *esa* tarea).

### 6.7 `GET /api/v1/tasks` — Mis tareas (cross-proyecto) *(fase posterior)*

**Autenticado.** `?assignedToMe=true&status=&page=&size=`. Se deja fuera de la v1 pero la ruta
se reserva para la fase de métricas.


### 6.8 `GET /api/v1/projects/{projectId}/stats` — Métricas de progreso

**Autenticado + miembro del proyecto** (cualquier rol, incluido `VIEWER`).

**200 OK**

```json
{
  "projectId": 10,
  "totalTasks": 57,
  "byStatus": { "TODO": 20, "IN_PROGRESS": 5, "DONE": 30, "CANCELLED": 2 },
  "byPriority": { "LOW": 10, "MEDIUM": 25, "HIGH": 18, "URGENT": 4 },
  "unassigned": 7,
  "assignedToMe": 4,
  "overdue": 3,
  "dueWithinNext7Days": 6,
  "completionRate": 0.53
}
```

| Campo | Significado |
|---|---|
| `byStatus` / `byPriority` | Recuento por estado y por prioridad. **Incluyen todos los valores**, aunque valgan 0, para que el cliente pueda pintar la matriz completa sin tratar los nulos. |
| `unassigned` | Tareas sin assigned. |
| `assignedToMe` | Tareas asignadas al llamador. |
| `overdue` | Tareas con fecha límite pasada que **no** están `DONE`. |
| `dueWithinNext7Days` | Tareas que vencen hoy o en los próximos 7 días. |
| `completionRate` | Proporción de tareas `DONE` sobre el total, en el intervalo **[0,1]** con dos decimales. `0.53` significa 53 %. Con 0 tareas devuelve `0`. |

**Errores:** `401`, `404 PROJECT_NOT_FOUND`.

> El endpoint vive en el contexto `task` porque las métricas se calculan sobre tareas. El
> contexto `project` no conoce al `task` y la dependencia entre contextos es unidireccional
> (regla R-D4 de ADR-0001).

---

## 7. Resumen de la superficie de la v1

| # | Método | Ruta | Rol mínimo | Éxito |
|---|---|---|---|---|
| 1 | POST | `/api/v1/auth/register` | público | 201 |
| 2 | POST | `/api/v1/auth/login` | público | 200 |
| 3 | GET | `/api/v1/users/me` | autenticado | 200 |
| 4 | POST | `/api/v1/projects` | autenticado | 201 |
| 5 | GET | `/api/v1/projects` | autenticado | 200 |
| 6 | GET | `/api/v1/projects/{projectId}` | miembro (lectura) | 200 |
| 7 | PATCH | `/api/v1/projects/{projectId}` | `OWNER` | 200 |
| 8 | DELETE | `/api/v1/projects/{projectId}` | `OWNER` | 204 |
| 9 | GET | `/api/v1/projects/{projectId}/members` | miembro | 200 |
| 10 | POST | `/api/v1/projects/{projectId}/members` | `OWNER` | 201 |
| 11 | PATCH | `/api/v1/projects/{projectId}/members/{userId}` | `OWNER` | 200 |
| 12 | PUT | `/api/v1/projects/{projectId}/members/{userId}/ownership` | `OWNER` | 200 |
| 13 | DELETE | `/api/v1/projects/{projectId}/members/{userId}` | `OWNER` | 204 |
| 14 | GET | `/api/v1/projects/{projectId}/tasks` | miembro | 200 |
| 15 | POST | `/api/v1/projects/{projectId}/tasks` | `EDITOR` | 201 |
| 16 | GET | `/api/v1/projects/{projectId}/tasks/{taskId}` | miembro | 200 |
| 17 | PATCH | `/api/v1/projects/{projectId}/tasks/{taskId}` | `EDITOR` | 200 |
| 18 | PUT | `/api/v1/projects/{projectId}/tasks/{taskId}/assignee` | `EDITOR` | 200 |
| 19 | DELETE | `/api/v1/projects/{projectId}/tasks/{taskId}` | `OWNER` o `EDITOR` creador | 204 |
| 20 | GET | `/api/v1/projects/{projectId}/stats` | miembro (lectura) | 200 |

**Endpoints fuera de la v1 (reservados):** `GET /api/v1/users` (ADMIN),
`GET /api/v1/tasks` (cross-proyecto), `/api/v1/projects/{id}/comments`,
`/api/v1/projects/{id}/activity`.

---

## 8. Códigos HTTP: política

| Código | Uso en DevTrack |
|---|---|
| `200 OK` | Lectura o actualización correcta |
| `201 Created` | Recurso creado. `Location` con la URI del nuevo recurso |
| `204 No Content` | Borrado o archivado. Sin cuerpo |
| `400 Bad Request` | Error de validación o de formato. `errors[]` con el detalle por campo |
| `401 Unauthorized` | Token ausente, inválido, expirado o credenciales incorrectas |
| `403 Forbidden` | Autenticado pero sin rol suficiente. **O** cuenta deshabilitada |
| `404 Not Found` | El recurso no existe **o** el llamador no es miembro (no se distingue) |
| `409 Conflict` | Conflicto de estado: email duplicado, miembro duplicado, owner inmutable, conflicto de versión |
| `422 Unprocessable Entity` | Sintaxis válida, semántica inválida: transición de estado, assignee no miembro, borrado no permitido |
| `500 Internal Server Error` | Fallo no previsto. `detail` genérico; el stack trace va al log, no a la respuesta |

> La distinción `400` vs `422` se aplica de forma consistente: `400` = el cuerpo no tiene la
> forma o el tipo correctos; `422` = la forma es correcta pero la operación es imposible en
> el contexto actual.

---

## 9. Generar la documentación OpenAPI

- `springdoc-openapi-starter-webmvc-ui:3.1.1`
- UI: `/swagger-ui.html` · Definición: `/v3/api-docs` (JSON) y `/v3/api-docs.yaml`
- Salida por defecto: **OpenAPI 3.1.0**
- Se configura en `OpenApiConfig`:
  - `title`: "DevTrack API", `version`: "1.0.0"
  - `description` con el modelo de autorización (tabla de roles) — es la primera pregunta
    que hace quien lee la API.
  - Esquema de seguridad `bearerAuth` (JWT) aplicado **globalmente**, salvo en los endpoints
    públicos, que se marcan con `@SecurityRequirements` vacío.
- Anotaciones por endpoint: `@Operation(summary=...)`, `@ApiResponse` para cada código
  documentado en §8, `@Parameter` para los query params, `@Schema` para los campos con
  restricciones no evidentes.
- En producción: `springdoc.api-docs.enabled=false` y `springdoc.swagger-ui.enabled=false`.
  Desactivar solo el Swagger UI **no** desactiva `/v3/api-docs`: hay que desactivar ambos.

---

## 10. Ejemplos `curl`

```bash
# 1. Registro
curl -sX POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@devtrack.dev","password":"Str0ng-Passphrase!","fullName":"Ada Lovelace"}'

# 2. Login
TOKEN=$(curl -sX POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@devtrack.dev","password":"Str0ng-Passphrase!"}' | jq -r .accessToken)

# 3. Crear proyecto
curl -sX POST http://localhost:8080/api/v1/projects \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"DevTrack v1","description":"API de gestión de proyectos"}'

# 4. Crear tarea
curl -sX POST http://localhost:8080/api/v1/projects/1/tasks \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Primera tarea","priority":"HIGH","dueDate":"2026-12-31"}'

# 5. Listar tareas filtradas
curl -s "http://localhost:8080/api/v1/projects/1/tasks?status=TODO&sort=dueDate,asc" \
  -H "Authorization: Bearer $TOKEN"

# 6. Asignar (idempotente)
curl -sX PUT http://localhost:8080/api/v1/projects/1/tasks/1/assignee \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"assigneeId":2}'
```

---

## 11. Checklist de revisión de un endpoint (para el Agente 2)

Antes de dar por terminada una tarea de API, verificar:

- [ ] La ruta, el método y el código de éxito coinciden con este documento.
- [ ] La entrada se valida con Bean Validation y los errores de validación devuelven
      `400` con `errors[]` por campo.
- [ ] **La salida es un DTO**, nunca una entidad JPA.
- [ ] El endpoint llama a `ProjectAccessGuard` (si toca un proyecto) y el test incluye un
      caso de **usuario no miembro → 404** y **miembro con rol insuficiente → 403**.
- [ ] Un `401` sin token produce `ProblemDetail` con `code=UNAUTHENTICATED`.
- [ ] Los casos de conflicto devuelven `409` con su `code` correspondiente.
- [ ] Las mutaciones que dependen del estado (transiciones) devuelven `422` si no aplican.
- [ ] El endpoint está anotado para OpenAPI (`@Operation`, `@ApiResponse`).
- [ ] Existe un test de integración que ejercita el endpoint **con y sin** token.


---

## 12. Divergencias respecto a la propuesta inicial

Recogidas durante la implementación. La propuesta original de este documento era
`PROPUESTA`; lo que hay es lo **implementado y verificado con tests**.

| # | Propuesta original | Implementación real | Motivo |
|---|---|---|---|
| 1 | `ProjectResponse` con `taskCount` | Sin `taskCount` | El contexto `project` no puede conocer al `task` (regla R-D4 de ADR-0001). El contador se expone desde el contexto `task`. |
| 2 | `POST /auth/register` con cabecera `Location` | Sin `Location` | El recurso creado se lee en `/api/v1/users/me`, no en `/api/v1/users/{id}` (ruta que no existe en la v1). `Location` sí se emite en `POST /projects` y `POST /tasks`, donde sí hay ruta de lectura. |
| 3 | `PATCH /projects/{id}/tasks/{taskId}` con `422` en toda transición inválida | `422` solo si la transición es **inválida**; el mismo estado es un *no-op* `200` | Un no-op no es un error: reenviar el mismo estado no debe fallar. |
| 4 | `size` máximo 100 recortado en silencio | `size` fuera de 1..100 devuelve **400** | Un recorte silencioso hace que el cliente crea que su peticion se atendió como pidió. Verificado con `PageSizeInterceptor`. |
| 5 | Rol de proyecto en el DTO como enum propio (`ProjectRoleRequest`) | `ProjectRoleRequest` para miembros (excluye `OWNER`); enums de dominio directamente en los DTOs de tarea | En tareas no hay que excluir ningún valor, así que un enum espejo solo añadiría ruido de conversión. |
| 6 | `project_members.added_at` | `created_at` / `updated_at` | Uniformidad: todas las tablas extienden `BaseEntity`. El DTO sigue exponiendo `addedAt`. |
| 7 | Cuatro migraciones | Tres (`V1` usuarios, `V2` proyectos y miembros, `V3` tareas) | Una migración por grupo de tablas; sin migraciones de relleno. |
| 8 | `GET /projects/{id}/tasks/{taskId}` → siempre `TASK_NOT_FOUND` | `PROJECT_NOT_FOUND` si no se es miembro; `TASK_NOT_FOUND` si se es miembro y la tarea no está en ese proyecto | El proyecto es la frontera de autorización: si no eres miembro, no se revela ni el proyecto ni sus tareas. |
| 9 | `GET /projects/{id}/members` con envoltura de paginación | Devuelve un **array plano** (`List<ProjectMemberResponse>`) | El equipo de un proyecto se sirve completo: paginarlo no aporta nada y complica el cliente. Corregido tras revisar el código y los tests. |
| 10 | Métricas de progreso en fase posterior | `GET /projects/{id}/stats` implementado en la v1 | Es la consulta que más aporta para el seguimiento del trabajo y no requiere estado adicional: se calcula con agregaciones sobre `tasks`. |
