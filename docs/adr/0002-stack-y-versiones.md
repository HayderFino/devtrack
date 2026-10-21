# ADR-0002 · Versiones del stack: Spring Boot 4.1.1 sobre Java 21

- **Estado:** **Aceptado** (2026-10-21)
- **Fecha:** 2026-10-21
- **Decide sobre:** versiones de la plataforma
- **Relacionado:** `docs/architecture.md` §2

## Contexto

El proyecto parte de cero (no hay código previo). Hay que elegir la versión de Spring Boot
y la de Java. Spring Boot publica una major cada seis meses (mayo y noviembre) y su
soporte OSS sigue la política de VMware Tanzu: cada major al menos 3 años, cada minor al
menos 12 meses.

Las versiones relevantes, **verificadas el 2026-10-21** contra Maven Central, `endoflife.date`
y la documentación oficial de Spring:

| Línea | Última | Publicada | EOL OSS | Soporte extendido |
|---|---|---|---|---|
| **4.1** | **4.1.1** | 2026-06-30 | **2027-07-31** | 2028-07-31 |
| 4.0 | 4.0.8 | 2025-11-30 | 2026-12-31 | 2027-12-31 |
| 3.5 | 3.5.16 | 2025-05-31 | **2026-06-30** (ya vencido) | 2032-06-30 |

Spring Boot 4.1 requiere **Java 17 como mínimo** y es compatible hasta Java 26.
Spring Boot 4.x modularizó el framework y renombró varios starters (ver más abajo).

## Decisión

**Spring Boot 4.1.1 sobre Java 21 (LTS).**

Versiones fijadas (todas verificadas):

| Componente | Versión | ¿Gestionada por el BOM de Boot? |
|---|---|---|
| Java | 21 (LTS) | — |
| Spring Boot | 4.1.1 | — (es el parent) |
| Maven | 3.9.x (Maven Wrapper 3.9.16) | — |
| PostgreSQL (servidor) | 18 (`postgres:18-alpine`) | — |
| Driver JDBC PostgreSQL | 42.7.13 | Sí |
| Flyway | 12.4.0 | Sí |
| Testcontainers | 2.0.5 | Sí |
| springdoc-openapi | 3.1.1 | No — se fija explícitamente |
| jjwt | 0.13.0 | No — se fija explícitamente |
| Spring Modulith | 2.1.1 *(opcional)* | No — se fija explícitamente |

**Principio de gestión de versiones:** todo lo que el BOM de Spring Boot gestione se deja
**sin versión explícita** en el `pom.xml`, para heredar las actualizaciones de seguridad
del parche de Boot. Solo se fija versión en las dependencias que Boot **no** gestiona
(springdoc, jjwt).

## Justificación

### Spring Boot 4.1.1 frente a 3.5.16

- **3.5.16 ya no tiene soporte OSS** (venció el 2026-06-30). Solo queda el soporte comercial
  extendido hasta 2032. Empezar un proyecto nuevo sobre una línea cuyo soporte comunitario
  ha terminado es una mala base.
- **4.1.1 tiene 15 meses de soporte OSS por delante** (hasta 2027-07-31) y 21 meses de
  soporte extendido (hasta 2028-07-31).
- **Es greenfield:** no hay coste de migración. Se adoptan los nombres de starter nuevos
  desde el primer día.
- El ecosistema ya está alineado: springdoc 3.x, Spring Modulith 2.1.x, Testcontainers 2.x
  y Flyway 13.x soportan Spring Boot 4.

### Java 21 frente a Java 25

- Ambos son LTS.
- **Java 21** es el LTS más implementado y con mayor compatibilidad de librerías y herramientas.
  Es el valor por defecto prudente.
- **Java 25** (LTS desde 2025) es más moderno, pero Spring Boot 4.1.1 fue compilado contra
  Java 17 como mínimo, y el ecosistema de build todavía se estandariza en 21.
- Se fija **21**. Si el entorno ya tiene 25 y todo compila, es un cambio trivial
  (`<java.version>` y el toolchain), pero **no se hace sin probarlo**.

### Consecuencias de usar Spring Boot 4 (obligatorias en la implementación)

Estas son **cambios reales** documentados en la guía de migración oficial, no FISAs:

| En Boot 3 | En Boot 4 |
|---|---|
| `spring-boot-starter-web` | **`spring-boot-starter-webmvc`** (el anterior queda deprecado) |
| `spring-boot-starter-json` | **`spring-boot-starter-jackson`** (Jackson 3, `tools.jackson.core`) |
| `spring-boot-starter-test` | `spring-boot-starter-<tech>-test` (p. ej. `-webmvc-test`, `-data-jpa-test`, `-security-test`) |
| `@MockBean` / `@SpyBean` | **`@MockitoBean` / `@MockitoSpyBean`** (los anteriores se eliminaron) |
| `flyway-core` suelto | **`spring-boot-starter-flyway`** + `flyway-database-postgresql` |
| `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` |

Además, en **Testcontainers 2.x los módulos se renombraron**:
`org.testcontainers:testcontainers-postgresql` y `org.testcontainers:testcontainers-junit-jupiter`
(los nombres `postgresql` / `junit-jupiter` de la 1.x devuelven 404).
Este detalle es una trampa fácil de caer y está escrito en el backlog (tarea T-05).

### Trampa conocida: Jackson 2 vs Jackson 3

Spring Boot 4 usa **Jackson 3** (`tools.jackson.core`). La librería `jjwt-jackson` depende de
**Jackson 2** (`com.fasterxml.jackson.core`). Ambas conviven en el classpath, pero es una
combinación que **debe verificarse en runtime**, no asumir. Está registrado como riesgo
**R-10 / R-12** y la tarea T-08 incluye un test explícito de emisión y validación de un
token real. Si falla, la alternativa es `jjwt-gson` o `jjwt-orgjson`.

## Alternativas descartadas

| Alternativa | Por qué no |
|---|---|
| Spring Boot 3.5.16 | Soporte OSS vencido (2026-06-30) |
| Spring Boot 4.0.8 | EOL OSS en 2026-12-31, dentro de 2 meses. La 4.1.1 dura más y solo cuesta aceptar los starters nuevos |
| Spring Boot 4.2.0-M2 | Es un *milestone*, no una estable |
| Java 17 | LTS pero anterior a 21; se pierde parte del lenguaje moderno (records, sealed, pattern matching exhaustivo ya está en 17, pero no es el LTS "de facto") |
| Java 25 | Válido, pero menos probado en el ecosistema de build. Se acepta como cambio posterior |
| Gradle | Maven es lo pedido y suficiente |
| Spring Boot 4.0.7 con springdoc 3.0.x | springdoc 3.1.1 ya está alineado con Boot 4.1; no hay razón para fijar una 4.0 anterior |

## Fecha de revisión

Al cerrar el Sprint 4 (T-21): comprobar que Spring Boot 4.2.x o 5.x no estén disponibles con
una breaking change que afecte al proyecto, y actualizar la fecha de EOL en el README.

## Resultado de la implementacion

Las versiones fijadas se han materialized tal cual. Tres hallazgos surgieron durante la
implementacion y se documentan aqui:

1. **Testcontainers 2.x renombro sus modulos.** El artefacto es
   `org.testcontainers:testcontainers-postgresql` (no `postgresql`) y la clase paso al
   paquete `org.testcontainers.postgresql`. Ademas `PostgreSQLContainer` **ya no es
   generica** en la 2.x. Los nombres de la 1.x devuelven 404.
2. **Los starters de test especificos de Spring Boot 4 no traen JUnit.**
   `spring-boot-starter-webmvc-test` y companhia solo anaden el soporte de slice test; el
   JUnit 5 (con `junit-jupiter-params`), AssertJ, Mockito y JSONassert vienen de
   `spring-boot-starter-test`, que debe declararse **ademas** de los especificos.
3. **`spring-boot-testcontainers` es un modulo aparte**, no lo aporta ningun starter: hay
   que declararlo explicitamente para usar `@ServiceConnection`.

### Version probada en este entorno

| Componente | Version verificada |
|---|---|
| OpenJDK | 21.0.13 (Homebrew) |
| Docker Engine / Compose | 29.9.2 / 5.6.1 |
| Colima | 0.10.3 (runtime de Docker en macOS sin Docker Desktop) |
| PostgreSQL | 18.6 (imagen `postgres:18-alpine`) |

### Nota sobre Ryuk y Colima

Ryuk necesita montar el socket de Docker dentro de su contenedor, y **Colima no permite
montar sockets unix del host** (`operation not supported`). Opciones aplicadas:

- `~/.testcontainers.properties` con `docker.host=unix://<ruta al socket de colima>`
  (ruta **absoluta**: Java no expande variables en los ficheros de properties).
- `TESTCONTAINERS_RYUK_DISABLED=true` en el entorno. En la 2.x el flag **solo** existe
  como variable de entorno: no hay propiedad `ryuk.disabled`.

Los contenedores los limpia Testcontainers al cerrar el contexto de Spring
(`GenericContainer.stop()` los elimina), de modo que no se acumulan.
