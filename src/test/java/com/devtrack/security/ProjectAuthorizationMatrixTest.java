package com.devtrack.security;

import java.util.List;

import com.devtrack.support.IntegrationTest;
import com.devtrack.support.TestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Matriz de autorizacion por rol de proyecto.
 *
 * <p>Cubre <strong>todas</strong> las celdas de la matriz de ADR-0004 para las operaciones
 * de proyecto y membresia. El objetivo es que un cambio en la politica de permisos rompa
 * un test, no que se detecte en produccion.
 *
 * <p>Reglas verificadas:
 * <ul>
 *   <li>No miembro → 404 (no se revela la existencia del recurso).</li>
 *   <li>Miembro con rol insuficiente → 403.</li>
 *   <li>Miembro con rol suficiente → 2xx.</li>
 * </ul>
 */
@IntegrationTest
@AutoConfigureMockMvc
class ProjectAuthorizationMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    static List<Arguments> projectOperations() {
        return List.of(
                Arguments.of("VIEWER", Operation.READ_PROJECT, 200),
                Arguments.of("VIEWER", Operation.LIST_MEMBERS, 200),
                Arguments.of("VIEWER", Operation.UPDATE_PROJECT, 403),
                Arguments.of("VIEWER", Operation.ARCHIVE_PROJECT, 403),
                Arguments.of("VIEWER", Operation.ADD_MEMBER, 403),
                Arguments.of("VIEWER", Operation.CHANGE_MEMBER_ROLE, 403),
                Arguments.of("VIEWER", Operation.REMOVE_MEMBER, 403),
                Arguments.of("VIEWER", Operation.TRANSFER_OWNERSHIP, 403),

                Arguments.of("EDITOR", Operation.READ_PROJECT, 200),
                Arguments.of("EDITOR", Operation.LIST_MEMBERS, 200),
                Arguments.of("EDITOR", Operation.UPDATE_PROJECT, 403),
                Arguments.of("EDITOR", Operation.ARCHIVE_PROJECT, 403),
                Arguments.of("EDITOR", Operation.ADD_MEMBER, 403),
                Arguments.of("EDITOR", Operation.CHANGE_MEMBER_ROLE, 403),
                Arguments.of("EDITOR", Operation.REMOVE_MEMBER, 403),
                Arguments.of("EDITOR", Operation.TRANSFER_OWNERSHIP, 403),

                Arguments.of("OWNER", Operation.READ_PROJECT, 200),
                Arguments.of("OWNER", Operation.LIST_MEMBERS, 200),
                Arguments.of("OWNER", Operation.UPDATE_PROJECT, 200),
                Arguments.of("OWNER", Operation.ARCHIVE_PROJECT, 204),
                Arguments.of("OWNER", Operation.ADD_MEMBER, 201),
                Arguments.of("OWNER", Operation.CHANGE_MEMBER_ROLE, 200),
                Arguments.of("OWNER", Operation.REMOVE_MEMBER, 204),
                Arguments.of("OWNER", Operation.TRANSFER_OWNERSHIP, 200));
    }

    @ParameterizedTest(name = "[{index}] rol {0} -> {1} espera {2}")
    @MethodSource("projectOperations")
    @DisplayName("La operacion respeta la matriz de permisos del rol")
    void operationRespectsRoleMatrix(String role, Operation operation, int expectedStatus) throws Exception {
        Context context = scenarioWithRole(role);

        operation.perform(mockMvc, context)
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                        .isEqualTo(expectedStatus));
    }

    @ParameterizedTest(name = "[{index}] no miembro -> {1} espera 404")
    @MethodSource("projectOperations")
    @DisplayName("Un usuario que no es miembro recibe 404 en todas las operaciones")
    void nonMemberAlwaysGetsNotFound(String role, Operation operation, int expectedStatus) throws Exception {
        Context context = scenarioWithRole("OWNER");
        String outsiderToken = TestSupport.registerAndLogin(mockMvc, TestSupport.validPassword());

        operation.perform(mockMvc, context.withToken(outsiderToken))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                        .isEqualTo(404));
    }

    @ParameterizedTest(name = "[{index}] sin token -> {1} espera 401")
    @MethodSource("projectOperations")
    @DisplayName("Sin token todas las operaciones devuelven 401")
    void withoutTokenAlwaysGetsUnauthorized(String role, Operation operation, int expectedStatus) throws Exception {
        Context context = scenarioWithRole("OWNER");

        operation.perform(mockMvc, context.withToken(null))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                        .isEqualTo(401));
    }

    // ---------------------------------------------------------------------------------------------

    private Context scenarioWithRole(String role) throws Exception {
        String ownerEmail = TestSupport.uniqueEmail();
        Long ownerId = TestSupport.registerAndGetId(mockMvc, ownerEmail, TestSupport.validPassword());
        String ownerToken = TestSupport.login(mockMvc, ownerEmail, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());
        Long candidateId = TestSupport.registerAndGetId(mockMvc, TestSupport.uniqueEmail(), TestSupport.validPassword());

        if ("OWNER".equals(role)) {
            Long targetId = TestSupport.registerAndGetId(mockMvc, TestSupport.uniqueEmail(), TestSupport.validPassword());
            TestSupport.addMember(mockMvc, ownerToken, projectId, targetId, "VIEWER");
            return new Context(projectId, ownerToken, ownerId, targetId, candidateId);
        }

        String memberEmail = TestSupport.uniqueEmail();
        Long memberId = TestSupport.registerAndGetId(mockMvc, memberEmail, TestSupport.validPassword());
        TestSupport.addMember(mockMvc, ownerToken, projectId, memberId, role);
        String memberToken = TestSupport.login(mockMvc, memberEmail, TestSupport.validPassword());

        return new Context(projectId, memberToken, memberId, memberId, candidateId);
    }

    /** Datos minimos que necesita una operacion para ejecutarse. */
    private record Context(Long projectId, String token, Long userId, Long targetUserId, Long candidateUserId) {
        Context withToken(String newToken) {
            return new Context(projectId, newToken, userId, targetUserId, candidateUserId);
        }

        String authorization() {
            return token == null ? null : "Bearer " + token;
        }
    }

    /** Operacion de la API usada por la matriz. */
    private enum Operation {
        READ_PROJECT {
            @Override
            ResultActions perform(MockMvc mockMvc, Context context) throws Exception {
                return mockMvc.perform(get("/api/v1/projects/{id}", context.projectId()).header(AUTHORIZATION, context.authorization()));
            }
        },
        LIST_MEMBERS {
            @Override
            ResultActions perform(MockMvc mockMvc, Context context) throws Exception {
                return mockMvc.perform(
                        get("/api/v1/projects/{id}/members", context.projectId()).header(AUTHORIZATION, context.authorization()));
            }
        },
        UPDATE_PROJECT {
            @Override
            ResultActions perform(MockMvc mockMvc, Context context) throws Exception {
                return mockMvc.perform(patch("/api/v1/projects/{id}", context.projectId())
                        .header(AUTHORIZATION, context.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cambiado por la matriz\"}"));
            }
        },
        ARCHIVE_PROJECT {
            @Override
            ResultActions perform(MockMvc mockMvc, Context context) throws Exception {
                return mockMvc.perform(
                        delete("/api/v1/projects/{id}", context.projectId()).header(AUTHORIZATION, context.authorization()));
            }
        },
        ADD_MEMBER {
            @Override
            ResultActions perform(MockMvc mockMvc, Context context) throws Exception {
                return mockMvc.perform(post("/api/v1/projects/{id}/members", context.projectId())
                        .header(AUTHORIZATION, context.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":%d,\"role\":\"VIEWER\"}".formatted(context.candidateUserId())));
            }
        },
        CHANGE_MEMBER_ROLE {
            @Override
            ResultActions perform(MockMvc mockMvc, Context context) throws Exception {
                return mockMvc.perform(patch("/api/v1/projects/{id}/members/{userId}", context.projectId(), context.targetUserId())
                        .header(AUTHORIZATION, context.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"VIEWER\"}"));
            }
        },
        REMOVE_MEMBER {
            @Override
            ResultActions perform(MockMvc mockMvc, Context context) throws Exception {
                return mockMvc.perform(
                        delete("/api/v1/projects/{id}/members/{userId}", context.projectId(), context.targetUserId())
                                .header(AUTHORIZATION, context.authorization()));
            }
        },
        TRANSFER_OWNERSHIP {
            @Override
            ResultActions perform(MockMvc mockMvc, Context context) throws Exception {
                return mockMvc.perform(
                        put("/api/v1/projects/{id}/members/{userId}/ownership", context.projectId(), context.targetUserId())
                                .header(AUTHORIZATION, context.authorization())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"newOwnerId\":%d}".formatted(context.targetUserId())));
            }
        };

        abstract ResultActions perform(MockMvc mockMvc, Context context) throws Exception;

        static final String AUTHORIZATION = HttpHeaders.AUTHORIZATION;

        /** Encabezado Authorization con el esquema Bearer. */
        static String bearer(String token) {
            return token == null ? null : "Bearer " + token;
        }
    }
}
