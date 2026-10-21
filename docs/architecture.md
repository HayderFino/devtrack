# DevTrack — Arquitectura (propuesta inicial)

> **Estado:** PROPUESTA — pendiente de aprobación del usuario.
> **Autor:** Agente 1 (Arquitectura / Revisión técnica).
> **Fecha:** 2026-10-21.
> Este documento describe lo que se **propone**. Las decisiones marcadas con `PROPUESTO` en
> `docs/adr/` no están fijadas hasta que el usuario las apruebe.

---

## 0. Observaciones previas sobre el estado actual (hechos observados)

Estas son observaciones directas del entorno, no propuestas.

| Elemento | Estado observado | Evidencia |
|---|---|---|
| Directorio de trabajo | **Vacío**. No hay ningún archivo. | `ls -la` → solo `.` y `..` |
| Control de versiones | **No es un repositorio Git** | `git status` → *"Is directory a git repo: no"* |
| Java (JDK) | **No instalado**. `java -version` → *"Unable to locate a Java Runtime"* | `java -version` |
| Maven | **No instalado**. `mvn` → *command not found* | `mvn -v` |
| Docker / Compose | **No instalado**. `docker` → *command not found* | `docker --version` |
| Git | **Instalado**, v2.55.0 | `git --version` |
| Homebrew | **Instalado**, 7.0.7 | `brew --version` |
| Homebrew: JDK disponibles | `openjdk@21` (21.0.13), `openjdk@25` (25.0.5), `openjdk` (27) | `brew info` |

**Consecuencia:** ninguna tarea de implementación puede empezar hasta instalar el toolchain.
Esta es la Tarea `T-00` y es bloqueante para todo lo demás.

**Supuesto A-00:** el proyecto DevTrack vive en `./devtrack/` dentro del directorio de trabajo actual
(el directorio actual se llama `pracctica  exploracion opencode`, no `devtrack`, y está vacío).
Es trivial moverlo o renombrarlo si se prefiere otra ubicación.

---

## 1. Resumen de requisitos y supuestos

### 1.1 Requisitos (derivados del encargo)

| ID | Requisito | Prioridad |
|---|---|---|
| R-01 | API REST de gestión de proyectos y tareas | Alta |
| R-02 | Usuarios con autenticación | Alta |
| R-03 | Proyectos con miembros y roles por proyecto | Alta |
| R-04 | Tareas con descripción, estado, prioridad, fecha límite y persona asignada | Alta |
| R-05 | Monolito modular, mantenible, ejecutable localmente | Alta |
| R-06 | Persistencia relacional (PostgreSQL) con migraciones versionadas | Alta |
| R-07 | Autorización en servidor: conocer un ID no da acceso | Alta |
| R-08 | Pruebas unitarias y de integración | Alta |
| R-09 | Documentación OpenAPI/Swagger | Media |
| R-10 | Entorno local reproducible con Docker Compose | Media |
| R-11 | Repositorio Git en GitHub, con CI | Media |
| R-12 | Comentarios, historial de actividad y métricas | **Fase posterior** |

### 1.2 Supuestos declarados

| ID | Supuesto | Impacto si es falso |
|---|---|---|
| A-01 | El proyecto vive en `./devtrack/` (ver arriba) | Bajo — solo mueve archivos |
| A-02 | Un proyecto tiene un **único owner** (usuario), y además el owner es un miembro con rol `OWNER` | Medio — afecta el modelo de membresía |
| A-03 | Los roles por proyecto son `OWNER`, `EDITOR`, `VIEWER` (jerárquicos) | Medio — cambia la matriz de autorización |
| A-04 | Solo hay autenticación por **email + password**. No hay OAuth/SSO en la v1 | Alto — si se pide SSO, cambia el sprint 1 |
| A-05 | La v1 usa **JWT de acceso stateless** (sin refresh token) | Medio — refresh token se pidió explícitamente "cuando se implemente autenticación" |
| A-06 | Se usan **enums de dominio almacenados como `VARCHAR` + `CHECK` constraint**, no tipos ENUM nativos de PostgreSQL | Bajo — facilita migraciones futuras |
| A-07 | Los emails se almacenan **normalizados en minúsculas** y son únicos globalmente | Bajo |
| A-08 | Un usuario puede pertenecer a muchos proyectos; un proyecto a muchos usuarios (N:M) | Alto — es el núcleo del modelo |
| A-09 | Las tareas se exponen **anidadas bajo el proyecto** (`/projects/{id}/tasks`) | Medio — cambia el contrato REST |
| A-10 | Se usan **DTOs + `ProblemDetail` (RFC 7807)**; nunca se serializan entidades JPA | Alto — es una regla de diseño explícita |
| A-11 | La gestión de secretos por variables de entorno; nada de secretos en el repo | Alto |
| A-12 | Los tests de integración usan **PostgreSQL real vía Testcontainers**, no H2 | Medio — evita divergencia de dialecto SQL |
| A-13 | Idioma de la documentación: **español** (consistente con el encargo) | Bajo |
| A-14 | El producto es **monolingüe en la interfaz**; no hay i18n en la v1 | Bajo |

### 1.3 Fuera de alcance de la v1 (fase posterior)

- Refresh tokens / rotación de tokens / revocación (`F2-AUTH`)
- OAuth2 / OIDC / SSO (`F2-AUTH`)
- Recuperación de contraseña y verificación de email (`F2-AUTH`)
- Rate limiting (`F2-SEC`)
- MFA / 2FA (`F2-SEC`)
- Comentarios e historial de actividad (`F2-FEAT`)
- Métricas de progreso y reporting (`F2-FEAT`)
- Búsqueda de texto completo (`F2-FEAT`)
- Multi-tenantidad por organización (`F3-ARCH`)
- Notificaciones (email/push) (`F3-FEAT`)
- Soft-delete global ypapelera (`F3-ARCH`)
- Internacionalización (`F3-FEAT`)

---

## 2. Stack y versiones (verificadas)

> Todas las versiones de esta tabla fueron comprobadas contra **Maven Central** (metadata),
> `endoflife.date`, la documentación oficial de Spring y la API de Docker Hub el **2026-10-21**.
> No hay versiones inventadas.

### 2.1 Tabla de versiones

| Componente | Versión fijada | Fuente / nota |
|---|---|---|
| Java | **21 (LTS)** | Spring Boot 4.1 admite Java 17–26. Homebrew ofrece `openjdk@21` (21.0.13). Java 21 es LTS y el punto de equilibrio más seguro. Java 25 (LTS) es alternativa válida. |
| Spring Boot | **4.1.1** | Última estable. Publicada 2026-06-30. Soporte OSS hasta **2027-07-31**, extendido hasta 2028-07-31. |
| Maven | **3.9.x** | Spring Boot 4.1 exige Maven ≥ 3.6.3. Se usará Maven Wrapper (3.9.16) para reproducibilidad. |
| Spring Framework | 7.0.9 | Gestionado por el BOM de Spring Boot 4.1.1. |
| Spring Security | 7.1.1 | Gestionado por el BOM. |
| Hibernate | 7.4.5.Final | Gestionado por el BOM. |
| PostgreSQL (servidor) | **18** (imagen `postgres:18-alpine`) | 18 es GA desde 2025-09-25, EOL 2030-11-14. Docker Hub expone `18` y `18-alpine`. |
| Driver JDBC PostgreSQL | **42.7.13** | Gestionado por `spring-boot-dependencies:4.1.1`. |
| Flyway | **12.4.0** | Gestionado por el BOM. Requiere el módulo extra `org.flywaydb:flyway-database-postgresql:12.4.0` (existe y devuelve HTTP 200). |
| Testcontainers | **2.0.5** | Gestionado por el BOM. ⚠️ En Testcontainers **2.x los módulos se renombraron**: `org.testcontainers:testcontainers-postgresql` y `org.testcontainers:testcontainers-junit-jupiter`. Los nombres clásicos `postgresql` / `junit-jupiter` de 1.x devuelven **404**. |
| springdoc-openapi | **3.1.1** | `springdoc-openapi-starter-webmvc-ui`. Compatible con Spring Boot 4.0/4.1. En Spring Boot 4 el artifact clásico `springdoc-openapi-ui` **no** sirve. |
| jjwt | **0.13.0** | `jjwt-api` / `jjwt-impl` / `jjwt-jackson`. No está gestionado por el BOM: fijar versión explícita. |
| Spring Modulith | **2.1.1** *(opcional)* | Compilado contra Spring Boot 4.1. Aporta verificación de límites de módulo. Ver ADR-0001. |

### 2.2 Trampas de Spring Boot 4 que el Agente 2 debe conocer

Spring Boot 4 modularizó el framework. Estas son **cambios reales** documentados en la guía de
migración oficial, no posibilidades hipotéticas:

| En Spring Boot 3 | En Spring Boot 4 | Nota |
|---|---|---|
| `spring-boot-starter-web` | **`spring-boot-starter-webmvc`** | El antiguo queda *deprecado*, no eliminado |
| `spring-boot-starter-json` | **`spring-boot-starter-jackson`** | Jackson 3 (`tools.jackson.core`) |
| `spring-boot-starter-test` | `spring-boot-starter-<tech>-test` | Cada starter de test trae el de test genérico por transitivity |
| `@MockBean` / `@SpyBean` | **`@MockitoBean` / `@MockitoSpyBean`** | Los anteriores fueron eliminados |
| `flyway-core` pelado | **`spring-boot-starter-flyway`** | Hay que cambiar el starter |
| `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` | Renombrado |

Además:
- Spring Security 7 deprecó `AccessDeniedException` en favor de `AuthorizationDeniedException`.
  Hay que decidir y **probar** cómo se traduce a 403 el error de método-seguridad (ver R-06 en §5.4).
- El `spring-boot-starter-webmvc-test` puede requerir `@AutoConfigureTestRestTemplate` para
  auto-configurar `TestRestTemplate` en Spring Boot 4.

### 2.3 Dependencias finales del `pom.xml` (propuesta)

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.1.1</version>
</parent>

<properties>
  <java.version>21</java.version>
  <springdoc.version>3.1.1</springdoc.version>
  <jjwt.version>0.13.0</jjwt.version>
</properties>

<dependencies>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-flyway</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
  <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
  <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
  <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>${springdoc.version}</version></dependency>
  <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>${jjwt.version}</version></dependency>
  <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>${jjwt.version}</version><scope>runtime</scope></dependency>
  <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>${jjwt.version}</version><scope>runtime</scope></dependency>
</dependencies>

<!-- test -->
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc-test</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa-test</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security-test</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-postgresql</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-junit-jupiter</artifactId><scope>test</scope></dependency>
```

> `jjwt-jackson` usa **Jackson 2** (`com.fasterxml.jackson`), mientras Spring Boot 4 usa **Jackson 3**
> (`tools.jackson`). Son librerías de serialización distintas que conviven en el classpath; el
> `jjwt-jackson` es solo para que jjwt sepa construir JSON. Es una combinación conocida, pero
> **debe verificarse en el arranque** (ver R-04 en §6).

### 2.4 Alternativa considerada y descartada

**Spring Boot 3.5.16** — Última de la línea 3.x; su soporte OSS **terminó el 2026-06-30**
(solo soporte comercial extendido hasta 2032-06-30). Se descarta porque:
Spring Boot 4.1.x es la generación vigente con un año y medio de soporte OSS por delante, y porque
empezar en greenfield permite adoptar los nombres de starter nuevos desde el día uno.
Documentado en ADR-0002.

---

## 3. Estructura de paquetes y módulos

### 3.1 Criterio: *package-by-feature* (corte vertical), no *package-by-layer* global

**Decisión (PROPUESTO):** se organiza por **bounded context / feature**, con capas **dentro**
de cada feature. Se descarta la estructura global por capas
(`controller/`, `service/`, `repository/`…, todo al nivel raíz) porque:

1. Un cambio de negocio (p. ej. "una tarea solo puede asignarse a un miembro del proyecto")
   toca un único paquete en lugar de 6 paquetes de nivel raíz → diffs más pequeños y revisables.
2. La dependencia entre features es visible en la estructura de paquetes → **es una arquitectura
   modular real**, no solo una convención de nombres.
3. Permite que Spring Modulith (opcional, ADR-0001) verifique los límites automáticamente.

Los paquetes `controller`/`service`/`repository`/`entity`/`dto`/`config`/`exception` que pide el
encargo **se conservan como sub-paquetes**, renombrados a un vocabulario más estándar en Spring:

| Encargo | Propuesta | Contenido |
|---|---|---|
| `controller` | `api` | `@RestController`, DTOs de entrada/salida, assembler/mapeador |
| `service` | `service` | Casos de uso, `@Transactional`, autorización |
| `repository` | `repository` | Interfaces Spring Data |
| `entity/model` | `domain` | Entidades JPA, enums, value objects |
| `dto` | `api` (subpaquete `dto`) | `records` de request/response |
| `config` | `config` (raíz de `shared`) | `SecurityConfig`, `OpenApiConfig`, `JacksonConfig` |
| `exception` | `exception` (raíz de `shared`) | `@RestControllerAdvice`, excepciones de dominio |

### 3.2 Árbol de paquetes

```
com.devtrack
├── DevTrackApplication.java
│
├── shared/                              ── transversal, mínimo
│   ├── config/
│   │   ├── SecurityConfig.java          ── cadena de filtros, PasswordEncoder, CORS
│   │   ├── OpenApiConfig.java           ── metadatos OpenAPI, esquema Bearer
│   │   ├── JacksonConfig.java           ── convenciones de fecha/JSON
│   │   └── ClockConfig.java             ── Clock inyectable (testabilidad)
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java  ── @RestControllerAdvice único
│   │   ├── ResourceNotFoundException.java
│   │   ├── ConflictException.java
│   │   ├── UnprocessableEntityException.java
│   │   ├── InvalidStateTransitionException.java
│   │   └── ErrorCatalog.java            ── códigos de error estables
│   ├── persistence/
│   │   ├── BaseEntity.java              ── id + createdAt + updatedAt
│   │   └── AuditingConfig.java          ── @EnableJpaAuditing
│   ├── security/
│   │   ├── CurrentUser.java             ── @AuthenticationPrincipal
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── RestAuthenticationEntryPoint.java   ── 401 en ProblemDetail
│   │   ├── RestAccessDeniedHandler.java        ── 403 en ProblemDetail
│   │   └── ProjectAccessGuard.java      ── ★ autorización por membresía
│   └── web/
│       └── PageResponse.java            ── wrapper de paginación (no Page de Spring)
│
├── user/                                ── bounded context: identidad
│   ├── api/
│   │   ├── AuthController.java
│   │   ├── UserController.java
│   │   └── dto/
│   │       ├── RegisterRequest.java  LoginRequest.java  AuthResponse.java
│   │       └── UserResponse.java
│   ├── service/
│   │   ├── AuthService.java
│   │   └── UserService.java
│   ├── domain/
│   │   ├── User.java
│   │   └── PlatformRole.java            ── USER | ADMIN
│   └── repository/
│       └── UserRepository.java
│
├── project/                             ── bounded context: proyectos y membresía
│   ├── api/
│   │   ├── ProjectController.java
│   │   ├── ProjectMemberController.java
│   │   └── dto/
│   │       ├── CreateProjectRequest.java  UpdateProjectRequest.java
│   │       ├── ProjectResponse.java
│   │       ├── UpsertMemberRequest.java  UpdateMemberRoleRequest.java
│   │       └── ProjectMemberResponse.java
│   ├── service/
│   │   ├── ProjectService.java
│   │   └── ProjectMemberService.java
│   ├── domain/
│   │   ├── Project.java
│   │   ├── ProjectMember.java
│   │   └── ProjectRole.java             ── OWNER | EDITOR | VIEWER
│   └── repository/
│       ├── ProjectRepository.java
│       └── ProjectMemberRepository.java
│
└── task/                                ── bounded context: tareas
    ├── api/
    │   ├── TaskController.java
    │   └── dto/
    │       ├── CreateTaskRequest.java  UpdateTaskRequest.java
    │       ├── AssignTaskRequest.java
    │       └── TaskResponse.java
    ├── service/
    │   ├── TaskService.java
    │   └── TaskStatusPolicy.java        ── ★ máquina de estados
    ├── domain/
    │   ├── Task.java
    │   ├── TaskStatus.java              ── TODO | IN_PROGRESS | DONE | CANCELLED
    │   └── TaskPriority.java            ── LOW | MEDIUM | HIGH | URGENT
    └── repository/
        └── TaskRepository.java
```

### 3.3 Reglas de dependencia entre paquetes

```
api  ──►  service  ──►  domain
 │           │
 │           └────────►  repository
 └──────►  shared (solo config, exception, security, web)

Reglas:
  R-D1  `domain` no depende de nada del proyecto (ni de Spring Data).
  R-D2  `api` no contiene lógica de negocio; solo valida, delega y traduce.
  R-D3  `domain` NO importa `api` (ni al revés): la conversión entidad↔DTO
        ocurre en el assembler de `api`, no en la entidad.
  R-D4  `task` puede leer `project` (necesita validar que el assignee es miembro),
        pero `project` NO conoce `task`.
  R-D5  `user` no conoce `project` ni `task`.
```

`R-D4` es la única dependencia cruzada entre features y es **unidireccional**. Esa asimetría es
justificable: `task` necesita saber si un usuario es miembro, pero la membresía no necesita
saber nada de las tareas.

### 3.4 Por qué no arquitectura hexagonal completa

Spring Data JPA + repositorios Spring hace que la separación
`dominio ← puerto ← adaptador` sea **mayormente decorativa** en este tamaño de proyecto.
Lo que sí aporta valor real y verificable son: DTOs, no exponer entidades, transacciones en
la capa de servicio, autorización explícita y tests. Se documenta en ADR-0001.

---

## 4. Modelo de datos

### 4.1 Diagrama de relaciones

```
                 ┌──────────────┐
                 │    users     │
                 │──────────────│
                 │ id      PK   │
                 │ email   UQ   │
                 │ pwd_hash     │
                 │ full_name    │
                 │ platform_role│
                 │ enabled      │
                 │ created_at   │
                 │ updated_at   │
                 └──────┬───────┘
                        │
        ┌───────────────┼───────────────────┐
        │ 1:N           │ 1:N               │ 1:N
        │               │                   │
┌───────▼────────┐ ┌───▼──────────────┐ ┌──▼────────────────┐
│ project_members│ │    projects      │ │     tasks         │
│────────────────│ │──────────────────│ │───────────────────│
│ id        PK   │ │ id           PK  │ │ id            PK  │
│ project_id FK  │ │ name             │ │ project_id    FK  │
│ user_id    FK  │ │ description      │ │ title            │
│ role           │ │ owner_id     FK  │ │ description      │
│ added_at       │ │ archived         │ │ status           │
│ UQ(project,user)│ │ created_at       │ │ priority         │
└────────────────┘ │ updated_at       │ │ assignee_id  FK  │
                    └─────────┬────────┘ │ due_date         │
                              │          │ created_by   FK  │
                              │ 1:N      │ version         │
                              └─────────►│ created_at      │
                                         │ updated_at      │
                                         └─────────────────┘
```

Cardinalidades:
- `users 1—N project_members` (un usuario en muchos proyectos)
- `projects 1—N project_members`
- `projects 1—N tasks` (borrado en cascada)
- `users 1—N tasks` como `assignee_id` (`ON DELETE SET NULL`)
- `users 1—N tasks` como `created_by` (restricción: no se permite borrar el usuario)

### 4.2 Definición de tablas

#### `users`

| Columna | Tipo | Restricciones | Notas |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `email` | `VARCHAR(320)` | NOT NULL, **UNIQUE** | 320 = máx. RFC 5321. Se guarda en minúsculas (A-07) |
| `password_hash` | `VARCHAR(255)` | NOT NULL | BCrypt. **Nunca texto plano** |
| `full_name` | `VARCHAR(120)` | NOT NULL | |
| `platform_role` | `VARCHAR(20)` | NOT NULL, DEFAULT `'USER'`, CHECK (`USER`,`ADMIN`) | Rol **de plataforma**, distinto del rol de proyecto |
| `enabled` | `BOOLEAN` | NOT NULL, DEFAULT `TRUE` | Baja lógica, no borrado físico |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `now()` | |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `now()` | Actualizado por `@PreUpdate` |

Índices: `PK(id)`, `UQ(email)`.

#### `projects`

| Columna | Tipo | Restricciones | Notas |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `name` | `VARCHAR(120)` | NOT NULL | |
| `description` | `TEXT` | NULL | |
| `owner_id` | `BIGINT` | NOT NULL, FK → `users(id)` | |
| `archived` | `BOOLEAN` | NOT NULL, DEFAULT `FALSE` | Borrado lógico (borrar proyecto = archivar) |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | |

Índices: `PK(id)`, `idx_projects_owner (owner_id)`.
No se impone unicidad de `(owner_id, name)`: un owner puede legítima tener dos proyectos
con el mismo nombre.

#### `project_members`

| Columna | Tipo | Restricciones | Notas |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `project_id` | `BIGINT` | NOT NULL, FK → `projects(id)` **ON DELETE CASCADE** | |
| `user_id` | `BIGINT` | NOT NULL, FK → `users(id)` **ON DELETE CASCADE** | |
| `role` | `VARCHAR(20)` | NOT NULL, CHECK (`OWNER`,`EDITOR`,`VIEWER`) | |
| `added_at` | `TIMESTAMPTZ` | NOT NULL | |

Índices: `PK(id)`, **`UQ(project_id, user_id)`**, `idx_pm_user (user_id)` (para "mis proyectos").

> **Restricción de negocio (no SQL):** exactamente un miembro con rol `OWNER` por proyecto.
> Se garantiza en la capa de servicio, no con un `CHECK` de SQL, porque SQL estándar no
> puede expresar "exactamente uno" sin trigger. Riesgo accepted: ver R-05 (§6).

#### `tasks`

| Columna | Tipo | Restricciones | Notas |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `project_id` | `BIGINT` | NOT NULL, FK → `projects(id)` **ON DELETE CASCADE** | |
| `title` | `VARCHAR(200)` | NOT NULL | |
| `description` | `TEXT` | NULL | |
| `status` | `VARCHAR(20)` | NOT NULL, DEFAULT `'TODO'`, CHECK (`TODO`,`IN_PROGRESS`,`DONE`,`CANCELLED`) | |
| `priority` | `VARCHAR(20)` | NOT NULL, DEFAULT `'MEDIUM'`, CHECK (`LOW`,`MEDIUM`,`HIGH`,`URGENT`) | |
| `assignee_id` | `BIGINT` | NULL, FK → `users(id)` **ON DELETE SET NULL** | Asignar a un usuario borrado deja la tarea sin asignar, no la borra |
| `due_date` | `DATE` | NULL | Sin hora: es una fecha de vencimiento de jornada |
| `created_by` | `BIGINT` | NOT NULL, FK → `users(id)` | Sin cascada: no se puede borrar un usuario con historial |
| `version` | `BIGINT` | NOT NULL, DEFAULT `0` | **Locking optimista** (`@Version`) |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | |

Índices:
- `PK(id)`
- `idx_tasks_project_status (project_id, status)` — listado filtrado por estado
- `idx_tasks_project_assignee (project_id, assignee_id)` — listado por asignado
- `idx_tasks_project_due (project_id, due_date)` — listado por vencimiento

### 4.3 Mapeo JPA: reglas obligatorias

| Regla | Detalle |
|---|---|
| R-M1 | `@Enumerated(EnumType.STRING)` en todos los enums. Nunca `ORDINAL`. |
| R-M2 | Relaciones **unidireccionales** desde el lado propietario. `Task` referencia `Project` por `project_id`; `Project` **no** tiene `List<Task>`. Se evita el problema de serialización y el `MultipleBagFetchException`. |
| R-M3 | Las FKs se mapean con `@ManyToOne(fetch = LAZY)` + `@JoinColumn(name="...")`; **nunca** `@OneToMany` con `cascade` en el lado inverso. |
| R-M4 | Las entidades **no** implementan `Serializable` y **no** tienen `equals`/`hashCode` basados en todos los campos. `equals`/`hashCode` solo por `id`. |
| R-M5 | `spring.jpa.open-in-view=false`. Los servicios resuelven dentro de la transacción todo lo que necesita exponer. |
| R-M6 | `spring.jpa.hibernate.ddl-auto=validate`. El esquema lo crea **Flyway**, nunca Hibernate. |
| R-M7 | Las entidades usan `Instant` para timestamps y `LocalDate` para `due_date`. Timestamps en UTC. |
| R-M8 | `@Version private Long version` en `Task`. others no lo necesitan (no se editan concurrentemente). |
| R-M9 | `BaseEntity` (id + createdAt + updatedAt) con `@MappedSuperclass`. `Task.createdBy` **no** va en `BaseEntity` (solo `Task` tiene creador). |
| R-M10 | Sin Lombok (salvo decisión explícita posterior). Records para DTOs, clases normales para entidades. Razonamiento: Lombok + Java 21 + IDEs heterogéneos = fricción; el proyecto es pequeño y legible sin él. |

### 4.4 Máquina de estados de tareas

Regla de negocio central, implementada en `TaskStatusPolicy` y cubierta por tests unitarios:

```
            ┌──────────────────────────────┐
            ▼                              │
   ┌─────────────┐   ┌───────────────┐   ┌───┴───────┐
   │    TODO     │──►│ IN_PROGRESS   │──►│   DONE    │
   │  (inicial)  │   │               │   │           │
   └─────┬───────┘   └───────┬───────┘   └─────┬─────┘
         │                   │                 │
         │                   ▼                 ▼
         │            ┌───────────────┐  ┌──────────────┐
         └───────────►│  CANCELLED    │  │  CANCELLED   │
                      │               │  │              │
                      └───────┬───────┘  └──────────────┘
                              │
                              │  (reabrir → TODO)
                              ▼
                          ┌───────┐
                          │ TODO  │
                          └───────┘
```

**Transiciones permitidas**

| Desde \ Hacia | TODO | IN_PROGRESS | DONE | CANCELLED |
|---|---|---|---|---|
| **TODO** | — (no-op) | ✅ | ✅ | ✅ |
| **IN_PROGRESS** | ✅ | — (no-op) | ✅ | ✅ |
| **DONE** | ❌ **prohibida** | ✅ (reabrir) | — (no-op) | ✅ |
| **CANCELLED** | ✅ (reabrir) | ❌ **prohibida** | ❌ **prohibida** | — (no-op) |

- Las transiciones prohibidas devuelven **422 Unprocessable Entity**.
- Las transiciones no-op (mismo estado) devuelven **200** y no modifican `updated_at`.
- `CANCELLED` solo se puede reabrir a `TODO`: obliga a pasar por el backlog, no se puede
  "resucitar" una tarea cancelada directamente a "en curso".

---

## 5. Estrategia de seguridad y autorización

### 5.1 Modelo de identidad

- **Autenticación:** email + password. Password almacenado con **BCrypt** (`BCryptPasswordEncoder`,
  coste 10 por defecto, configurable por propiedad).
- **Token:** **JWT HS256**, stateless, `Bearer` en cabecera `Authorization`.
  - Claims: `sub` = user id, `email`, `role` = rol de plataforma, `iss` = `devtrack`,
    `iat`, `exp`.
  - TTL de acceso: **15 minutos**, configurable (`devtrack.jwt.access-token-ttl`).
  - **Sin refresh token en la v1** (A-05). Se añade en `F2-AUTH`.
- **Secreto:** variable de entorno `DEVTRACK_JWT_SECRET`, mínimo 32 bytes.
  **Nunca en el repositorio.** `.env.example` documenta la variable; `.gitignore` excluye `.env`.
  En arranque, si el secreto falta o es demasiado corto → **fallo rápido** (fail-fast),
  no arranque con un secreto por defecto inseguro.

### 5.2 Cadena de filtros

```
SecurityFilterChain (stateless, CSRF off, CORS configurado)
  ├─ JwtAuthenticationFilter      → valida el Bearer, crea Authentication en el SecurityContext
  ├─ ExceptionTranslationFilter
  │    ├─ RestAuthenticationEntryPoint  → 401 + ProblemDetail
  │    └─ RestAccessDeniedHandler       → 403 + ProblemDetail
  └─ AuthorizationFilter
```

**Rutas públicas:** `POST /api/v1/auth/register`, `POST /api/v1/auth/login`,
`GET /actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`.
**Todo lo demás:** requiere token válido.

> **CSRF desactivado — justificación:** la API es stateless y no usa cookies ni sesiones de
> servidor; el token viaja en la cabecera `Authorization`. Un atacante externo no puede forzar
> al navegador a adjuntar esa cabecera. Si en `F2-AUTH` se introduce el token en cookies
> `HttpOnly`, **CSRF debe volver a activarse**. Queda anotado en ADR-0003.

### 5.3 Autorización: modelo por membresía

**Idea central:** la fuente de verdad de la autorización es la tabla `project_members`.
Conocer el `project_id` o el `task_id` **no** concede acceso alguno.

```
                 ┌─────────────────────────────┐
  request ──────►│  ProjectAccessGuard         │
                 │  requireRead(projectId)      │
                 │  requireEdit(projectId)      │
                 │  requireManage(projectId)    │
                 └──────────────┬──────────────┘
                                │
                 consulta project_members por (project_id, user_id)
                                │
        ┌───────────────────────┼────────────────────────┐
        ▼                       ▼                        ▼
   sin fila                fila + rol insuficiente    fila + rol suficiente
   (no es miembro)         (p. ej. VIEWER quiere      → continúa
        │                   editar)
        ▼                       ▼
      404 Not Found          403 Forbidden
   (no revelar que el     (ya sabe que existe:
    recurso existe)        es miembro)
```

**Matriz de permisos por rol de proyecto**

| Acción | VIEWER | EDITOR | OWNER | ADMIN (plataforma) | No miembro |
|---|:---:|:---:|:---:|:---:|:---:|
| Leer proyecto | ✅ | ✅ | ✅ | ✅ | 404 |
| Listar miembros | ✅ | ✅ | ✅ | ✅ | 404 |
| Listar tareas | ✅ | ✅ | ✅ | ✅ | 404 |
| Leer tarea | ✅ | ✅ | ✅ | ✅ | 404 |
| Crear tarea | ❌ | ✅ | ✅ | ✅ | 404 |
| Editar tarea (campos) | ❌ | ✅ | ✅ | ✅ | 404 |
| Cambiar estado | ❌ | ✅ | ✅ | ✅ | 404 |
| Asignar tarea | ❌ | ✅ | ✅ | ✅ | 404 |
| **Borrar tarea** | ❌ | ⚠️ solo si la creó | ✅ | ✅ | 404 |
| Editar proyecto | ❌ | ❌ | ✅ | ✅ | 404 |
| Archivar proyecto | ❌ | ❌ | ✅ | ✅ | 404 |
| Añadir/quitar miembro | ❌ | ❌ | ✅ | ✅ | 404 |
| Cambiar rol de miembro | ❌ | ❌ | ✅ | ✅ | 404 |
| Transferir ownership | ❌ | ❌ | ✅ | ✅ | 404 |

> **ADMIN de plataforma** puede operar sobre cualquier proyecto (soporte/admin del sistema),
> pero **no** ve ni opera proyectos en los que no es miembro mediante las rutas normales
> (`GET /projects` lista solo proyectos donde es miembro). Esto evita que un admin filtre
> datos por accidente y mantiene el modelo de "membresía" coherente.

**Por qué un `ProjectAccessGuard` explícito y no `@PreAuthorize`:**
`@PreAuthorize("hasRole('OWNER')")` no puede expresar *"el llamador es OWNER **del proyecto X**"*,
porque el proyecto es un parámetro del método. Se necesitaría un bean de spel como
`@projectAuth.canManage(#projectId)`. Un `ProjectAccessGuard` explícito:
- es **más legible** para quien lea el código,
- es **trivialmente testeable** con unit tests (un mock del repositorio y cuatro asserts),
- centraliza la política 404-vs-403 en un solo sitio,
- no depende de la semántica de las expresiones SpEL ni de la versión de Spring Security.

`@PreAuthorize` se reserva para lo que sí encaja: `@PreAuthorize("hasRole('ADMIN')")` en
administración de usuarios.

### 5.4 Manejo de errores de autorización (riesgo conocido)

Spring Security 7 deprecó `AccessDeniedException` en favor de `AuthorizationDeniedException`, y
las excepciones lanzadas **en la cadena de filtros** (por ejemplo, un token ausente o inválido)
**no** pasan por el `@RestControllerAdvice`. Por eso se requieren **dos** mecanismos:

1. `RestAuthenticationEntryPoint` → 401 con `ProblemDetail` (fallo en filtros).
2. `RestAccessDeniedHandler` → 403 con `ProblemDetail` (fallo en filtros).
3. `@ExceptionHandler` en `GlobalExceptionHandler` para `AuthorizationDeniedException`
   (fallo de seguridad a nivel de método, que **sí** atraviesa el `DispatcherServlet`).

Esto **debe verificarse con tests de integración** que comprueben el *cuerpo* de la respuesta,
no solo el código HTTP. Es la tarea `T-13` y el riesgo `R-06` (§6).

### 5.5 Qué queda para una fase posterior

| Ítem | Fase |
|---|---|
| Refresh tokens + rotación + revocación (blacklist) | `F2-AUTH` |
| OAuth2 / OIDC como proveedor externo | `F2-AUTH` |
| Verificación de email, recuperación de contraseña | `F2-AUTH` |
| MFA / TOTP | `F2-SEC` |
| Rate limiting por IP y por usuario | `F2-SEC` |
| Cabeceras de seguridad (CSP, HSTS) en el proxy | `F2-SEC` |
| Logs de auditoría de accesos | `F2-SEC` |
| Rotación de claves (RS256 → RSA, KMS) | `F2-SEC` |

---

## 6. Riesgos identificados

| ID | Riesgo | Impacto | Prob. | Mitigación | Tarea |
|---|---|---|---|---|---|
| R-01 | Fuga de información: un usuario descubre que un proyecto existe por el código de error | Media | Media | 404 para no-miembros; 403 solo para miembros con rol insuficiente (§5.3) | T-11 |
| R-02 | **IDOR**: conocer un `projectId`/`taskId` permite leer o modificar recursos ajenos | **Alta** | Media | `ProjectAccessGuard` obligatorio en **todos** los servicios; test que intente acceder con token de otro usuario | T-11, T-16 |
| R-03 | Contraseñas en texto plano o secretos en el repo | **Alta** | Baja | BCrypt obligatorio; `.env` en `.gitignore`; `.env.example` sin valores; revisión en el code review | T-08, T-20 |
| R-04 | Token JWT con secreto débil o por defecto | Alta | Media | Fail-fast en arranque si `DEVTRACK_JWT_SECRET` falta o es < 32 bytes | T-08 |
| R-05 | Pérdida de consistency: dos miembros `OWNER` o ninguno | Media | Baja | Servicio transaccional + `UQ(project_id,user_id)` + test de invariante | T-10 |
| R-06 | El error de seguridad a nivel de método no produce 403 con `ProblemDetail` | Media | Media | Implementar los 3 mecanismos de §5.4 y **probar el cuerpo** de la respuesta | T-13 |
| R-07 | **Lost update**: dos editan la misma tarea y se pisan | Media | Alta | `@Version` (locking optimista) → `OptimisticLockingFailureException` → 409 | T-15 |
| R-08 | `LazyInitializationException` por `open-in-view=false` | Media | Alta | Servicios transaccionales que resuelven todo lo necesario dentro de la transacción; `JOIN FETCH` donde aplique | T-09 |
| R-09 | Divergencia SQL entre H2 (tests) y PostgreSQL (producción) | Media | Media | Testcontainers con PostgreSQL real; H2 prohibido en tests de integración | T-04, T-05 |
| R-10 | Incompatibilidad de `jjwt-jackson` (Jackson 2) con Jackson 3 de Spring Boot 4 | Media | Media | Test de arranque que valida un token real end-to-end desde el primer commit | T-08 |
| R-11 | Deriva del esquema entre entidad JPA y migración Flyway | Media | Alta | `ddl-auto=validate` → la app **no arranca** si el esquema no coincide | T-04 |
| R-12 | Colisión `jjwt-jackson` vs Jackson 3 produce errores en runtime al serializar el JWT | Media | Baja | Ver R-10 | T-08 |
| R-13 | Under-testing del `ProjectAccessGuard` (la pieza más crítica) | **Alta** | Media | Mínimo 8 tests de matriz de permisos (§5.3), uno por celda crítica | T-11, T-16 |
| R-14 | Fuga de datos en logs (tokens, contraseñas) | Media | Media | No loggear el `Authorization` ni los cuerpos de login; revisar el nivel `DEBUG` de Spring Security | T-20 |
| R-15 | MitM / inyección SQL | Media | Baja | Driver JDBC parametrizado por defecto; TLS gestionado por el proxy en producción | — |
| R-16 | Abandono oORAGE desactualizado: 3.5.x ya no tiene soporte OSS | Media | Alta | Fijar Boot 4.1.1 y documentar la fecha de EOL (2027-07-31) | ADR-0002 |

> Nota: R-16 se corrige en parte con **Spring Boot 4.1.1** (EOL OSS 2027-07-31, extendido
> 2028-07-31) frente a 3.5.16 (EOL OSS 2026-06-30). La decisión queda registrada en ADR-0002.

---

## 7. Plan de sprints

Ordenado por **valor entregado** y **dependencias**. Cada sprint deja el proyecto en estado
ejecutable y verificable.

```
 S0 ──────────► S1 ──────────► S2 ──────────► S3 ──────────► S4
 Cimientos       Identidad      Proyectos      Tareas         Pulido
 (infra)         (auth+JWT)     y miembros     (dominio)      (calidad)
                                 ▲                              ▲
                                 └──── T-14 (tareas) ───────────┘
```

### Sprint 0 — Cimientos (toolchain y esqueleto) · T-00 … T-05

**Objetivo:** un proyecto que compila, arranca, se conecta a PostgreSQL y tiene health check.
**Valor:** desbloquea todo lo demás. Sin esto, ninguna otra tarea es verificable.

- T-00 Toolchain local (JDK 21, Maven Wrapper, Docker Desktop)
- T-01 Estructura Maven + `pom.xml` + aplicación mínima + `.gitignore`
- T-02 Docker Compose con PostgreSQL 18 + healthcheck + volumen
- T-03 Perfil de configuración (`application.yml`, `application-test.yml`) y variables de entorno
- T-04 Flyway: migración `V1__baseline.sql` + `ddl-auto=validate`
- T-05 Base de pruebas: Testcontainers PostgreSQL + `AbstractIntegrationTest`

**Salida del sprint:** `docker compose up -d && ./mvnw spring-boot:run` → `/actuator/health` = UP.
`./mvnw test` verde.

### Sprint 1 — Identidad y acceso · T-06 … T-09

**Objetivo:** registro, login, JWT y `GET /users/me` funcionando de punta a punta.
**Valor:** desbloquea todo el modelo de autorización posterior.
**Depende de:** S0.

- T-06 Entidad `User` + `UserRepository` + migración `V2`
- T-07 `GlobalExceptionHandler` + `ProblemDetail` + catálogo de errores
- T-08 Registro y login con BCrypt + emisión/validación de JWT + `SecurityFilterChain`
- T-09 `GET /users/me` + pruebas de integración del flujo de auth

**Salida del sprint:** `register → login → me` con token Bearer, verificado con tests.

### Sprint 2 — Proyectos y membresía · T-10 … T-13

**Objetivo:** CRUD de proyectos + gestión de miembros + **la pieza crítica**: el guard de autorización.
**Valor:** es la Demonstrate de seguridad del proyecto.
**Depende de:** S1.

- T-10 Entidades `Project` / `ProjectMember` + repositorios + migración `V3`
- T-11 `ProjectAccessGuard` + `ProjectService` + `ProjectMemberService`
- T-12 Controllers de proyecto y de miembros + DTOs
- T-13 Mecanismos 401/403 (`EntryPoint`, `AccessDeniedHandler`, advice) + tests del cuerpo de error

**Salida del sprint:** matriz de permisos (§5.3) cubierta por tests, incluidos los casos 404/403.

### Sprint 3 — Tareas · T-14 … T-16

**Objetivo:** el dominio de tareas completo, con máquina de estados y locking optimista.
**Valor:** la funcionalidad central del producto.
**Depende de:** S2.

- T-14 Entidad `Task` + `TaskRepository` + migración `V4` + `TaskStatusPolicy`
- T-15 `TaskService` + controller + DTOs + filtros + paginación
- T-16 Locking optimista + transiciones de estado + tests de la matriz de transiciones

**Salida del sprint:** CRUD de tareas anidado, filtros por estado/asignado, transiciones
validadas y `409` ante conflicto de versión.

### Sprint 4 — Calidad y entrega · T-17 … T-21

**Objetivo:** que un tercero pueda clonar, levantar y entender el proyecto en 5 minutos.
**Valor:** es lo que un reclutador evalúa primero.

- T-17 OpenAPI: metadatos, esquema Bearer, descripciones, ejemplos
- T-18 Dockerfile multi-stage + servicio `api` en Docker Compose
- T-19 CI en GitHub Actions (build + test + verify)
- T-20 Endurecimiento: rate limit básico, cabeceras, revisión de logs, `.env.example`
- T-21 Documentación: README, `docs/architecture.md`, `docs/api-plan.md`, ADRs finales

**Salida del sprint:** `git clone && docker compose up` → API documentada y testeada.

---

## 8. Estrategia de pruebas

| Nivel | Herramienta | Qué cubre | Objetivo de cobertura |
|---|---|---|---|
| Unitario | JUnit 5 + Mockito | Servicios, `TaskStatusPolicy`, `ProjectAccessGuard`, validadores | Ramas de negocio al 100 % |
| Integración | `@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers PostgreSQL | Flujos HTTP completos, migraciones Flyway, restricciones de BD | Endpoints críticos al 100 % |
| Seguridad | Integración con tokens reales | Matriz 404/403, IDOR, token inválido/ausente/expirado | Toda la matriz §5.3 |
| Contrato | `MockMvc` + `jsonPath` sobre DTOs | Forma de las respuestas y de los errores | Todos los endpoints |

**Reglas**

1. **Nada se aprueba sin evidencia.** Una tarea no está completa hasta que existe un test que
   falla sin el cambio y pasa con él. El Agente 2 debe adjuntar la salida de `./mvnw test`.
2. **H2 está prohibido** en tests de integración (R-09).
3. Los tests de integración usan `@ServiceConnection` de Spring Boot para inyectar el
   `DataSource` del contenedor automáticamente.
4. Cada test de integración que necesite datos usa una base limpia por clase
   (`@Transactional` o `TRUNCATE`); no se depende del orden de ejecución.
5. Los tests de la matriz de permisos se parametrizan (`@ParameterizedTest`) para que la
   matriz sea legible y exhaustiva.

---

## 9. Configuración propuesta

```yaml
# application.yml
spring:
  application:
    name: devtrack
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/devtrack}
    username: ${DB_USERNAME:devtrack}
    password: ${DB_PASSWORD:devtrack}
  jpa:
    open-in-view: false          # R-08
    hibernate:
      ddl-auto: validate         # R-11 — el esquema lo manda Flyway
    properties:
      hibernate:
        jdbc.time_zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration
  jackson:
    default-property-inclusion: non_null

server:
  port: ${SERVER_PORT:8080}

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true

devtrack:
  jwt:
    secret: ${DEVTRACK_JWT_SECRET}       # sin valor por defecto → fail-fast
    issuer: devtrack
    access-token-ttl: PT15M
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000}

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

**Variables de entorno requeridas**

| Variable | Requerida | Valor por defecto | Notas |
|---|---|---|---|
| `DB_URL` | No | `jdbc:postgresql://localhost:5432/devtrack` | Solo en local |
| `DB_USERNAME` | No | `devtrack` | |
| `DB_PASSWORD` | No | `devtrack` | Solo local. En producción, obligatoria |
| `DEVTRACK_JWT_SECRET` | **Sí** | — | Mín. 32 bytes. Fail-fast si falta |
| `SERVER_PORT` | No | `8080` | |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:3000` | Lista separada por comas |

---

## 10. Criterios de calidad de la entrega (Definition of Done global)

- [ ] `./mvnw clean verify` termina en `BUILD SUCCESS`, sin tests omitidos (`skipTests`).
- [ ] No hay entidades JPA en las respuestas públicas (verificable leyendo los DTOs).
- [ ] No hay `System.out.println`; logging con SLF4J y niveles correctos.
- [ ] No hay secretos ni `.env` versionado.
- [ ] `docker compose up` levanta la API y PostgreSQL y `/actuator/health` responde `UP`.
- [ ] Swagger UI accesible en `/swagger-ui.html` con el esquema Bearer documentado.
- [ ] Todo endpoint no público responde 401 sin token, y un usuario sin permiso recibe
      404 (no miembro) o 403 (rol insuficiente).
- [ ] Cada PR incluye su salida de `./mvnw verify`.
- [ ] Las decisiones relevantes están en `docs/adr/`.

---

## 11. Preguntas abiertas (requieren respuesta del usuario)

| ID | Pregunta | Impacto si no se responde |
|---|---|---|
| Q-01 | ¿Confirmas **Spring Boot 4.1.1 + Java 21**? (Alternativa: Boot 3.5.16 sin soporte OSS) | Bloqueante para T-01 |
| Q-02 | ¿Confirmas el modelo de roles `OWNER/EDITOR/VIEWER` por proyecto con `ADMIN` de plataforma? | Bloqueante para T-10 |
| Q-03 | ¿Se acepta JWT sin refresh token en la v1, o entra refresh en el alcance? | Afecta T-08 y el sprint 1 |
| Q-04 | ¿El owner puede transferir la propiedad del proyecto? (Lo he incluido en la matriz) | Afecta T-11 |
| Q-05 | ¿Se usa Spring Modulith (2.1.1) para verificar límites de módulo, o se deja solo como convención? | Afecta el `pom.xml` de T-01 |
| Q-06 | ¿El repositorio Git debe ser público (portafolio) y en qué cuenta de GitHub? | Afecta T-19 |
| Q-07 | ¿Instalar el toolchain (JDK 21, Maven Wrapper, Docker Desktop) es tarea mía o del usuario? | Bloqueante para todo |

---

## 12. Trazabilidad requisito → sprint

| Requisito | Sprint | Tareas |
|---|---|---|
| R-01 API REST | S2, S3 | T-12, T-15 |
| R-02 Usuarios + auth | S1 | T-06 … T-09 |
| R-03 Proyectos + miembros | S2 | T-10 … T-13 |
| R-04 Tareas | S3 | T-14 … T-16 |
| R-05 Monolito modular | S0 | T-01, T-05 (+ ADR-0001) |
| R-06 Persistencia + migraciones | S0 | T-02, T-04 |
| R-07 Autorización en servidor | S1, S2 | T-08, T-11, T-13 |
| R-08 Pruebas | S0–S4 | T-05, T-09, T-13, T-16, T-19 |
| R-09 OpenAPI | S4 | T-17 |
| R-10 Docker Compose | S0, S4 | T-02, T-18 |
| R-11 Git + CI | S0, S4 | T-01, T-19 |
| R-12 Comentarios / historial / métricas | **Fase 2** | — |
