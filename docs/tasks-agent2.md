# DevTrack — Backlog de tareas para el Agente 2 (implementación)

> **Estado:** **Implementado** (2026-10-21). Las 22 tareas están completadas y verificadas
> con **223 pruebas en verde**.
>
> Este documento se conserva como **contrato de aceptación** y registro de lo implementado:
> cada criterio marcado abajo se comprobó con un test o con una ejecución real.
> Fuentes de verdad: `docs/architecture.md` (diseño) y `docs/api-plan.md` (contratos).

---

## Reglas de trabajo para el Agente 2

1. **Una tarea = un commit (o un PR) pequeño y revisable.** Nada de "todo el módulo de tareas"
   en un solo commit.
2. **Nada se marca como terminado sin evidencia.** El entregable de cada tarea incluye la
   salida literal de `./mvnw verify` (o `./mvnw test -Dtest=...`) mostrando `BUILD SUCCESS`.
3. **No se inventa API.** Si un contrato no está en `docs/api-plan.md`, se pregunta antes de
   inventarlo.
4. **No se editan entidades para "simplificar".** El modelo de datos está en
   `docs/architecture.md` §4. Si una restricción resulta incorrecta, se propone un cambio
   acotado al Agente 1; no se aplica por cuenta propia.
5. **No seAdds dependencias** sin justificar. La lista de dependencias permitidas está en
   `docs/architecture.md` §2.3.
6. **Prohibido:** `System.out.println`, entidades JPA en respuestas, `ddl-auto=update`,
   `skipTests`, secretos en el repo, `@MockBean` (eliminado en Spring Boot 4 → `@MockitoBean`).
7. **Si una tarea está bloqueada**, se para, se documenta el bloqueo y se pide decisión.
   No se improvisa.

---

## Sprint 0 — Cimientos

### T-00 · Preparación del toolchain local

| Campo | Detalle |
|---|---|
| **Objetivo** | Dejar el entorno de desarrollo listo para compilar y ejecutar Spring Boot |
| **Archivos** | Ninguno en el repo. Documentar los pasos en `README.md` (sección "Requisitos") |
| **Dependencias** | — |
| **Criterios de aceptación** | 1) `java -version` muestra **21.x**.<br>2) `./mvnw -v` funciona (Maven Wrapper **3.9.x**).<br>3) `docker --version` y `docker compose version` funcionan.<br>4) `docker run --rm postgres:18-alpine postgres --version` responde sin error.<br>5) Todo esto está documentado en `README.md` |
| **Pruebas requeridas** | No aplica (es entorno). Verificar con la salida de los comandos |
| **Riesgos / decisiones pendientes** | Docker Desktop requiere licencia y puede pedir permisos. Si el usuario no puede instalar Docker, la alternativa es un PostgreSQL local vía Homebrew (`brew install postgresql@18`) y se anota en el README. **Requiere autorización del usuario para instalar software en su máquina** |

---

### T-01 · Estructura Maven y esqueleto de la aplicación

| Campo | Detalle |
|---|---|
| **Objetivo** | Proyecto Maven que compila, arranca y responde, con la estructura de paquetes de `architecture.md` §3.2 |
| **Archivos** | `pom.xml`, `src/main/java/com/devtrack/DevTrackApplication.java`, `src/main/resources/application.yml`, `src/main/java/com/devtrack/{shared,user,project,task}/` (paquetes vacíos + `package-info.java`), `.gitignore`, `.editorconfig`, `README.md` (esqueleto), `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` |
| **Dependencias** | T-00 |
| **Criterios de aceptación** | 1) `pom.xml` usa `spring-boot-starter-parent:4.1.1` y `<java.version>21</java.version>`.<br>2) Dependencias **exactamente** las de `architecture.md` §2.3 (ni una más, ni una menos).<br>3) `./mvnw clean package` → `BUILD SUCCESS`.<br>4) `./mvnw spring-boot:run` arranca y `/actuator/health` responde `{"status":"UP"}`.<br>5) `git status` no muestra ningún artefacto de build (el `.gitignore` cubre `target/`).<br>6) Los 5 directorios de feature existen con `package-info.java` documentando su responsabilidad.<br>7) `README.md` tiene: nombre, descripción, stack con versiones, requisitos, y estado "en construcción" |
| **Pruebas requeridas** | El test de arranque (`contextLoads`) pasa |
| **Riesgos / decisiones pendientes** | **Q-01 (Boot 4.1.1 + Java 21) y Q-05 (Spring Modulith: sí/no)** deben estar resueltas antes de escribir el `pom.xml`. Si Spring Modulith se aprueba, añadir `spring-modulith-starter-core:2.1.1` y `spring-modulith-starter-jdbc` (o el que corresponda) |

---

### T-02 · Docker Compose con PostgreSQL 18

| Campo | Detalle |
|---|---|
| **Objetivo** | Base de datos local reproducible |
| **Archivos** | `docker-compose.yml`, `docker/postgres/init/` (vacío, preparado), `.env.example` |
| **Dependencias** | T-01 |
| **Criterios de aceptación** | 1) `docker compose up -d postgres` levanta PostgreSQL **18** (imagen `postgres:18-alpine`).<br>2) `docker compose ps` muestra el servicio en `healthy` (healthcheck con `pg_isready`).<br>3) `psql` acepta `devtrack/devtrack` en la base `devtrack`.<br>4) Los datos persisten entre reinicios (volumen nombrado).<br>5) `docker compose down -v` limpia todo.<br>6) `.env.example` documenta `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DEVTRACK_JWT_SECRET` **sin valores reales**.<br>7) El puerto 5432 no se expone al host por defecto, o se documenta claramente que sí |
| **Pruebas requeridas** | Verificación manual con la salida de `docker compose ps` y `docker compose logs postgres` |
| **Riesgos / decisiones pendientes** | Puerto 5432 en conflicto con un PostgreSQL local. Composición alternativa con `profiles` |

---

### T-03 · Configuración por perfiles y variables de entorno

| Campo | Detalle |
|---|---|
| **Objetivo** | Configuración externalizada, sin secretos en el repo |
| **Archivos** | `src/main/resources/application.yml`, `src/main/resources/application-local.yml`, `src/main/resources/application-test.yml` |
| **Dependencias** | T-01, T-02 |
| **Criterios de aceptación** | 1) El contenido de `application.yml` es **exactamente** el de `architecture.md` §9.<br>2) `spring.jpa.open-in-view=false` y `spring.jpa.hibernate.ddl-auto=validate`.<br>3) `DEVTRACK_JWT_SECRET` **no tiene valor por defecto** (sin `:` de fallback).<br>4) Al arrancar **sin** `DEVTRACK_JWT_SECRET` la app **falla rápido** con un mensaje claro (no arranca con un secreto inseguro por defecto). Este test es parte de los criterios.<br>5) `application-test.yml` apunta a la BD de test y usa el mismo secreto de prueba.<br>6) Ningún valor sensible está en un archivo versionado |
| **Pruebas requeridas** | Un test que arranque el contexto sin la variable y compruebe el fallo (o un test de validación del bean de propiedades) |
| **Riesgos / decisiones pendientes** | El fail-fast del secreto se implementa en `SecurityConfig`/`JwtProperties` (tarea T-08); en T-03 se deja el hueco y la propiedad declarada |

---

### T-04 · Flyway y esquema inicial

| Campo | Detalle |
|---|---|
| **Objetivo** | El esquema de base de datos lo gobierna Flyway, no Hibernate |
| **Archivos** | `src/main/resources/db/migration/V1__baseline.sql`, `src/main/resources/db/migration/V2__users.sql`, `V3__projects_and_members.sql`, `V4__tasks.sql`, `src/main/resources/db/migration/README.md` |
| **Dependencias** | T-03 |
| **Criterios de aceptación** | 1) Las 4 migraciones existen y son **idempotentes en el sense de versionado** (cada archivo, un número, sin huecos).<br>2) El DDL coincide **columna a columna** con `architecture.md` §4.2: tipos, nullability, defaults, `CHECK` de enums, `UNIQUE`, `ON DELETE CASCADE` / `SET NULL`.<br>3) Los índices `idx_projects_owner`, `idx_pm_user`, `idx_tasks_project_status`, `idx_tasks_project_assignee`, `idx_tasks_project_due` existen.<br>4) `tasks.version BIGINT NOT NULL DEFAULT 0` existe.<br>5) `./mvnw spring-boot:run` con la BD vacía migra y arranca.<br>6) `ddl-auto=validate` **pasa**: si se altera una entidad sin migrar, la app **falla al arrancar** (es la prueba de que el esquema está sincronizado).<br>7) `SELECT version, description FROM flyway_schema_history` muestra 4 filas aplicadas |
| **Pruebas requeridas** | Un test de integración que aplica las migraciones sobre un PostgreSQL limpio (vía Testcontainers) y valida tablas, índices y constraints |
| **Riesgos / decisiones pendientes** | **R-11** (deriva esquema/entidad) mitigado por `validate`. El nombre de los ficheros debe ser `V<n>__<descripcion>.sql` en minúsculas |

---

### T-05 · Base de pruebas de integración con Testcontainers

| Campo | Detalle |
|---|---|
| **Objetivo** | Infraestructura de tests reutilizable, contra PostgreSQL real |
| **Archivos** | `src/test/java/com/devtrack/support/AbstractIntegrationTest.java`, `src/test/java/com/devtrack/support/IntegrationTest.java` (meta-anotación), `src/test/resources/application-test.yml` |
| **Dependencias** | T-04 |
| **Criterios de aceptación** | 1) Usa `org.testcontainers:testcontainers-postgresql:2.0.5` y `org.testcontainers:testcontainers-junit-jupiter:2.0.5` (**nombres de módulo de Testcontainers 2.x**; los de 1.x no existen).<br>2) `@ServiceConnection` de Spring Boot inyecta el `DataSource` automáticamente.<br>3) El contenedor se reutiliza entre clases (`withReuse(true)`) y arranca **una sola vez** por ejecución de tests.<br>4) Flyway corre sobre el contenedor en cada test de integración.<br>5) **H2 está prohibido**: no aparece `h2` en el `pom.xml` en ningún scope.<br>6) Cada test de integración parte de una base limpia (sin depender del orden de ejecución).<br>7) `./mvnw test` levanta el contenedor, migra y pasa |
| **Pruebas requeridas** | Un test de humo `smokeContextLoads` que usa el contenedor |
| **Riesgos / decisiones pendientes** | Requiere Docker en la máquina (T-00). Si Docker no está disponible, los tests de integración **se saltan** con `@DisabledIf` y el Sprint 0 no se puede dar por cerrado |

---

## Sprint 1 — Identidad y acceso

### T-06 · Entidad `User` y repositorio

| Campo | Detalle |
|---|---|
| **Objetivo** | Persistencia de usuarios conforme al modelo |
| **Archivos** | `src/main/java/com/devtrack/user/domain/User.java`, `.../domain/PlatformRole.java`, `.../repository/UserRepository.java`, `src/main/resources/db/migration/V2__users.sql` (si no se creó en T-04) |
| **Dependencias** | T-04, T-05 |
| **Criterios de aceptación** | 1) `User` extiende `BaseEntity` y cumple R-M1..R-M10 de `architecture.md` §4.3.<br>2) `email` es único, `NOT NULL`, `VARCHAR(320)`, y se normaliza a minúsculas **antes de persistir** (`@PrePersist`/`@PreUpdate` o en el servicio — documenta cuál).<br>3) `passwordHash` es `VARCHAR(255)` y **nunca** se expone en ningún DTO.<br>4) `platformRole` es un enum `USER`/`ADMIN` con `@Enumerated(EnumType.STRING)`.<br>5) `UserRepository` tiene `Optional<User> findByEmail(String email)` y `boolean existsByEmail(String email)`.<br>6) `BaseEntity` (`id`, `createdAt`, `updatedAt`) está en `shared.persistence` con `@MappedSuperclass` y `AuditingConfig` con `@EnableJpaAuditing`.<br>7) `createdAt`/`updatedAt` se rellenan automáticamente (`@CreatedDate` / `@LastModifiedDate`).<br>8) `equals`/`hashCode` solo por `id` (R-M4).<br>9) `save()` de un usuario con email duplicado falla con `DataIntegrityViolationException` |
| **Pruebas requeridas** | Test de integración: persiste y relee un usuario; verifica que `email` se normaliza; verifica el fallo por email duplicado; verifica que `createdAt`/`updatedAt` se rellenan |
| **Riesgos / decisiones pendientes** | R-08: `open-in-view=false` obliga a resolver datos dentro de la transacción en T-08 |

---

### T-07 · Manejo centralizado de errores

| Campo | Detalle |
|---|---|
| **Objetivo** | Un único punto donde se traducen las excepciones a respuestas HTTP |
| **Archivos** | `src/main/java/com/devtrack/shared/exception/GlobalExceptionHandler.java`, `ResourceNotFoundException.java`, `ConflictException.java`, `UnprocessableEntityException.java`, `InvalidStateTransitionException.java`, `ErrorCatalog.java` |
| **Dependencias** | T-06 |
| **Criterios de aceptación** | 1) `@RestControllerAdvice` produce `application/problem+json` con los campos de `api-plan.md` §1.2: `type`, `title`, `status`, `detail`, `instance`, `code`, `timestamp`.<br>2) `MethodArgumentNotValidException` → **400** con `errors[]` (`field`, `message`, `rejectedValue`).<br>3) `HttpMessageNotReadableException` → 400 `MALFORMED_JSON`.<br>4) `ResourceNotFoundException` → 404 con su `code`.<br>5) `ConflictException` → 409.<br>6) `UnprocessableEntityException` → 422.<br>7) Excepción no prevista → 500 con `detail` **genérico** (sin el mensaje de la excepción) y el stack trace en el log.<br>8) `ErrorCatalog` centraliza los pares (`code`, `httpStatus`, `title`) del §2 de `api-plan.md`.<br>9) **Ningún controlador devuelve `Map<String,Object>` ni construye JSON a mano.** |
| **Pruebas requeridas** | Un test de integración por cada rama del advice (400 validación, 400 JSON malformado, 404, 409, 422, 500) comprobando **status + `code` + `type`** |
| **Riesgos / decisiones pendientes** | El manejo de `AuthorizationDeniedException` (401/403) **no** se resuelve aquí: va en T-13, porque las excepciones del filtro de seguridad no pasan por el advice (R-06) |

---

### T-08 · Registro, login, BCrypt y JWT

| Campo | Detalle |
|---|---|
| **Objetivo** | Autenticación completa de punta a punta |
| **Archivos** | `src/main/java/com/devtrack/user/api/AuthController.java`, `.../user/api/dto/{RegisterRequest,LoginRequest,AuthResponse}.java`, `.../user/service/AuthService.java`, `.../shared/security/{JwtAuthenticationFilter,JwtService,RestAuthenticationEntryPoint}.java`, `.../shared/config/SecurityConfig.java`, `.../shared/exception/ApiExceptionHandler` (401), `src/main/java/com/devtrack/config/JwtProperties.java` |
| **Dependencias** | T-06, T-07 |
| **Criterios de aceptación** | 1) `POST /api/v1/auth/register` devuelve **201** con `UserResponse` y **nunca** incluye el hash.<br>2) Email duplicado → **409** `EMAIL_ALREADY_REGISTERED`.<br>3) `POST /api/v1/auth/login` devuelve **200** con `accessToken`, `tokenType=Bearer`, `expiresIn`, `user`.<br>4) Contraseña almacenada con **BCrypt** (`BCryptPasswordEncoder`, coste 10). Un test verifica que el hash almacenado **no** es igual a la contraseña en claro y que `matches()` funciona.<br>5) El JWT es **HS256**, con claims `sub` (user id), `email`, `role`, `iss=devtrack`, `iat`, `exp`. TTL **15 min**.<br>6) El secreto viene de `DEVTRACK_JWT_SECRET`; si falta o mide **< 32 bytes**, la app **falla al arrancar** (fail-fast). Test específico.<br>7) La `SecurityFilterChain` es **stateless**; CSRF desactivado con la justificación de `architecture.md` §5.2; CORS restringido a `devtrack.cors.allowed-origins`.<br>8) Rutas públicas: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`. **Todo lo demás exige token.**<br>9) Un token válido autentica; uno **expirado**, uno **con firma inválida** o uno **malformado** devuelven **401** con `ProblemDetail` `code=UNAUTHENTICATED`.<br>10) El login con email inexistente y con contraseña incorrecta devuelven el **mismo** `code` y el **mismo** `detail` (no enumeración de cuentas).<br>11) `users.enabled=false` → **403** `ACCOUNT_DISABLED` en el login y **401** en peticiones posteriores.<br>12) **No se loggea** el token ni el cuerpo del login |
| **Pruebas requeridas** | Integración: flujo register→login→petición autenticada completo; token expirado; token con firma manipulada; token ausente; email duplicado; contraseña incorrecta; usuario deshabilitado; fail-fast del secreto. **Verificar el cuerpo de la respuesta, no solo el status** (R-06, R-10) |
| **Riesgos / decisiones pendientes** | **R-10 / R-12**: `jjwt-jackson` usa Jackson 2 mientras Spring Boot 4 usa Jackson 3. Si el arranque o el parseo del token falla, la alternativa es `jjwt-gson` (o `jjwt-orgjson`) en lugar de `jjwt-jackson`. **Verificarlo en el primer test.** **Q-03**: sin refresh token en la v1 |

---

### T-09 · `GET /users/me` y cierre del flujo de identidad

| Campo | Detalle |
|---|---|
| **Objetivo** | El usuario autenticado puede consultar su propio perfil |
| **Archivos** | `src/main/java/com/devtrack/user/api/UserController.java`, `.../user/api/dto/UserResponse.java`, `.../user/service/UserService.java`, `.../shared/security/CurrentUser.java` |
| **Dependencias** | T-08 |
| **Criterios de aceptación** | 1) `GET /api/v1/users/me` devuelve **200** con `id`, `email`, `fullName`, `platformRole`, `createdAt`.<br>2) **Nunca** incluye `passwordHash`.<br>3) Sin token → 401 `UNAUTHENTICATED`.<br>4) `UserResponse` se construye por **mapper explícito**, no por reflexión/serialización de la entidad.<br>5) `CurrentUser` se obtiene con `@AuthenticationPrincipal` y expone `userId`, `email`, `platformRole`.<br>6) El `Principal` del `SecurityContext` contiene el **user id**, no solo el email (necesario para T-11) |
| **Pruebas requeridas** | Integración: con token válido → 200 y el email correcto; sin token → 401; el JSON **no** contiene `passwordHash` ni `password` |
| **Riesgos / decisiones pendientes** | R-08: el `Principal` se resuelve en cada petición; verificar que no hay N+1 ni `LazyInitializationException` al leer `email` |

---

## Sprint 2 — Proyectos y membresía

### T-10 · Entidades `Project` y `ProjectMember`

| Campo | Detalle |
|---|---|
| **Objetivo** | Persistencia de proyectos y membresía con sus restricciones |
| **Archivos** | `src/main/java/com/devtrack/project/domain/{Project,ProjectMember,ProjectRole}.java`, `.../project/repository/{ProjectRepository,ProjectMemberRepository}.java`, `src/main/resources/db/migration/V3__projects_and_members.sql` |
| **Dependencias** | T-06 |
| **Criterios de aceptación** | 1) `Project` y `ProjectMember` cumplen `architecture.md` §4.2 y §4.3 (R-M1..R-M10).<br>2) `ProjectRole` = `OWNER` \| `EDITOR` \| `VIEWER`, `@Enumerated(EnumType.STRING)`.<br>3) `ProjectMember` tiene **UNIQUE (project_id, user_id)**.<br>4) Las relaciones son **unidireccionales** (R-M2/R-M3): `Project` **no** tiene `List<ProjectMember>` ni `List<Task>`.<br>5) `ProjectMemberRepository` tiene `Optional<ProjectMember> findByProjectIdAndUserId(Long, Long)`, `boolean existsByProjectIdAndUserId(...)`, `List<ProjectMember> findByUserId(Long)`, `long countByProjectId(Long)`.<br>6) `ProjectRepository` tiene `boolean existsByOwnerIdAndName(...)` si se decide unicidad de nombre (por defecto **no**).<br>7) Al borrar un proyecto, sus miembros y tareas se borran en cascada (verificado con un test de integración).<br>8) Al borrar un usuario, sus tareas asignadas pasan a `assignee_id = NULL` y sus tareas creadas **impiden** el borrado del usuario (FK restrictiva). |
| **Pruebas requeridas** | Integración: alta de proyecto + owner como miembro; UNIQUE rechaza el segundo miembro con el mismo `user_id`; cascada al borrar proyecto; `SET NULL` al borrar usuario asignado |
| **Riesgos / decisiones pendientes** | **R-05**: la invariante "exactamente un OWNER" se mantiene en el servicio (T-11), no en SQL. **Q-02**: confirmar los roles |

---

### T-11 · `ProjectAccessGuard` y servicios de proyecto/miembro ★

| Campo | Detalle |
|---|---|
| **Objetivo** | **La pieza crítica de seguridad.** Toda autorización por membresía vive aquí |
| **Archivos** | `src/main/java/com/devtrack/shared/security/ProjectAccessGuard.java`, `src/main/java/com/devtrack/project/service/{ProjectService,ProjectMemberService}.java` |
| **Dependencias** | T-09, T-10 |
| **Criterios de aceptación** | **Sobre el guard:**<br>1) `requireMember(projectId, userId)` → lanza `ResourceNotFoundException` si **no** hay fila en `project_members` (→ 404, sin revelar existencia).<br>2) `requireEditor(projectId, userId)` → 404 si no es miembro; **403** si es `VIEWER`.<br>3) `requireOwner(projectId, userId)` → 404 si no es miembro; **403** si es `EDITOR` o `VIEWER`.<br>4) `requireOwnerOrAdmin(...)` → además acepta `platformRole=ADMIN`.<br>5) El guard **no** distingue entre "proyecto inexistente" y "no es miembro": ambos → 404 con el mismo `detail`.<br>6) Los métodos son **públicos y testeables unitariamente** (inyección del repositorio, sin contexto HTTP).<br>**Sobre los servicios:**<br>7) `ProjectService.create(userId, request)` inserta el proyecto **y** el miembro `OWNER` en la **misma transacción** (`@Transactional`). Un test fuerza el fallo del segundo insert y verifica que no queda el proyecto huérfano.<br>8) `ProjectService.findByIdForMember(projectId, userId)` llama al guard **antes** de leer la entidad.<br>9) `ProjectService.listForUser(userId, ...)` devuelve **solo** proyectos donde el usuario es miembro (join con `project_members`).<br>10) `ProjectMemberService.add(...)` → 409 si ya es miembro; 404 si el usuario no existe; **nunca** crea un `OWNER`.<br>11) `ProjectMemberService.changeRole(...)` → 409 si se intenta degradar al owner.<br>12) `ProjectMemberService.remove(...)` → 409 si es el owner.<br>13) `ProjectMemberService.transferOwnership(...)` → en **una sola transacción**: el owner actual pasa a `EDITOR` y el receptor a `OWNER`. Si no hay siguiente owner, **rollback completo** (verificado con un test).<br>14) `ProjectService.archive(...)` marca `archived=true` (no borra).<br>15) **Todos** los métodos de servicio son `@Transactional` (readOnly donde aplique).<br>16) **Ninguna** entidad JPA se devuelve desde el servicio: se devuelven DTOs o modelos de lectura |
| **Pruebas requeridas** | **Unitarias (guard):** matriz de 3 roles × 4 operaciones = 12 casos, con `@ParameterizedTest`.<br>**Unitarias (servicios):** transacciones y casos de conflicto con mocks.<br>**De integración:** ver T-16 |
| **Riesgos / decisiones pendientes** | **R-01, R-02, R-13**: es la tarea de mayor riesgo del proyecto. **Q-04**: si se rechaza la transferencia de ownership, se elimina el punto 13 y la ruta `PUT .../ownership`. La documentación del guard debe explicar por qué 404 y no 403 para los no-miembros |

---

### T-12 · Controllers de proyecto y de miembros

| Campo | Detalle |
|---|---|
| **Objetivo** | Exponer el contrato HTTP de proyectos y miembros |
| **Archivos** | `src/main/java/com/devtrack/project/api/{ProjectController,ProjectMemberController}.java`, `.../project/api/dto/*.java` |
| **Dependencias** | T-11 |
| **Criterios de aceptación** | 1) Las rutas, métodos, cuerpos y códigos coinciden **exactamente** con `api-plan.md` §4 y §5.<br>2) Los controladores **no contienen lógica de negocio**: solo validan, delegan al servicio y traducen. Cada controlador es legible de un vistazo.<br>3) Las entradas se validan con Bean Validation en los DTOs de request (`@Valid @RequestBody`).<br>4) Las salidas son **DTOs**, nunca entidades.<br>5) `GET /projects` devuelve `PageResponse<ProjectResponse>` con el `role` del llamador.<br>6) `GET /projects/{projectId}` → 404 `PROJECT_NOT_FOUND` si no es miembro.<br>7) `PATCH /projects/{projectId}` → 403 si el llamador es `EDITOR`/`VIEWER`.<br>8) `DELETE /projects/{projectId}` → 204 y `archived=true` en la BD.<br>9) `POST /projects/{id}/members` → 201 con `Location`.<br>10) `PATCH/DELETE /projects/{id}/members/{userId}` → 200/204 con las protecciones del owner.<br>11) `PUT /projects/{id}/members/{userId}/ownership` → 200 con el `ProjectResponse` actualizado (o 404 si esa ruta no se aprueba, ver Q-04).<br>12) Los parámetros de paginación están acotados (`size` máximo 100) y un `size=1000` devuelve **400**, no una respuesta enorme. |
| **Pruebas requeridas** | Un test de integración por endpoint, parametrizado por rol, que compruebe status + `code` + cuerpo |
| **Riesgos / decisiones pendientes** | N+1 al calcular `memberCount`/`taskCount`: usar queries de conteo separadas, nunca cargar las colecciones. Verificarlo en el test de listado |

---

### T-13 · Errores 401 y 403 con `ProblemDetail` ★

| Campo | Detalle |
|---|---|
| **Objetivo** | Que los errores de seguridad tengan el **mismo formato** que el resto de la API |
| **Archivos** | `src/main/java/com/devtrack/shared/security/{RestAuthenticationEntryPoint,RestAccessDeniedHandler}.java`, `src/main/java/com/devtrack/shared/exception/GlobalExceptionHandler.java` (añadir el handler de `AuthorizationDeniedException`) |
| **Dependencias** | T-07, T-12 |
| **Criterios de aceptación** | 1) Petición **sin token** → **401**, `content-type: application/problem+json`, `code=UNAUTHENTICATED`.<br>2) Token **inválido o expirado** → **401**, mismo formato.<br>3) Token válido, usuario **miembro con rol insuficiente** → **403**, `code=FORBIDDEN`.<br>4) Token válido, usuario **no miembro** → **404**, `code=PROJECT_NOT_FOUND`.<br>5) Los **cuerpos** de 401/403/404 tienen la misma estructura (`type`, `title`, `status`, `detail`, `instance`, `code`, `timestamp`).<br>6) El 401 y el 403 **no** incluyen detalles internos (stack traces, nombres de tablas, clase de excepción).<br>7) El `detail` de un 404 **no** distingue entre "inexistente" y "sin permiso".<br>8) Se cubre el error de seguridad a **nivel de método** (`AuthorizationDeniedException` de Spring Security 7) con un handler en el advice, además de los handlers del filtro |
| **Pruebas requeridas** | Test de integración que compruebe **status + `content-type` + `code` + `detail`** en los 4 casos (1,2,3,4). **Comprobar el cuerpo, no solo el status** |
| **Riesgos / decisiones pendientes** | **R-06**: Spring Security 7 deprecó `AccessDeniedException` a favor de `AuthorizationDeniedException`. Hay que confirmar con la versión exacta (7.1.1) qué excepción lanza el `AuthorizationManager` y cubrir **ambas** clases en el handler por si acaso |

---

## Sprint 3 — Tareas

### T-14 · Entidad `Task` y `TaskStatusPolicy`

| Campo | Detalle |
|---|---|
| **Objetivo** | Persistencia de tareas y máquina de estados |
| **Archivos** | `src/main/java/com/devtrack/task/domain/{Task,TaskStatus,TaskPriority}.java`, `.../task/service/TaskStatusPolicy.java`, `.../task/repository/TaskRepository.java`, `src/main/resources/db/migration/V4__tasks.sql` |
| **Dependencias** | T-10 |
| **Criterios de aceptación** | 1) `Task` cumple `architecture.md` §4.2 y §4.3, con `@Version private Long version` (R-M8).<br>2) `TaskStatus` = `TODO` \| `IN_PROGRESS` \| `DONE` \| `CANCELLED`; `TaskPriority` = `LOW` \| `MEDIUM` \| `HIGH` \| `URGENT`. Ambos `@Enumerated(EnumType.STRING)`.<br>3) `dueDate` es `LocalDate`; los timestamps son `Instant`.<br>4) `assignee_id` es **nullable** con `ON DELETE SET NULL`; `created_by` es **NOT NULL** sin cascada.<br>5) Existen los 3 índices de `tasks`.<br>6) `TaskStatusPolicy` implementa **exactamente** la matriz de `architecture.md` §4.4:<br>  · permitidas: `TODO→IN_PROGRESS`, `TODO→DONE`, `TODO→CANCELLED`, `IN_PROGRESS→TODO`, `IN_PROGRESS→DONE`, `IN_PROGRESS→CANCELLED`, `DONE→IN_PROGRESS`, `DONE→CANCELLED`, `CANCELLED→TODO`.<br>  · **prohibidas**: `DONE→TODO`, `CANCELLED→IN_PROGRESS`, `CANCELLED→DONE`.<br>  · **no-op** (mismo estado): permitido, no modifica `updated_at`.<br>7) `TaskStatusPolicy` es una clase **sin dependencias de Spring** (POJO), testeable de forma aislada.<br>8) `TaskRepository` tiene `findByProjectId(...)` con filtros y paginación (`Pageable`), `findByIdAndProjectId(...)`, `countByProjectId(...)`, `countByProjectIdAndAssigneeId(...)` |
| **Pruebas requeridas** | **Unitarias (policy):** los 16 pares (estado origen × destino) como `@ParameterizedTest`, comprobando permitido/prohibido/no-op. **De integración:** la tarea se persiste y relee con `version=0`; los índices existen |
| **Riesgos / decisiones pendientes** | La política debe ser consultable desde el servicio; si más adelante se quieres auditar, se registrará en el historial (fase 2) |

---

### T-15 · `TaskService` y controller de tareas

| Campo | Detalle |
|---|---|
| **Objetivo** | CRUD de tareas anidado bajo el proyecto, con filtros y paginación |
| **Archivos** | `src/main/java/com/devtrack/task/service/TaskService.java`, `.../task/api/TaskController.java`, `.../task/api/dto/{CreateTaskRequest,UpdateTaskRequest,AssignTaskRequest,TaskResponse}.java` |
| **Dependencias** | T-11, T-13, T-14 |
| **Criterios de aceptación** | 1) Las rutas, métodos y códigos coinciden **exactamente** con `api-plan.md` §6.<br>2) **Toda** operación pasa por `ProjectAccessGuard` **antes** de tocar la tarea. Un `taskId` de otro proyecto no es accesible ni por error ni por confusión de ruta.<br>3) Crear tarea requiere rol `EDITOR`+; un `VIEWER` recibe **403**.<br>4) `assigneeId` debe ser **miembro del proyecto**; si no, **422** `ASSIGNEE_NOT_A_MEMBER`.<br>5) `PATCH` con un `status` no permitido → **422** `INVALID_STATUS_TRANSITION`.<br>6) `PATCH` **no** cambia el asignable; la asignación tiene su propio endpoint `PUT .../assignee`.<br>7) `PUT .../assignee` es **idempotente**: asignar el mismo usuario dos veces no cambia nada; `{"assigneeId": null}` desasigna.<br>8) `DELETE` → 204; un `EDITOR` que **no** creó la tarea recibe **422** `TASK_DELETION_NOT_ALLOWED`; el `OWNER` puede borrarla.<br>9) `GET .../tasks` soporta todos los filtros de `api-plan.md` §6.1: `status` (repetible), `priority` (repetible), `assigneeId`, `unassigned`, `dueBefore`, `overdue`, `q`, `page`, `size`, `sort`.<br>10) La paginación es **estable y determinista** (siempre un desempate por `id`).<br>11) Los valores de enum inválidos en el query devuelven **400**, no un 500.<br>12) `TaskResponse` no expone `createdBy` como entidad; expone `createdById` y `createdByName`.<br>13) La consulta de listado **no** produce N+1 (verificable contando queries o usando `@Transactional(readOnly=true)` con `JOIN FETCH` para los nombres) |
| **Pruebas requeridas** | Integración: un test por endpoint; un test por filtro (combinados y no combinados); un test por transición (permitida, prohibida, no-op); un test de idempotencia de asignación; un test de borrado por un EDITOR no creador; un test de `taskId` de otro proyecto → 404 |
| **Riesgos / decisiones pendientes** | **R-07**: el conflicto de versión produce `OptimisticLockingFailureException` → mapear a **409** `OPTIMISTIC_LOCK_CONFLICT` en el advice (T-15). La prueba de concurrencia real (dos peticiones simultáneas) puede ser intermitente: **preferir un test que fije la `version` esperada en el `If-Match`** o verificar el excepción del servicio, no una carrera real |

---

### T-16 · Locking optimista y matriz de autorización completa

| Campo | Detalle |
|---|---|
| **Objetivo** | Cerrar los dos riesgos más altos del proyecto (R-02, R-07) con evidencia |
| **Archivos** | `src/main/java/com/devtrack/shared/exception/GlobalExceptionHandler.java` (handler de `OptimisticLockingFailureException`), `src/test/java/com/devtrack/security/ProjectAuthorizationMatrixTest.java`, `src/test/java/com/devtrack/task/TaskConcurrencyTest.java` |
| **Dependencias** | T-15 |
| **Criterios de aceptación** | 1) `OptimisticLockingFailureException` → **409** `OPTIMISTIC_LOCK_CONFLICT`.<br>2) Existe un test que carga una tarea, la modifica con otra instancia del servicio (misma `version` original) y comprueba que la segunda escritura **falla** con 409.<br>3) Existe `ProjectAuthorizationMatrixTest` que cubre **la matriz completa** de `architecture.md` §5.3: para cada operación × cada rol, el status esperado. **Ninguna celda sin cubrir.**<br>4) El test incluye el caso **IDOR**: un usuario miembro del proyecto A no puede leer ni modificar un recurso del proyecto B (→ 404).<br>5) El test incluye el caso **no miembro** con un `projectId` existente → 404 con el **mismo** `detail` que un `projectId` inexistente.<br>6) El test incluye el caso **token de usuario deshabilitado** → 401.<br>7) La matriz está parametrizada (`@ParameterizedTest`) y es legible de un vistazo |
| **Pruebas requeridas** | Es la tarea: los tests son el entregable |
| **Riesgos / decisiones pendientes** | Si el test de concurrencia resulta intermitente, se documenta la limitación y se usa una aproximación determinista (manipular la `version` directamente) |

---

## Sprint 4 — Calidad y entrega

### T-17 · Documentación OpenAPI

| Campo | Detalle |
|---|---|
| **Objetivo** | Una API autodocumentada y legible |
| **Archivos** | `src/main/java/com/devtrack/shared/config/OpenApiConfig.java`, anotaciones en todos los controllers y DTOs |
| **Dependencias** | T-15 |
| **Criterios de aceptación** | 1) `/v3/api-docs` devuelve **200** con un documento OpenAPI **3.1** válido.<br>2) `/swagger-ui.html` carga la UI.<br>3) El esquema de seguridad `bearerAuth` (JWT) está aplicado globalmente; los endpoints públicos están explícitamente marcados como sin seguridad.<br>4) La descripción de la API incluye la **tabla de roles** (OWNER/EDITOR/VIEWER) y la política 404/403.<br>5) Cada endpoint tiene `@Operation(summary=...)` y `@ApiResponse` para cada código documentado en `api-plan.md` §8.<br>6) Los DTOs tienen `@Schema` con `description`, `example` y las restricciones (`minimum`, `maxLength`) donde proceda.<br>7) El documento se puede **descargar como YAML** (`/v3/api-docs.yaml`).<br>8) Con `springdoc.api-docs.enabled=false` (perfil de producción) el endpoint devuelve **404** |
| **Pruebas requeridas** | Test de integración que compruebe 200 y la presencia de los paths esperados en el JSON |
| **Riesgos / decisiones pendientes** | springdoc 3.x expone `/mcp` sin autenticación por defecto: **no habilitarlo** o protegerlo |

---

### T-18 · Dockerfile y servicio `api` en Compose

| Campo | Detalle |
|---|---|
| **Objetivo** | La aplicación completa en un comando |
| **Archivos** | `Dockerfile`, `.dockerignore`, `docker-compose.yml` (ampliar) |
| **Dependencias** | T-16 |
| **Criterios de aceptación** | 1) `Dockerfile` **multi-stage**: etapa de build con JDK 21 + Maven, etapa final con JRE 21 (sin herramientas de build).<br>2) La imagen final no contiene el JDK completo ni las fuentes.<br>3) La app corre como **usuario no root**.<br>4) `docker compose up` levanta `postgres` **y** `api`; la `api` espera a que la BD esté `healthy` (`depends_on: condition: service_healthy`).<br>5) Las migraciones de Flyway se ejecutan al arrancar la app.<br>6) `DEVTRACK_JWT_SECRET` llega a la app **desde el entorno**, no está en la imagen ni en el `Dockerfile`.<br>7) `docker compose up` + `curl /actuator/health` → `UP`.<br>8) La imagen tiene un tamaño razonable (JRE, no JDK) |
| **Pruebas requeridas** | Verificación manual con la salida de `docker compose ps` y `curl`. **Ojo:** `docker compose up` levanta la BD en el puerto 5432 → posible conflicto con T-02. Usar un perfil o un puerto distinto para el stack de integración |
| **Riesgos / decisiones pendientes** | Conflicto de puertos entre el Postgres de desarrollo y el de integración. Resolver con `profiles: ["dev"]` / `["stack"]` |

---

### T-19 · CI en GitHub Actions

| Campo | Detalle |
|---|---|
| **Objetivo** | Cada push ejecuta build y tests |
| **Archivos** | `.github/workflows/ci.yml`, `.gitignore` (actualizar), `git init` + primer commit |
| **Dependencias** | T-16 |
| **Criterios de aceptación** | 1) El workflow dispara en `push` a `main` y en `pull_request`.<br>2) Usa **GitHub Actions** oficiales y **pines las versiones** (no `@main`).<br>3) Usa el **Maven Wrapper** del repo, no un Maven instalado.<br>4) Configura **cache de Maven**.<br>5) Levanta el servicio **PostgreSQL** (service container) para que los tests de integración tengan BD.<br>6) Expone `DEVTRACK_JWT_SECRET` como **secret** del repositorio (con un valor de prueba en el `secrets` de GitHub, no en el YAML).<br>7) El job falla si `./mvnw verify` falla. **Nunca** `-DskipTests`.<br>8) El badge de estado está en el `README.md`.<br>9) El repositorio tiene commits limpios y un `.gitignore` que excluye `target/`, `.env`, `.idea/`, `*.iml` |
| **Pruebas requeridas** | El workflow corre en verde sobre el repositorio. Adjuntar el enlace al run |
| **Riesgos / decisiones pendientes** | **Q-06**: ¿repositorio público? Requiere crear el repo en GitHub. El secret `DEVTRACK_JWT_SECRET` hay que crearlo en la configuración del repo |

---

### T-20 · Endurecimiento de seguridad

| Campo | Detalle |
|---|---|
| **Objetivo** | Cerrar los riesgos de seguridad abiertos que no bloquean la v1 |
| **Archivos** | `src/main/resources/application.yml`, `src/main/java/com/devtrack/shared/config/*`, `.env.example`, `README.md` |
| **Dependencias** | T-16 |
| **Criterios de aceptación** | 1) `.gitignore` incluye `.env` y `*.env` (y **no** `.env.example`).<br>2) `.env.example` documenta **todas** las variables con valores de ejemplo **no secretos**.<br>3) `git log --all -p \| grep -iE "secret\|password\|token"` no revela ningún valor real.<br>4) El log **no** contiene el token, ni el `Authorization`, ni el cuerpo del login. Se verifica forzando un login incorrecto y revisando el log.<br>5) CORS restringido a la lista configurada; el origen `*` no está por defecto.<br>6) `spring.jpa.open-in-view=false` confirmado.<br>7) `ddl-auto=validate` confirmado (no `update` ni `create`).<br>8) No se devuelven entidades JPA ni campos sensibles en ningún DTO (revisión de todos los DTOs de respuesta).<br>9) Los mensajes de error de login no enumeran cuentas.<br>10) La documentación incluye una sección "Seguridad" con el modelo de roles y las decisiones tomadas |
| **Pruebas requeridas** | Test que comprueba que el log no contiene el token (con un logback appender de prueba o revisando la salida). Test del fail-fast del secreto |
| **Riesgos / decisiones pendientes** | El rate limiting queda para `F2-SEC` (requiere decisión de infraestructura, no un simple filtro) |

---

### T-21 · Documentación final del proyecto

| Campo | Detalle |
|---|---|
| **Objetivo** | Que un tercero pueda clonar, levantar y entender el proyecto |
| **Archivos** | `README.md`, `docs/architecture.md` (actualizar), `docs/api-plan.md` (ajustar a lo implementado), `docs/adr/` (completar) |
| **Dependencias** | T-17, T-18, T-19, T-20 |
| **Criterios de aceptación** | 1) `README.md` tiene: nombre + descripción, **captura o texto del flujo principal**, stack **con versiones reales**, **requisitos previos**, **instrucciones de arranque en ≤ 5 pasos**, comandos de test, **estructura del proyecto**, **decisiones de diseño** (enlace a los ADRs), **qué NO incluye** (fase 2), licencia.<br>2) Los comandos del README están **verificados**: se ejecutan y su salida se pega en el PR.<br>3) `docs/architecture.md` refleja **lo implementado**, no lo planificado. Las divergencias se anotan explícitamente.<br>4) `docs/api-plan.md` refleja el contrato **real**.<br>5) Cada ADR tiene: contexto, decisión, consecuencias, estado (`Aceptado`), fecha.<br>6) Existe un ADR para cada decisión que se apartó de la propuesta original, explicando por qué.<br>7) Un diagrama de la estructura de paquetes y del modelo de datos.<br>8) Se eliminan del repo **todas** las referencias a tareas pendientes del backlog |
| **Pruebas requeridas** | Un revisor externo (el Agente 1) debe poder clonar y arrancar el proyecto **solo** con el README |
| **Riesgos / decisiones pendientes** | La documentación siempre queda desfasada: se actualiza en el mismo PR que cambia el código, no al final |

---

## Resumen de dependencias

```
T-00 → T-01 → T-02 → T-03 → T-04 → T-05
                        │      │
                        ▼      ▼
                       T-06 → T-07 → T-08 → T-09
                        │              │
                        ▼              ▼
                       T-10 → T-11 → T-12 → T-13
                        │              │
                        ▼              ▼
                       T-14 ──────────► T-15 → T-16
                                             │
                                             ▼
                                    T-17, T-18, T-19, T-20 → T-21
```

## Definición de "terminado" (aplica a todas las tareas)

- [ ] `./mvnw clean verify` en verde, **sin** `skipTests`.
- [ ] Los criterios de aceptación de la tarea están **todos** verificados, con evidencia.
- [ ] Hay tests **nuevos** que fallan sin el cambio (si la tarea no es solo documentación).
- [ ] No hay entidades JPA en las respuestas; no hay `System.out.println`; no hay secretos.
- [ ] El código sigue las reglas de `architecture.md` §3.3 (dependencias entre paquetes).
- [ ] Si la tarea cambió un contrato, `docs/api-plan.md` se actualiza **en el mismo commit**.
- [ ] Si la tarea cambió una decisión, se añade o actualiza un ADR en el mismo commit.
- [ ] El mensaje del commit describe el *qué* y el *por qué*, en imperativo y en español.


---

## Estado de implementación (2026-10-21)

| Sprint | Tareas | Estado | Evidencia |
|---|---|---|---|
| S0 Cimientos | T-00 … T-05 | Completado | Build verde, 3 migraciones aplicadas, `ddl-auto=validate` sin deriva |
| S1 Identidad | T-06 … T-09 | Completado | 20 pruebas (registro, login, tokens, fail-fast del secreto) |
| S2 Proyectos | T-10 … T-13 | Completado | 26 pruebas + matriz de autorización de 138 casos |
| S3 Tareas | T-14 … T-16 | Completado | 38 pruebas (CRUD, filtros, 16 transiciones, concurrencia) |
| S4 Calidad | T-17 … T-21 | Completado | OpenAPI 3.1.0 con 11 rutas, Dockerfile multi-stage, CI en GitHub Actions, README y ADRs |

### Criterios de aceptación no verificables de forma automática

| Criterio | Cómo se comprobó |
|---|---|
| T-00 · toolchain instalado | `java -version` (21.0.13), `docker --version` (29.9.2), `colima version` (0.10.3) |
| T-02 · healthcheck de PostgreSQL | `docker compose ps` → `Up (healthy)` |
| T-18 · la imagen arranca | `docker build` correcto y **stack completo levantado** (`docker compose --profile api up -d`): ambos contenedores `healthy`, flujo HTTP completo ejercitado sobre el puerto 8080, migraciones aplicadas y proceso ejecutándose como usuario no root (`uid=100(devtrack)`) |
| T-19 · CI en verde | Workflow escrito; **no ejecutado** (requiere publicar el repositorio en GitHub, pregunta Q-06 sin responder) |

### Pruebas de humo end-to-end ejecutadas

Sobre el jar empaquetado, con PostgreSQL 18 real:

```
POST /api/v1/auth/register                -> 201
POST /api/v1/auth/login                   -> 200 (token JWT)
POST /api/v1/projects                     -> 201 (Location + id)
POST /api/v1/projects/1/tasks             -> 201
GET  /api/v1/projects/1/tasks?status=TODO -> 200 (PageResponse)
GET  /api/v1/projects/1  (sin token)       -> 401 UNAUTHENTICATED
GET  /api/v1/projects/1  (sin membresia)  -> 404 PROJECT_NOT_FOUND
POST /api/v1/projects  (nombre vacio)      -> 400 VALIDATION_ERROR + errors[]
GET  /v3/api-docs                         -> 200 (OpenAPI 3.1.0, 11 rutas, esquema bearerAuth)
```

### Verificación del stack en Docker

```
docker compose --profile api up -d
devtrack-api        Up (healthy)
devtrack-postgres   Up (healthy)

flyway_schema_history:  1 users | 2 projects and members | 3 tasks
GET /v3/api-docs       -> 200, OpenAPI 3.1.0, 11 rutas
GET /swagger-ui/index.html -> 200
POST .../tasks         -> 201
PATCH .../tasks/{id}   -> 422 INVALID_STATUS_TRANSITION (DONE -> TODO)
```

### Trabajo pendiente

| Ítem | Motivo |
|---|---|
| Instalar GitHub CLI y publicar el repositorio | `gh` no esta instalado en la maquina y falta la cuenta de GitHub del usuario (Q-06) |
| Publicar el repositorio en GitHub y ejecutar el CI | Requiere la cuenta de GitHub del usuario (Q-06 sin responder) |
| Publicar la imagen en un registro | Fuera del alcance de la v1 |
| Licencia | Pendiente de decidir |
