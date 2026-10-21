# ADR-0001 · Monolito modular con organización *package-by-feature*

- **Estado:** PROPUESTO — pendiente de aprobación
- **Fecha:** 2026-10-21
- **Decide sobre:** arquitectura general del proyecto
- **Relacionado:** `docs/architecture.md` §3

## Contexto

DevTrack es una API de gestión de proyectos y tareas, creada como proyecto de portafolio.
El encargo pide explícitamente un **monolito modular**, mantenible y fácil de ejecutar
localmente, evitando microservicios y complejidad innecesaria.

Spring Boot, al ser un framework basado en anotaciones y en un contenedor de dependencias,
ofrece dos convenciones de empaquetado habituales:

1. **Package-by-layer** (por tipo técnico): `controller/`, `service/`, `repository/`,
   `entity/`, `dto/`… todos al nivel raíz de la aplicación.
2. **Package-by-feature** (por funcionalidad): `user/`, `project/`, `task/`, con las
   capas como subpaquetes dentro de cada funcionalidad.

Existe también una tercera vía, la **arquitectura hexagonal** (puertos y adaptadores),
que separa el dominio de la infraestructura de forma estricta.

## Decisión

**Adoptar un monolito modular organizado por *feature* (contexto acotado), con las capas
dentro de cada feature.**

```
com.devtrack
├── shared/    config · exception · security · persistence · web
├── user/      api · service · domain · repository
├── project/   api · service · domain · repository
└── task/      api · service · domain · repository
```

Las dependencias entre features son **unidireccionales**:
`task → project → user`, y `shared` no depende de ninguna feature.

Se **descarta** la arquitectura hexagonal estricta (puertos/adaptadores) para esta v1.

## Justificación

### Por qué *package-by-feature* y no *package-by-layer*

- **Cohesión del cambio.** La regla de negocio "el asignado de una tarea debe ser miembro
  del proyecto" toca `task/service` y `project/service`. Con *package-by-layer*, el mismo
  cambio tocaría `service/`, `repository/`, `dto/` y `entity/` — cuatro carpetas no
  relacionadas, y el diff reviewer no puede ver de un vistazo el impacto.
- **La modularidad es real, no declarativa.** Con *package-by-layer*, la frontera entre
  módulos es difusa: cualquier paquete puede llamar a cualquier otro. Con *package-by-feature*,
  la estructura de paquetes **es** la frontera, y se puede verificar automáticamente.
- **Escala con el equipo.** El encargo anticipa dos agentes trabajando en paralelo. Con
  paquetes por feature, el Agente 1 (arquitectura) y el Agente 2 (desarrollo) pueden
  trabajar en zonas distintas del árbol sin colisionar.

### Por qué no arquitectura hexagonal

Spring Data JPA hace que el puerto `TaskRepository` y el adaptador generado por Spring sean
**el mismo artefacto**, sin separación real. La separación hexagonal en este proyecto sería
mayormente decorativa: añadiría interfaces y mapeadores sin añadir testabilidad ni
desacoplamiento real.

Lo que **sí** aporta valor, y se mantiene:

- DTOs explícitos y **nunca** exponer entidades JPA.
- Transacciones declaradas en la capa de servicio.
- Autorización explícita en un componente testeable (`ProjectAccessGuard`).
- Tests unitarios sin contexto de Spring.

### Descartado explícitamente

| Alternativa | Por qué no |
|---|---|
| Microservicios | El encargo los descarta. Añaden despliegue, red, observabilidad distribuida y consistencia distribuida para un dominio que cabe en un proceso. |
| Monolito por capas | Visto arriba: diffs más grandes, modularidad difusa. |
| Hexagonal estricto | Visto arriba: sobre-ingeniería para el tamaño y el objetivo. |
| CQRS / Event Sourcing | Complejidad desproporcionada. La API es CRUD con autorización; no hay requisitos de auditoría de eventos en la v1. |
| Spring Modulith (librería) | **Aprobado (Q-05, 2026-10-21).** Verifica los límites de módulo automáticamente y aporta diagramas. Se adoptó la versión 2.1.1 (compilada contra Spring Boot 4.1) y se añadió el test de verificación en T-01. |

## Consecuencias

### Positivas

- Los diffs son pequeños y revisables.
- La estructura de paquetes comunica la arquitectura sin necesidad de un documento.
- El aislamiento entre features hace trivial paralelizar el trabajo de dos agentes.
- Menos ceremonia y menos dependencias que en hexagonal.

### Negativas

- Código compartido entre features (p. ej. un `ProjectMemberView` que usan dos features)
  puede acabar en `shared/`, que es un cajón de sastre. **Mitigación:** `shared/` solo
  admite truly cross-cutting (configuración, errores, seguridad, paginación). Si algo
  pertenece a una feature, vive en esa feature.
- Una feature puede necesitar leer datos de otra (p. ej. `task` lee `project`). Eso rompe
  el aislamiento puro. **Mitigación:** la dependencia `task → project` es explícita,
  unidireccional y está documentada en `architecture.md` §3.3 (regla R-D4).

## Alternativas revisitadas

- **Spring Modulith 2.1.1** — *aprobado e incluido en el proyecto (Q-05)*.

  A favor: verifica los límites de módulo en los tests, genera diagramas automáticos,
  muestra en el README el estado real de la modularidad. Es un diferenciador de portafolio
  con coste bajo.

  En contra: una dependencia más, un modelo de módulos que hay que aprender, y una
  verificación que puede producir falsos positivos en un proyecto pequeño.

  Se aplica `@ApplicationModule` en `user`, `project` y `task` cuando esos módulos tengan
  codigo, y el test `ApplicationModules.verify()` ya falla si aparece una dependencia no
  permitida.

## Fecha de revisión

Cuando se complete el Sprint 2 (tareas T-10 a T-13), esta decisión debe revisarse: en ese
punto las dependencias reales entre features ya son observables y no hipotéticas.
