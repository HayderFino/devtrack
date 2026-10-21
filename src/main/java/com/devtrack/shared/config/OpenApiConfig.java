package com.devtrack.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Metadatos del documento OpenAPI que describe la API de DevTrack.
 *
 * <p>El esquema de seguridad {@code bearerAuth} se aplica de forma global; los endpoints
 * publicos (registro y login) lo anulan explicitamente con {@code @SecurityRequirements}.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    OpenAPI devTrackOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DevTrack API")
                        .version("1.0.0")
                        .description(description())
                        .contact(new Contact().name("DevTrack"))
                        .license(new License().name("Por definir")))
                .components(new Components().addSecuritySchemes(
                        SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token de acceso JWT. Se obtiene en POST /api/v1/auth/login.")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }

    private static String description() {
        return """
                API REST para la gestion de proyectos y tareas.

                ## Autenticacion

                La API es **stateless**: se autentica con un token JWT en la cabecera
                `Authorization: Bearer <token>`. El token se obtiene en
                `POST /api/v1/auth/login` y caduca a los 15 minutos.

                ```
                Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
                ```

                ## Autorizacion

                Cada usuario tiene un rol **dentro de cada proyecto**. La fuente de verdad
                es la membresia del proyecto: conocer el identificador de un proyecto o de
                una tarea **no** da acceso a ellos.

                | Operacion | VIEWER | EDITOR | OWNER |
                |---|:---:|:---:|:---:|
                | Leer proyecto, miembros y tareas | si | si | si |
                | Crear, editar, reasignar tareas | no | si | si |
                | Eliminar una tarea creada por otro | no | no | si |
                | Editar o archivar el proyecto | no | no | si |
                | Gestionar los miembros del proyecto | no | no | si |
                | Transferir la propiedad del proyecto | no | no | si |

                ## Convenciones de error

                Todos los errores se devuelven como `application/problem+json` (RFC 7807)
                con un campo `code` estable que permite ramificar sin parsear texto:

                ```
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

                Un usuario que **no es miembro** de un proyecto recibe `404`, no `403`: la
                respuesta no revela si el recurso existe.
                """;
    }
}
