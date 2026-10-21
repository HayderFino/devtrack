# Contexto de traspaso — Agente 2 (Frontend)

> **Para qué sirve este documento.** El estado del frontend de DevTrack en el momento de
> escribirlo, preparado para que una sesión nueva (o un agente nuevo) pueda continuar sin
> re-derivar nada. No es un tutorial: es el contexto que hace falta para **seguir
> trabajando**.
>
> Actualizado: 2026-10-21 · Commit base: `ed09b8e` · Rama: `main`

---

## 1. Qué es este proyecto y dónde vive

**DevTrack** es una aplicación full stack para gestionar proyectos, miembros y tareas.

- **Repositorio:** https://github.com/HayderFino/devtrack (público)
- **Local:** `devtrack/` dentro del directorio de trabajo de la sesión.
- **Frontend:** `web/` — React 19 + TypeScript + Vite 8.
- **Backend:** `src/` — Java 21 + Spring Boot 4.1.1 + PostgreSQL 18.

## 2. Rol de cada agente

| | Agente 1 | Agente 2 |
|---|---|---|
| Ámbito | **Backend** | **Frontend** |
| Rutas | `src/**`, `docs/**`, `pom.xml` | `web/**` |
| Nada más | No tocar `web/` salvo acordado | No tocar `src/` salvo acordado |

> **Contexto para la sesión nueva:** compartís **el mismo directorio y la misma rama
> `main`**. Cada agente hace `git add` **solo de sus rutas**; nunca `git add -A` si el
> otro tiene cambios en curso (habrá archivos `web/` modificados sin commitear en el
> momento de escribir esto).

## 3. Estado del frontend

- `web/` contiene una SPA funcional: login, registro, listado de proyectos, detalle de
  proyecto con tablero de tareas, panel de miembros y alta/edición de proyectos y tareas.
- **Vite 8.3.5** en el puerto **3000**, con proxy de `/api` → `http://localhost:8080`.
- Se sirve el `dist/` ya construido.
- **Hay trabajo en curso sin commitear** en `web/src/`, `web/package.json` y
  `web/vite.config.ts`: revisa `git status` antes de tocar nada.

## 4. Contrato con el backend: cumple al 100 %

Verificado por comparación automática entre los tipos de TypeScript (`web/src/lib/types.ts`)
y los records de Java. **Los 19 endpoints que consumes existen y los 6 DTOs coinciden
campo a campo.**

| Tipo TS | Record Java | Estado |
|---|---|---|
| `User` | `UserResponse` | ✅ idéntico |
| `AuthResponse` | `AuthResponse` | ✅ idéntico |
| `Project` | `ProjectResponse` | ✅ idéntico |
| `ProjectMember` | `ProjectMemberResponse` | ✅ idéntico |
| `Task` | `TaskResponse` | ✅ idéntico |
| `PageResponse<T>` | `PageResponse<T>` | ✅ idéntico |
| `ProblemDetail` | `ProblemDetail` + `FieldViolation` | ✅ idéntico |

Los **9 filtros** de `TaskFilters` coinciden con los query params del backend:
`status[]`, `priority[]`, `assigneeId`, `unassigned`, `dueBefore`, `overdue`, `q`, `page`, `size`.

### 4.1 Detalles del contrato que conviene no olvidar

| Detalle | Valor |
|---|---|
| Base de la API | `/api/v1` |
| `GET /projects/{id}/members` | Devuelve un **array plano**, no un `PageResponse`. El cliente ya lo contempla con un tipo unión. |
| `completionRate` | Proporción en **[0,1]** con dos decimales (`0.53` = 53 %). Multiplica por 100 para pintar. |
| `byStatus` / `byPriority` | Incluyen **todos** los valores aunque valgan 0. No hace falta tratar nulos. |
| Errores | Siempre `application/problem+json` con `code` estable (`UNAUTHENTICATED`, `PROJECT_NOT_FOUND`, `FORBIDDEN`, `INVALID_STATUS_TRANSITION`, `VALIDATION_ERROR`…). El campo `detail` es legible, en español. |
| 404 vs 403 | **404** si no eres miembro del proyecto (o no existe). **403** si eres miembro pero tu rol no basta. El 404 **no** revela si el recurso existe. |
| `PUT /tasks/{id}/assignee` | **Idempotente**. `{"assigneeId": null}` desasigna. |
| `PATCH /tasks/{id}` | **No** cambia el asignado: tiene endpoint propio, para no confundir "ausente" con "a null". |
| `POST /auth/register` | No lleva cabecera `Location`. El perfil se lee en `/users/me`. |

**Contrato completo:** [`api-plan.md`](api-plan.md) · **OpenAPI interactivo:**
`http://localhost:8080/swagger-ui.html` (20 rutas documentadas).

### 4.2 Endpoint nuevo, disponible desde el 2026-10-21

```http
GET /api/v1/projects/{projectId}/stats
```

Devuelve el total de tareas, el recuento por estado y por prioridad, las sin asignar, las
asignadas al llamador, las vencidas, las que vencen en 7 días y el `completionRate`.
**Cualquier miembro puede consultarlo** (también un `VIEWER`). Está pensado para pintar un
panel de progreso en la cabecera del proyecto; si lo vas a usar, decide si quieres un
`GET` extra o aprovechar el proyecto que ya traes.

## 5. Entorno

### 5.1 Arrancar todo

```bash
cd devtrack
cp .env.example .env    # y poner DEVTRACK_JWT_SECRET (32+ caracteres)

# Opción A: backend en Docker (puerto 8080)
export DEVTRACK_JWT_SECRET="clave-de-prueba-local-de-al-menos-32-caracteres"
docker compose --profile api up -d --build

# Frontend
cd web && npm run dev   # http://localhost:3000
```

```bash
# Opción B: backend desde el IDE o con Maven
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run
```

### 5.2 Trampas del entorno

| Trampa | Realidad |
|---|---|
| **Docker es Colima**, no Docker Desktop | El socket no está en `/var/run/docker.sock`. Ya está configurado en `~/.testcontainers.properties`. |
| **Testcontainers necesita Ryuk deshabilitado** | Colima no permite montar sockets unix. `TESTCONTAINERS_RYUK_DISABLED=true` ya está en `~/.zshrc`. |
| **CORS no se ejercita en desarrollo** | Vite hace de proxy, así que el navegador ve todo como mismo origen. Si sirves el `dist/` desde otro origen, hay que añadirlo a `CORS_ALLOWED_ORIGINS`. |
| **JDK 21** | `JAVA_HOME` ya apunta a él en `~/.zshrc`. Si abres una terminal sin recargar: `source ~/.zshrc`. |

## 6. Reglas de trabajo que ha que respetar

1. **Nada se da por terminado sin evidencia.** Una pantalla está cerrada cuando se ha
   probado contra la API real, no cuando compila.
2. **Un cambio = un commit.**
3. **No se edita el backend sin explicar el problema y proponer un cambio acotado** →
   es del Agente 1.
4. **`README.md` y `docs/api-plan.md` son compartidos**: antes de editarlos, `git pull` y
   revisa si el otro agente los cambió.
5. **Nunca `git add -A`**: stagea solo `web/`.
6. **El contrato de la API no se cambia desde el frontend.** Si falta un endpoint o un
   campo, se pide; no se improvisa un workaround en el cliente.

## 7. Pendiente del frontend

| Prioridad | Ítem |
|---|---|
| Alta | Commitear el trabajo en curso de `web/` (hay cambios sin commitear) |
| Alta | Panel de métricas de progreso usando `GET /projects/{id}/stats` (endpoint ya disponible) |
| Media | Historial de actividad y comentarios: **dependen del backend**, que está aparcado por decisión del usuario. No se puede consumir hasta que exista el endpoint |
| Media | Login con refresh token y expiración de sesión (depende del backend, fase `F2-AUTH`) |
| Baja | i18n, notificaciones, modo oscuro, filtros guardados |

## 8. Estado del backend (para consumirlo, no para tocarlo)

- **234 tests en verde**, CI de GitHub Actions en verde.
- API REST stateless con JWT Bearer de 15 minutos.
- Persistencia con Flyway (3 migraciones) sobre PostgreSQL 18.
- Autorización por rol **dentro de cada proyecto**: `OWNER`, `EDITOR`, `VIEWER`.
- Swagger UI en `http://localhost:8080/swagger-ui.html`.

Detalle en [`contexto-agente-1-backend.md`](contexto-agente-1-backend.md).
