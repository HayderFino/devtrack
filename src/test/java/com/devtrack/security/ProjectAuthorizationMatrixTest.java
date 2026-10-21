package com.devtrack.security;

import java.util.List;

import com.devtrack.support.IntegrationTest;
import com.devtrack.support.TestSupport;
import com.devtrack.user.domain.User;
import com.devtrack.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private UserRepository userRepository;

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
                Arguments.of("VIEWER", Operation.LIST_TASKS, 200),
                Arguments.of("VIEWER", Operation.READ_TASK, 200),
                Arguments.of("VIEWER", Operation.CREATE_TASK, 403),
                Arguments.of("VIEWER", Operation.UPDATE_TASK, 403),
                Arguments.of("VIEWER", Operation.CHANGE_STATUS, 403),
                Arguments.of("VIEWER", Operation.ASSIGN_TASK, 403),
                Arguments.of("VIEWER", Operation.DELETE_TASK, 403),

                Arguments.of("EDITOR", Operation.READ_PROJECT, 200),
                Arguments.of("EDITOR", Operation.LIST_MEMBERS, 200),
                Arguments.of("EDITOR", Operation.UPDATE_PROJECT, 403),
                Arguments.of("EDITOR", Operation.ARCHIVE_PROJECT, 403),
                Arguments.of("EDITOR", Operation.ADD_MEMBER, 403),
                Arguments.of("EDITOR", Operation.CHANGE_MEMBER_ROLE, 403),
                Arguments.of("EDITOR", Operation.REMOVE_MEMBER, 403),
                Arguments.of("EDITOR", Operation.TRANSFER_OWNERSHIP, 403),
                Arguments.of("EDITOR", Operation.LIST_TASKS, 200),
                Arguments.of("EDITOR", Operation.READ_TASK, 200),
                Arguments.of("EDITOR", Operation.CREATE_TASK, 201),
                Arguments.of("EDITOR", Operation.UPDATE_TASK, 200),
                Arguments.of("EDITOR", Operation.CHANGE_STATUS, 200),
                Arguments.of("EDITOR", Operation.ASSIGN_TASK, 200),
                Arguments.of("EDITOR", Operation.DELETE_TASK, 422),

                Arguments.of("OWNER", Operation.READ_PROJECT, 200),
                Arguments.of("OWNER", Operation.LIST_MEMBERS, 200),
                Arguments.of("OWNER", Operation.UPDATE_PROJECT, 200),
                Arguments.of("OWNER", Operation.ARCHIVE_PROJECT, 204),
                Arguments.of("OWNER", Operation.ADD_MEMBER, 201),
                Arguments.of("OWNER", Operation.CHANGE_MEMBER_ROLE, 200),
                Arguments.of("OWNER", Operation.REMOVE_MEMBER, 204),
                Arguments.of("OWNER", Operation.TRANSFER_OWNERSHIP, 200),
                Arguments.of("OWNER", Operation.LIST_TASKS, 200),
                Arguments.of("OWNER", Operation.READ_TASK, 200),
                Arguments.of("OWNER", Operation.CREATE_TASK, 201),
                Arguments.of("OWNER", Operation.UPDATE_TASK, 200),
                Arguments.of("OWNER", Operation.CHANGE_STATUS, 200),
                Arguments.of("OWNER", Operation.ASSIGN_TASK, 200),
                Arguments.of("OWNER", Operation.DELETE_TASK, 204));
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

    @Test
    @DisplayName("Un no miembro recibe el mismo detalle para un proyecto existente y uno inexistente")
    void nonMemberGetsSameDetailForExistingAndMissingProject() throws Exception {
        Context context = scenarioWithRole("OWNER");
        String outsiderToken = TestSupport.registerAndLogin(mockMvc, TestSupport.validPassword());

        String existingBody = mockMvc.perform(get("/api/v1/projects/{id}", context.projectId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String missingBody = mockMvc.perform(get("/api/v1/projects/{id}", 999_999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat((String) JsonPath.read(existingBody, "$.code")).isEqualTo("PROJECT_NOT_FOUND");
        assertThat((String) JsonPath.read(missingBody, "$.code")).isEqualTo("PROJECT_NOT_FOUND");
        assertThat((String) JsonPath.read(existingBody, "$.detail"))
                .isEqualTo(JsonPath.read(missingBody, "$.detail"));
    }

    @Test
    @DisplayName("Un miembro del proyecto A no puede leer ni modificar el proyecto B (IDOR)")
    void memberOfAnotherProjectCannotAccessResources() throws Exception {
        Context projectA = scenarioWithRole("EDITOR");
        Context projectB = scenarioWithRole("OWNER");
        Context crossProject =
                new Context(projectB.projectId(), projectA.token(), projectA.userId(), projectB.targetUserId(),
                        projectB.candidateUserId(), projectB.taskId());

        for (Operation operation : List.of(
                Operation.READ_PROJECT,
                Operation.UPDATE_PROJECT,
                Operation.LIST_TASKS,
                Operation.READ_TASK,
                Operation.CREATE_TASK)) {
            operation.perform(mockMvc, crossProject)
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404))
                    .andExpect(result -> assertThat((String) JsonPath.read(
                            result.getResponse().getContentAsString(), "$.code")).isEqualTo("PROJECT_NOT_FOUND"));
        }
    }

    @Test
    @DisplayName("Un token de usuario deshabilitado recibe 401")
    void disabledUserTokenGetsUnauthorized() throws Exception {
        Context context = scenarioWithRole("VIEWER");
        User user = userRepository.findById(context.userId()).orElseThrow();
        user.setEnabled(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/projects/{id}", context.projectId())
                        .header(HttpHeaders.AUTHORIZATION, context.authorization()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401))
                .andExpect(result -> assertThat((String) JsonPath.read(
                        result.getResponse().getContentAsString(), "$.code")).isEqualTo("UNAUTHENTICATED"));
    }

    // ---------------------------------------------------------------------------------------------

    private Context scenarioWithRole(String role) throws Exception {
        String ownerEmail = TestSupport.uniqueEmail();
        Long ownerId = TestSupport.registerAndGetId(mockMvc, ownerEmail, TestSupport.validPassword());
        String ownerToken = TestSupport.login(mockMvc, ownerEmail, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());
        Long candidateId = TestSupport.registerAndGetId(mockMvc, TestSupport.uniqueEmail(), TestSupport.validPassword());
        Long taskId = TestSupport.createTask(mockMvc, ownerToken, projectId, "Tarea de la matriz");

        if ("OWNER".equals(role)) {
            Long targetId = TestSupport.registerAndGetId(mockMvc, TestSupport.uniqueEmail(), TestSupport.validPassword());
            TestSupport.addMember(mockMvc, ownerToken, projectId, targetId, "VIEWER");
            return new Context(projectId, ownerToken, ownerId, targetId, candidateId, taskId);
        }

        String memberEmail = TestSupport.uniqueEmail();
        Long memberId = TestSupport.registerAndGetId(mockMvc, memberEmail, TestSupport.validPassword());
        TestSupport.addMember(mockMvc, ownerToken, projectId, memberId, role);
        String memberToken = TestSupport.login(mockMvc, memberEmail, TestSupport.validPassword());

        return new Context(projectId, memberToken, memberId, memberId, candidateId, taskId);
    }

    /** Datos minimos que necesita una operacion para ejecutarse. */
    private record Context(
            Long projectId,
            String token,
            Long userId,
            Long targetUserId,
            Long candidateUserId,
            Long taskId) {
        Context withToken(String newToken) {
            return new Context(projectId, newToken, userId, targetUserId, candidateUserId, taskId);
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
        },
        LIST_TASKS {
            @Override
            ResultActions perform(MockMvc mockMvc, Context context) throws Exception {
                return mockMvc.perform(
                        get("/api/v1/projects/{id}/tasks", context.projectId()).header(AUTHORIZATION, context.authorization()));
            }
        },
        READ_TASK {
            @Override
            ResultActions perform(MockMvc mockMvc, Context context) throws Exception {
                return mockMvc.perform(get("/api/v1/projects/{id}/tasks/{taskId}", context.projectId(), context.taskId())
                        .header(AUTHORIZATION, context.authorization()));
            }
        },
        CREATE_TASK {
            @Override
            ResultActions perform(MockMvc mockMvc, Context context) throws Exception {
                return mockMvc.perform(post("/api/v1/projects/{id}/tasks", context.projectId())
                        .header(AUTHORIZATION, context.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Tarea creada por la matriz\"}"));
            }
        },
        UPDATE_TASK {
            @Override
            ResultActions perform(MockMvc mockMvc, Context context) throws Exception {
                return mockMvc.perform(patch("/api/v1/projects/{id}/tasks/{taskId}", context.projectId(), context.taskId())
                        .header(AUTHORIZATION, context.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Titulo cambiado por la matriz\"}"));
            }
        },
        CHANGE_STATUS {
            @Override
            ResultActions perform(MockMvc mockMvc, Context context) throws Exception {
                return mockMvc.perform(patch("/api/v1/projects/{id}/tasks/{taskId}", context.projectId(), context.taskId())
                        .header(AUTHORIZATION, context.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"));
            }
        },
        ASSIGN_TASK {
            @Override
            ResultActions perform(MockMvc mockMvc, Context context) throws Exception {
                return mockMvc.perform(
                        put("/api/v1/projects/{id}/tasks/{taskId}/assignee", context.projectId(), context.taskId())
                                .header(AUTHORIZATION, context.authorization())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"assigneeId\":%d}".formatted(context.userId())));
            }
        },
        DELETE_TASK {
            @Override
            ResultActions perform(MockMvc mockMvc, Context context) throws Exception {
                return mockMvc.perform(delete("/api/v1/projects/{id}/tasks/{taskId}", context.projectId(), context.taskId())
                        .header(AUTHORIZATION, context.authorization()));
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
