# Contexto de traspaso — Agente 1 (Backend)

> **Para qué sirve este documento.** Es el estado del backend de DevTrack en el momento de
> escribirlo, preparado para que una sesión nueva (o un agente nuevo) pueda continuar sin
> tener que re-derivar nada. No es un tutorial ni un resumen del producto: es el contexto
> que hace falta para **seguir trabajando**.
>
> Actualizado: 2026-10-21 · Commit base: `ed09b8e` · Rama: `main`

---

## 1. Qué es este proyecto y dónde vive

**DevTrack** es una API REST (Java 21 + Spring Boot 4.1.1) para gestionar proyectos, miembros
y tareas, con autorización por rol **dentro de cada proyecto**.

- **Repositorio:** https://github.com/HayderFino/devtrack (público)
- **Local:** `devtrack/` dentro del directorio de trabajo de la sesión.
- **Documentación de diseño:** [`architecture.md`](architecture.md) · [`api-plan.md`](api-plan.md) ·
  [`tasks-agent2.md`](tasks-agent2.md) · [`adr/`](adr/)

## 2. Rol de cada agente

| | Agente 1 | Agente 2 |
|---|---|---|
| Ámbito | **Backend** | **Frontend** |
| Rutas | `src/main/java/com/devtrack/**`, `src/test/java/com/devtrack/**` (menos `web/`), `src/main/resources/**`, `pom.xml`, `docs/**` | `web/**` |
| Nada más | No tocar `web/` salvo acordado | No tocar `src/` salvo acordado |

> **Contexto para la sesión nueva:** el backend y el frontend comparten **el mismo
> directorio de trabajo y la misma rama `main`**. Cada agente hace `git add` **solo de sus
> rutas**. Hay un único `README.md` y un único `docs/api-plan.md` compartidos: si los dos
> los editan, el último `push` gana. **Antes de tocar un doc compartido, haz `git pull` y
> revisa si el otro agente lo cambió** (`git diff`).

## 3. Estado del backend (todo verificado)

### 3.1 Implementado y en verde

| Área | Contenido |
|---|---|
| Identidad | Registro, login, JWT HS256 (15 min), BCrypt, `GET /users/me` |
| Proyectos | CRUD, archivado lógico, filtro `archived` |
| Miembros | Alta, cambio de rol, baja, transferencia de propiedad atómica |
| Tareas | CRUD anidado, 9 filtros, máquina de estados de 4 estados, asignación idempotente, locking optimista |
| Métricas | `GET /projects/{id}/stats` (totales, por estado, por prioridad, vencidas, `completionRate` en [0,1]) |
| Errores | `ProblemDetail` (RFC 7807) con `code` estable, unificado también en 401/403 |
| Autorización | `ProjectAccessGuard` explícito: 404 si no eres miembro, 403 si el rol no basta |
| Persistencia | Flyway (3 migraciones), `ddl-auto=validate`, `open-in-view=false` |
| Entrega | Dockerfile multi-stage (no root), docker-compose con perfil `api`, CI en GitHub Actions, OpenAPI 3.1.0 (20 rutas) |

**Pruebas:** `./mvnw clean verify` → **234 tests, BUILD SUCCESS**. El CI de GitHub Actions
también en verde (último run: `39677406097`).

### 3.2 Verificado en ejecución real (no solo en tests)

- Clon limpio → `./mvnw clean verify` → verde.
- Jar arrancado contra PostgreSQL limpio: aplica las migraciones y expone `/actuator/health`.
- Stack Docker completo: `devtrack-api` y `devtrack-postgres` en `healthy`.
- Integración real: Vite (`:3000`) → proxy → API (`:8080`) → JWT → `201`.

## 4. Decisiones que ya están fijadas (no re-litigar)

Están en [`adr/`](adr/) y **aceptadas**. Cambiarlas exige un ADR nuevo.

| ADR | Decisión |
|---|---|
| 0001 | Monolito modular *package-by-feature*. **Spring Modulith NO se usa** (decisión del usuario) |
| 0002 | Spring Boot 4.1.1 + Java 21. Spring Boot 3.5.x descartado por falta de soporte OSS |
| 0003 | JWT stateless HS256 + BCrypt. **Sin refresh token** en la v1 |
| 0004 | Autorización por membresía; 404 para no-miembros, 403 por rol insuficiente |
| 0005 | `ProblemDetail` + `code` de negocio estable |
| 0006 | Flyway gobierna el esquema; `project_members` se archiva lógicamente |

**Puntos donde la implementación se apartó del diseño inicial**, todos registrados en
`architecture.md` §13 y `api-plan.md` §12. Los tres que más conviene no olvidar:

1. `ProjectResponse` **no** tiene `taskCount`: el contexto `project` no puede conocer a
   `task` (regla R-D4).
2. `GET /projects/{id}/members` devuelve un **array plano**, no paginado.
3. `ProjectStatsController` vive en el **contexto `task`** aunque su ruta sea de proyecto,
   por la misma regla R-D4.

## 5. Entorno: lo que hay que saber antes de que algo falle

### 5.1 Toolchain

| Qué | Dónde |
|---|---|
| JDK 21 | `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` — **exportado en `~/.zshrc`** como `JAVA_HOME` |
| Docker | Homebrew (29.9.2) + **Colima 0.10.3** como runtime (no hay Docker Desktop) |
| Maven | Solo el wrapper: `./mvnw` (3.9.16). No hace falta Maven instalado |
| GitHub CLI | `gh` 2.103.0, **autenticado** como `HayderFino` (keyring, scope `repo`) |

### 5.2 Las dos trampas de Colima

**a) Testcontainers no encuentra el socket.** Colima no expone el socket en
`/var/run/docker.sock`. Solución en `~/.testcontainers.properties`:

```properties
docker.host=unix:///Users/hayder/.colima/default/docker.sock
```
> La ruta debe ser **absoluta**: Java no expande variables en los ficheros de properties.

**b) Ryuk no arranca.** Ryuk necesita montar el socket de Docker dentro de su contenedor y
Colima no permite montar sockets unix del host (`operation not supported`).
Solución: `export TESTCONTAINERS_RYUK_DISABLED=true` (**ya está en `~/.zshrc`**).

> En Testcontainers 2.x el flag de Ryuk **solo existe como variable de entorno**: no hay
> propiedad `ryuk.disabled`. Los contenedores los limpia Testcontainers al cerrar el
> contexto de Spring, así que no se acumulan.

### 5.3 Variables de entorno

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export TESTCONTAINERS_RYUK_DISABLED=true
```

`DEVTRACK_JWT_SECRET` **debe tener 32 caracteres o más**: si falta, la aplicación **no
arranca** (fail-fast deliberado, riesgo R-04).

## 6. Reglas de trabajo que ha que respetar

1. **Nada se da por terminado sin evidencia.** Una tarea está cerrada cuando hay un test
   que fallaría sin el cambio, o una ejecución real que lo demuestra.
2. **Una tarea = un commit.** Nada de "todo el módulo" en un solo commit.
3. **No se edita código ajeno sin explicar el problema y proponer un cambio acotado.**
4. **Los docs se actualizan en el mismo commit** que el código que los cambia.
5. **Prohibido**: `System.out.println`, entidades JPA en respuestas, `ddl-auto=update`,
   `skipTests`, secretos en el repo, `@MockBean` (eliminado en Boot 4 → `@MockitoBean`).
6. **Si una migración de Flyway ya se aplicó, no se edita**: se crea una nueva.
7. **No se stagean rutas del otro agente.** `git add src/ docs/ pom.xml` (nunca `git add -A`
   si el otro agente tiene cambios en curso).

## 7. Pendiente para el backend

| Prioridad | Ítem | Notas |
|---|---|---|
| Alta | **Historial de actividad** y **comentarios** | **Aparcado por decisión del usuario el 2026-10-21.** Es lo único pendiente del encargo original. El historial es el que más riesgo tiene: necesita tabla de auditoría con actor, tipo de evento y timestamp, y hay que decidir qué acciones se registran y cómo se evita que crezca sin control. |
| Media | `GET /api/v1/tasks` cross-proyecto (mis tareas) | Ruta ya reservada en `api-plan.md` |
| Media | `GET /api/v1/users` (solo ADMIN) | Ruta ya reservada |
| Baja | Rate limiting, cabeceras de seguridad, rotación de claves | Fase `F2-SEC` |
| Baja | Refresh tokens, OAuth2/SSO, MFA, recuperación de contraseña | Fase `F2-AUTH` |

## 8. Estado del frontend (para poder consumirlo, no para tocarlo)

- `web/` es una SPA React + TypeScript + Vite (Vite 8.3.5), en el puerto **3000**, con
  proxy de `/api` → `http://localhost:8080`.
- **Cumple el contrato al 100 %**: los 19 endpoints que consume existen y los 6 DTOs
  coinciden campo a campo (verificado por comparación automática TS ↔ Java).
- Su código está en curso: hay cambios sin commitear en `web/`. **No los stagees.**

Detalle en [`contexto-agente-2-frontend.md`](contexto-agente-2-frontend.md).

## 9. Cómo verificar que todo sigue bien

```bash
cd devtrack
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export TESTCONTAINERS_RYUK_DISABLED=true

./mvnw clean verify          # 234 tests + BUILD SUCCESS
docker compose --profile api up -d --build
curl -s localhost:8080/actuator/health
```

Si `docker compose` levanta un Postgres que ya está en el 5432 por otro proyecto, cambia
`DB_PORT` en `.env`.

## 10. Tareas de mantenimiento que se know

- **El CI falló una vez y ya está corregido.** Si vuelve a fallar, mira primero el paso
  "Arrancar el jar y verificar el esquema migrado": comprueba la base equivocada.
- **La rama debe ser `main`**: el workflow dispara en `main`. Si alguien la renombra, el
  CI deja de ejecutarse en silencio.
- **Spring Boot 4 renombró varios starters** (`webmvc`, `jackson`, `-test`), y los
  starters de test específicos **no traen JUnit**: hay que declarar también
  `spring-boot-starter-test`.
