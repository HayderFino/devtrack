# ADR-0006 · Esquema gobernado por Flyway, con borrado lógico en proyectos

- **Estado:** **Aceptado** (2026-10-21)
- **Fecha:** 2026-10-21
- **Decide sobre:** gestión del esquema de base de datos y política de borrado
- **Relacionado:** `docs/architecture.md` §4.2, §4.3

## Contexto

Spring Data JPA con Hibernate permite "generar" el esquema desde las entidades
(`spring.jpa.hibernate.ddl-auto=create-drop` / `update`). Es cómodo en una demo, pero en un
proyecto de portafolio tiene tres problemas:

1. **No hay historial.** No se sabe qué cambió, cuándo, ni por qué. No hay forma de
   revertir.
2. **No es reproducible.** El esquema depende de la versión del código que se ejecutó
   primero.
3. **Es peligroso.** `ddl-auto=update` altera la base de datos de producción de forma
   silenciosa e irreversible.

Además, hay que decidir la **política de borrado** para proyectos y tareas.

## Decisión

### 1. Flyway es la única fuente del esquema

- `spring-boot-starter-flyway` + `flyway-database-postgresql` (versión gestionada por el
  BOM de Spring Boot 4.1.1: Flyway 12.4.0).
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate **solo valida** que el esquema
  coincide con las entidades; **nunca** lo modifica.
- Las migraciones viven en `src/main/resources/db/migration/V<n>__<descripcion>.sql`.
- **Regla dura:** una migración **nunca** se edita una vez aplicada. Un cambio se hace con
  una migración nueva.

**Consecuencia deliberada:** si una entidad y el esquema divergen, la aplicación **no
arranca**. Eso convierte la deriva de esquema (riesgo R-11) en un fallo ruidoso y temprano,
en vez de un bug silencioso en producción.

### 2. Numeración de migraciones

| Migración | Contenido |
|---|---|
| `V1__baseline.sql` | Extensiones y preparación (sin tablas de negocio) |
| `V2__users.sql` | `users` |
| `V3__projects_and_members.sql` | `projects`, `project_members` |
| `V4__tasks.sql` | `tasks` |

Una migración por tabla (o por grupo íntegro). Cada una es **atómica**: o se aplica entera o
no se aplica.

### 3. Tipos de columna: `VARCHAR` + `CHECK`, no `ENUM` nativo

Los enums de dominio (`TaskStatus`, `TaskPriority`, `ProjectRole`, `PlatformRole`) se
almacenan como `VARCHAR` con una restricción `CHECK` que lista los valores permitidos.

**Por qué no el tipo `ENUM` de PostgreSQL:**
- Añadir un valor exige `ALTER TYPE ... ADD VALUE`, que en versiones antiguas de PostgreSQL
  no se podía hacer dentro de una transacción y no podía ejecutarse en una migración
  transaccional de Flyway.
- El `CHECK` es un estándar SQL legible y portable, y `ddl-auto=validate` lo puede verificar.
- El coste es despreciable: el `CHECK` se evalúa en escritura, no en lectura.

### 4. Claves foráneas: `CASCADE` explícito donde tiene sentido

| Relación | Comportamiento | Razón |
|---|---|---|
| `project_members.project_id` → `projects.id` | `ON DELETE CASCADE` | Un miembro sin proyecto no tiene sentido |
| `project_members.user_id` → `users.id` | `ON DELETE CASCADE` | Un miembro de un usuario borrado no debe quedar |
| `tasks.project_id` → `projects.id` | `ON DELETE CASCADE` | Las tareas son parte del proyecto |
| `tasks.assignee_id` → `users.id` | `ON DELETE SET NULL` | Borrar un usuario **no** debe borrar las tareas que tenía asignadas; la tarea queda sin asignar |
| `tasks.created_by` → `users.id` | **Sin cascada** (RESTRICT) | El `created_by` es evidencia de auditoría. Impide borrar un usuario con historial. Si el borrado de usuario se hace "soft" (deshabilitar la cuenta), este FK nunca bloquea |

### 5. Borrado lógico en proyectos

`DELETE /api/v1/projects/{id}` **no borra**: marca `projects.archived = TRUE` y devuelve 204.

**Por qué:**
- Un proyecto archivado sigue teniendo **tareas con historial** y **miembros**. Borrarlo en
  cascada destruiría datos que ya se han usado para trabajar.
- Permite "desarchivar" en el futuro sin pérdida de datos.
- Es el comportamiento habitual en herramientas de gestión de proyectos.

**Consecuencia:** los proyectos archivados **no aparecen** en `GET /projects` por defecto
(`?archived=true` los muestra, `?archived=all` muestra ambos). Un proyecto archivado es de
**solo lectura**: no se pueden crear ni modificar tareas en él (se documenta como regla en
`F2-FEAT` si se quiere formalizar, o se permite editar y se decide entonces).

**Las tareas sí se borran en físico** (`DELETE /projects/{id}/tasks/{taskId}`), porque una
tarea no tiene historial associated en la v1 (el historial de actividad es de fase 2).

### 6. Locking optimista en `tasks`

`tasks.version BIGINT NOT NULL DEFAULT 0` con `@Version` en la entidad.

**Por qué en `tasks` y no en todas las tablas:** la tarea es el recurso que varias personas
editan a la vez (estado, asignado, título). `projects` y `project_members` se modifican
muy pocas veces y por una sola persona a la vez. Añadir `@Version` en todas las tablas
sería ruido.

Un conflicto produce `OptimisticLockingFailureException` → **409 `OPTIMISTIC_LOCK_CONFLICT`**
(ver ADR-0005).

## Alternativas descartadas

| Alternativa | Por qué no |
|---|---|
| `ddl-auto=update` | Sin historial, no reproducible, y modifica producción en silencio |
| `ddl-auto=create-drop` | Borra los datos en cada arranque |
| Liquibase | Alternativa válida y con XML/YAML versionado, pero añade un formato más y Flyway es más simple para un proyecto SQL de este tamaño |
| Tipo `ENUM` nativo de PostgreSQL | Rigidez para añadir valores; problemas con migraciones transaccionales |
| `ON DELETE CASCADE` en `tasks.created_by` | Destruye el historial de auditoría |
| `ON DELETE CASCADE` en `tasks.assignee_id` | Borrar un usuario borraría sus tareas asignadas. `SET NULL` es correcto |
| Borrado físico de proyectos | Destruye tareas y members con historial |
| `@Version` en todas las entidades | Ruido; el problema de concurrencia solo es real en `tasks` |
| Locking pesimista (`SELECT ... FOR UPDATE`) | Correcto, pero peor para la concurrencia y más complejo de verificar. El locking optimista falla rápido y no bloquea |

## Consecuencias

### Positivas

- El esquema es **reproducible** (misma versión de código → mismo esquema) y **auditable**
  (`flyway_schema_history`).
- La deriva entre entidades y esquema se detecta **al arrancar**, no en producción.
- El borrado lógico preserva el historial.

### Negativas

- Hay que escribir las migraciones a mano. Es trabajo real, y es el precio de la
  reproducibilidad.
- `ddl-auto=validate` hace que **cualquier** cambio en una entidad **requiera** una
  migración en el mismo commit. Es una restricción, no un defecto: es exactamente lo que se
  quiere.
- Las migraciones de Flyway son irreversibles sin una migración compensatoria. Para la v1
  es aceptable: las tablas son nuevas y el proyecto es pequeño.

## Fecha de revisión

Al iniciar la fase `F2-FEAT` (comentarios e historial de actividad): la tabla de actividad
heredará parte de estas decisiones (FK `created_by` sin cascada, `version` si aplica) y este
ADR debe actualizarse.

## Resultado de la implementacion

- **Aceptado**: Flyway gobierna el esquema; `ddl-auto=validate`; borrado logico de
  proyectos.
- El esquema quedo en **tres migraciones**, no cuatro: `V1__users.sql`,
  `V2__projects_and_members.sql` y `V3__tasks.sql`. Una migracion por grupo de tablas
  en lugar de una por tabla: resultaba mas legible y no dejaba una migracion vacia.
- **Ajuste**: `project_members` usa `created_at` / `updated_at` (y no `added_at`) para
  que todas las tablas extiendan `BaseEntity` sin excepciones. El DTO sigue exponiendo
  `addedAt`, que es el termino de dominio ("momento en que se unio al proyecto").
- El borrado logico esta implementado: `DELETE /api/v1/projects/{id}` marca
  `archived = true`, devuelve 204 y el proyecto desaparece del listado activo
  (`?archived=true` lo muestra).
- El locking optimista funciona: `tasks.version` con `@Version`; el servicio fuerza el
  `flush()` para que la respuesta refleje la version persistida, y un conflicto se
  traduce en **409 `OPTIMISTIC_LOCK_CONFLICT`**.
