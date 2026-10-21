# DevTrack

API REST para gestion de proyectos y tareas. Monolito modular con Spring Boot.

**Estado:** Sprints 0-3 completados. Ya estan implementados el registro/login con JWT,
`GET /api/v1/users/me`, los endpoints de proyectos y membresias, y el CRUD de tareas
(`/api/v1/projects/{projectId}/tasks`) con filtros, maquina de estados y locking optimista.
Queda pendiente el Sprint 4 (OpenAPI, Docker, CI, hardening y documentacion final).

## Stack

| Componente | Version |
|---|---|
| Java | 21 (probado con 21.0.13) |
| Spring Boot | 4.1.1 |
| Maven | 3.9.16 (wrapper) |
| Spring Modulith | 2.1.1 |
| Spring Security | 7.1.1 (gestionado por Boot) |
| springdoc-openapi | 3.1.1 |
| jjwt | 0.13.0 |
| PostgreSQL | 18 (`postgres:18-alpine`) |
| Docker Engine / Compose | 29.5.2 / 5.6.1 (probado) |

## Requisitos

- JDK 21 en `JAVA_HOME` (`java -version` debe mostrar `21.x`).
- Docker con el plugin `docker compose` (Docker Desktop o Colima).
- No hace falta Maven instalado: se usa `./mvnw`.

Con Colima: `colima start` antes de levantar la base de datos.

## Arranque local

```bash
cp .env.example .env
docker compose up -d
./mvnw spring-boot:run
```

Comprobar salud:

```bash
curl http://localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

El puerto `5432` de PostgreSQL se publica en el host para facilitar el acceso con `psql`.
Si ya tienes un PostgreSQL local, cambia el mapeo en `docker-compose.yml`.

## Pruebas

```bash
./mvnw test
./mvnw verify
```

Los tests de integracion levantan PostgreSQL 18 con Testcontainers, asi que necesitan un
Docker en ejecucion. Con Colima, ademas del `colima start`, el socket no esta en la ruta
por defecto; exporta estas variables antes de lanzar Maven:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./mvnw verify
```

Sin `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`, Ryuk no puede montar el socket y los tests
fallan al arrancar el contenedor. Los tests actuales cubren autenticacion (incluido el
rechazo de tokens de cuentas deshabilitadas), el contrato de proyectos/membresias, la
matriz completa de autorizacion por rol, las transiciones de estado de tareas y el
conflicto de locking optimista (409).

## Documentacion

- [Arquitectura](docs/architecture.md) (propuesta, pendiente de aprobacion final)
- [Backlog del Agente 2](docs/tasks-agent2.md)
- [Contrato de API](docs/api-plan.md)
