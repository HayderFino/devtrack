# DevTrack

[![CI](https://github.com/HayderFino/devtrack/actions/workflows/ci.yml/badge.svg)](https://github.com/HayderFino/devtrack/actions/workflows/ci.yml)

Aplicación full stack para la gestión de **proyectos**, **miembros** y **tareas**. API REST
en Java y Spring Boot, con autorización por rol en cada proyecto, persistencia relacional
versionada con Flyway y una suite de pruebas de integración contra PostgreSQL real. Interfaz
web en React, TypeScript y Vite.

**Estado:** backend v1 completo con 227 pruebas en verde y frontend v1 operativo.

---

## Índice

- [Modelo de dominio](#modelo-de-dominio)
- [Stack](#stack)
- [Requisitos previos](#requisitos-previos)
- [Arranque local](#arranque-local)
- [Frontend](#frontend)
- [Pruebas](#pruebas)
- [Autenticación y uso](#autenticación-y-uso)
- [Autorización](#autorización)
- [Contrato de errores](#contrato-de-errores)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Decisiones de diseño](#decisiones-de-diseño)
- [Documentación](#documentación)
- [Qué no incluye esta versión](#qué-no-incluye-esta-versión)

---

## Modelo de dominio

```
users ──┬──< project_members >── projects ──< tasks
        │                              ▲            │
        └──────────────────────────────┴────────────┘
                    (assignee_id, created_by)
```

- Un **proyecto** tiene **miembros**, y cada miembro tiene un rol dentro de él:
  `OWNER`, `EDITOR` o `VIEWER`.
- Un **usuario** puede pertenecer a muchos proyectos.
- Una **tarea** pertenece a un proyecto y tiene título, descripción, estado, prioridad,
  fecha límite y asignado. El asignado **debe ser miembro del proyecto**.

La **fuente de verdad de la autorización es la tabla `project_members`**: conocer el
identificador de un proyecto o de una tarea no concede ningún acceso.

## Stack

| Componente | Versión |
|---|---|
| Java | 21 (LTS) |
| Spring Boot | 4.1.1 |
| Spring Framework / Security | 7.0.9 / 7.1.1 (gestionados por Boot) |
| Spring Data JPA (Hibernate) | 7.0.9 / 7.4.5 (gestionados por Boot) |
| PostgreSQL | 18 (`postgres:18-alpine`) |
| Flyway | 12.4.0 (gestionado por Boot) |
| Testcontainers | 2.0.5 (gestionado por Boot) |
| springdoc-openapi | 3.1.1 |
| jjwt | 0.13.0 |
| Maven | 3.9.16 (Maven Wrapper) |
| Docker Engine / Compose | 29.9.2 / 5.6.1 |
| React / React Router | 19.3 / 7.18 |
| TypeScript / Vite | 7.0 / 8.3 |
| TanStack Query | 5.104 |

Detalle y alternativas descartadas: [`ADR-0002`](docs/adr/0002-stack-y-versiones.md).

## Requisitos previos

| Herramienta | Versión | Notas |
|---|---|---|
| JDK | 21 | `java -version` debe mostrar `21.x` |
| Docker | con Compose v2 | Docker Desktop o Colima |
| Maven | — | No hace falta: se usa `./mvnw` |
| Node.js | 20.19+ o 22.12+ | Necesario solo para el frontend |
| npm | 10+ | Incluido con Node.js |

**Con Colima** (alternativa a Docker Desktop en macOS):

```bash
colima start
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
```

Colima no permite montar el socket de Docker en un contenedor, que es lo que necesita
*Ryuk* (el recolector de contenedores de Testcontainers). Por eso hay que desactivar
Ryuk en este entorno:

```bash
export TESTCONTAINERS_RYUK_DISABLED=true
```

Los contenedores los limpia Testcontainers al cerrar el contexto de Spring, de modo que no
se acumulan. Con Docker Desktop no hace falta ninguna de las dos variables.

## Arranque local

```bash
# 1. Configurar el entorno
cp .env.example .env
#    edita DEVTRACK_JWT_SECRET en .env: minimo 32 caracteres

# 2. Levantar PostgreSQL
docker compose up -d postgres

# 3. Arrancar la API (migra el esquema con Flyway al arrancar)
./mvnw spring-boot:run

# 4. Comprobar que esta sana
curl http://localhost:8080/actuator/health   # {"status":"UP", ...}

# 5. Abrir la documentacion interactiva
open http://localhost:8080/swagger-ui.html
```

Con Docker en vez de Maven:

```bash
docker compose --profile api up -d --build
```

## Frontend

La SPA vive en [`web/`](web/) y se sirve en **http://localhost:3000**. Vite redirige
`/api` al backend en `:8080`, por lo que no hace falta configurar CORS en desarrollo.

```bash
# Con la API ya levantada
cd web
npm install
npm run dev        # http://localhost:3000
```

Usa **`localhost`**, no `127.0.0.1`: el origen permitido por defecto en la API es
`http://localhost:3000`.

```bash
npm run build      # typecheck + bundle de produccion en web/dist
npm run preview    # sirve el bundle compilado
```

Incluye registro e inicio de sesión, listado y gestión de proyectos, tablero de tareas por
estado con filtros, asignación, prioridades y fechas límite, y administración de miembros
para el propietario. El token JWT se guarda en `sessionStorage` y se descarta al cerrar la
pestaña o al recibir un `401`.

## Pruebas

```bash
./mvnw test        # suite completa
./mvnw verify      # suite completa + verificacion
```

Los tests de integración levantan **PostgreSQL 18 real** con Testcontainers: no se usa
H2, para que el dialecto SQL sea el mismo que en producción. Requieren Docker en
ejecución.

| Suite | Qué cubre |
|---|---|
| `AuthControllerIntegrationTest` | Registro, login, validación de tokens, enumeración de cuentas |
| `DevTrackPropertiesTest` | *Fail-fast* del secreto JWT |
| `ProjectControllerIntegrationTest` | CRUD de proyectos, 404 indistinguible, archivado |
| `ProjectMemberControllerIntegrationTest` | Membresía, roles, transferencia de propiedad |
| `ProjectAuthorizationMatrixTest` | **Matriz completa rol × operación** (138 casos) |
| `TaskControllerIntegrationTest` | CRUD de tareas, filtros, transiciones, asignación |
| `TaskStatusPolicyTest` | Los 16 pares de la máquina de estados |
| `TaskConcurrencyTest` | Conflicto de *locking* optimista (409) |

## Autenticación y uso

La API es **stateless**: se autentica con un token JWT en la cabecera
`Authorization: Bearer <token>`, válido 15 minutos.

```bash
# Registrar
curl -sX POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@devtrack.dev","password":"Str0ng-Passphrase!","fullName":"Ada Lovelace"}'

# Iniciar sesión
TOKEN=$(curl -sX POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@devtrack.dev","password":"Str0ng-Passphrase!"}' | jq -r .accessToken)

# Crear un proyecto (el creator se convierte en OWNER)
curl -sX POST http://localhost:8080/api/v1/projects \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"DevTrack v1","description":"API de gestión de proyectos"}'

# Crear una tarea
curl -sX POST http://localhost:8080/api/v1/projects/1/tasks \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Primera tarea","priority":"HIGH","dueDate":"2026-12-31"}'
```

## Autorización

El rol se define **dentro de cada proyecto**, no de forma global:

| Operación | VIEWER | EDITOR | OWNER |
|---|:---:|:---:|:---:|
| Leer proyecto, miembros y tareas | ✅ | ✅ | ✅ |
| Crear, editar, reasignar tareas | ❌ | ✅ | ✅ |
| Eliminar una tarea creada por otro | ❌ | ❌ | ✅ |
| Editar o archivar el proyecto | ❌ | ❌ | ✅ |
| Gestionar los miembros del proyecto | ❌ | ❌ | ✅ |
| Transferir la propiedad del proyecto | ❌ | ❌ | ✅ |

- Un usuario que **no es miembro** recibe **404**, no 403: la respuesta no revela si el
  recurso existe.
- Un miembro con **rol insuficiente** recibe **403**.
- Conocer un `projectId` o un `taskId` **no** da acceso a nada.

Detalle: [`ADR-0004`](docs/adr/0004-autorizacion-por-membresia.md).

## Contrato de errores

Todos los errores son `application/problem+json` (RFC 7807) con un campo `code` estable:

```json
{
  "type": "https://devtrack.dev/problems/project-not-found",
  "title": "Proyecto no encontrado",
  "status": 404,
  "detail": "No existe un proyecto con id 42",
  "instance": "/api/v1/projects/42",
  "code": "PROJECT_NOT_FOUND",
  "timestamp": "2026-10-21T14:30:00.000Z"
}
```

Catálogo completo: [`docs/api-plan.md`](docs/api-plan.md) §2.

## Estructura del proyecto

```
devtrack/
├── pom.xml
├── Dockerfile                 multi-stage: JDK 21 + JRE 21, usuario no root
├── docker-compose.yml         PostgreSQL 18 + servicio opcional de la API
├── .github/workflows/ci.yml   build + tests + verificación del esquema
├── web/                       SPA React + TypeScript + Vite
│   ├── src/pages/             login, registro, proyectos y detalle
│   ├── src/components/        tablero, modales, miembros y layout
│   ├── src/lib/               cliente API, tipos, sesión y formato
│   └── vite.config.ts         puerto 3000 y proxy /api → :8080
├── docs/
│   ├── architecture.md        requisitos, modelo, seguridad, riesgos, sprints
│   ├── api-plan.md            contrato HTTP completo
│   ├── tasks-agent2.md        plan de trabajo con criterios de aceptación
│   └── adr/                   decisiones arquitectónicas
└── src/
    ├── main/java/com/devtrack/
    │   ├── shared/            config · exception · persistence · security · web
    │   ├── user/              api · domain · repository · service
    │   ├── project/           api · domain · repository · service
    │   └── task/              api · domain · repository · service
    ├── main/resources/
    │   ├── application.yml    configuración externalizada
    │   └── db/migration/      V1__users, V2__projects_and_members, V3__tasks
    └── test/java/com/devtrack/
        ├── support/           base de pruebas con Testcontainers
        ├── security/          matriz de autorización
        └── * /                tests de cada contexto
```

La organización es **por funcionalidad** (*package-by-feature*), no por tipo técnico: los
contextos `user`, `project` y `task` dependen en un solo sentido y comparten `shared` para
lo transversal. Ver [`ADR-0001`](docs/adr/0001-monolito-modular-package-by-feature.md).

## Decisiones de diseño

| ADR | Decisión |
|---|---|
| [0001](docs/adr/0001-monolito-modular-package-by-feature.md) | Monolito modular *package-by-feature* (sin Spring Modulith) |
| [0002](docs/adr/0002-stack-y-versiones.md) | Spring Boot 4.1.1 sobre Java 21 |
| [0003](docs/adr/0003-autenticacion-jwt-bcrypt.md) | JWT stateless con HS256 y contraseñas con BCrypt |
| [0004](docs/adr/0004-autorizacion-por-membresia.md) | Autorización por membresía; 404 para no-miembros |
| [0005](docs/adr/0005-contrato-de-errores.md) | `ProblemDetail` (RFC 7807) con código de negocio estable |
| [0006](docs/adr/0006-flyway-y-borrado-logico.md) | Flyway gobierna el esquema; borrado lógico de proyectos |

Aspectos destacados:

- **Ninguna entidad JPA se expone** en la API: todo pasa por DTOs.
- **El esquema solo lo crea Flyway**; Hibernate valida (`ddl-auto=validate`), de modo que
  la deriva entre entidad y esquema impide arrancar.
- **El esquema se versiona** y una migración aplicada no se edita nunca.
- **Transacciones en la capa de servicio**; los controladores no tienen lógica de negocio.
- **Locking optimista** en las tareas: dos ediciones simultáneas no se pisan, la segunda
  recibe un 409.
- **El JWT no lleva los roles de proyecto**: cambian con frecuencia y obligarían a
  reemitir tokens; se resuelven siempre contra la base de datos.
- **La cuenta deshabilitada se comprueba en cada petición**, no solo al iniciar sesión.

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/architecture.md`](docs/architecture.md) | Requisitos, supuestos, estructura, modelo de datos, seguridad, riesgos, sprints y divergencias |
| [`docs/api-plan.md`](docs/api-plan.md) | Contrato HTTP completo: rutas, cuerpos, códigos, catálogo de errores |
| [`docs/tasks-agent2.md`](docs/tasks-agent2.md) | Plan de trabajo con tareas, criterios de aceptación y pruebas |
| [`docs/adr/`](docs/adr/) | Decisiones arquitectónicas con contexto, alternativas y consecuencias |

## Qué no incluye esta versión

Queda fuera del alcance de la v1, ya planificado:

- **Autenticación**: refresh tokens, OAuth2/SSO, verificación de email, recuperación de
  contraseña, MFA.
- **Seguridad**: rate limiting, cabeceras de seguridad a nivel de proxy, auditoría de
  accesos, rotación de claves.
- **Funcionalidad**: comentarios, historial de actividad, métricas de progreso, búsqueda
  de texto completo, notificaciones, multi-tenantidad, internacionalización.

## Licencia

[MIT](LICENSE) © 2026 Hayderfino
