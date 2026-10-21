package com.devtrack.task.api;

import com.devtrack.support.IntegrationTest;
import com.devtrack.support.TestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRUD de tareas, filtros, transiciones de estado y asignacion.
 */
@IntegrationTest
@AutoConfigureMockMvc
class TaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Crea una tarea y devuelve 201 con el estado y la prioridad por defecto")
    void createReturnsCreatedWithDefaults() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");
        Long taskId = TestSupport.createTask(mockMvc, scenario.token(), scenario.projectId(), "Primera tarea");

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks/{taskId}", scenario.projectId(), taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId))
                .andExpect(jsonPath("$.title").value("Primera tarea"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    @DisplayName("Devuelve 400 si el titulo esta en blanco")
    void createWithoutTitleReturnsBadRequest() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");

        mockMvc.perform(post("/api/v1/projects/{projectId}/tasks", scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists());
    }

    @Test
    @DisplayName("Un VIEWER no puede crear tareas")
    void viewerCannotCreateTask() throws Exception {
        Scenario scenario = scenarioWithRole("VIEWER");

        mockMvc.perform(post("/api/v1/projects/{projectId}/tasks", scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"No deberia poder"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Asigna una tarea a un miembro del proyecto")
    void assignToMemberSucceeds() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");
        Long taskId = TestSupport.createTask(mockMvc, scenario.token(), scenario.projectId(), "Tarea asignable");

        mockMvc.perform(put("/api/v1/projects/{projectId}/tasks/{taskId}/assignee", scenario.projectId(), taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"assigneeId":%d}
                                """
                                .formatted(scenario.memberId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(scenario.memberId()));
    }

    @Test
    @DisplayName("No se puede asignar una tarea a alguien que no es miembro del proyecto")
    void assignToNonMemberReturnsUnprocessable() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");
        Long taskId = TestSupport.createTask(mockMvc, scenario.token(), scenario.projectId(), "Tarea");

        Long outsiderId = TestSupport.registerAndGetId(
                mockMvc, TestSupport.uniqueEmail(), TestSupport.validPassword());

        mockMvc.perform(put("/api/v1/projects/{projectId}/tasks/{taskId}/assignee", scenario.projectId(), taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"assigneeId":%d}
                                """
                                .formatted(outsiderId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_A_MEMBER"));
    }

    @Test
    @DisplayName("La asignacion es idempotente y se puede desasignar con null")
    void assignIsIdempotentAndUnassignable() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");
        Long taskId = TestSupport.createTask(mockMvc, scenario.token(), scenario.projectId(), "Tarea");

        String body = """
                {"assigneeId":%d}
                """
                .formatted(scenario.memberId());

        mockMvc.perform(put("/api/v1/projects/{projectId}/tasks/{taskId}/assignee", scenario.projectId(), taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(scenario.memberId()));

        // Repetir la asignacion no cambia nada.
        mockMvc.perform(put("/api/v1/projects/{projectId}/tasks/{taskId}/assignee", scenario.projectId(), taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(scenario.memberId()));

        mockMvc.perform(put("/api/v1/projects/{projectId}/tasks/{taskId}/assignee", scenario.projectId(), taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"assigneeId":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").doesNotExist());
    }

    @Test
    @DisplayName("Cambia el estado de la tarea a IN_PROGRESS")
    void updateStatusSucceeds() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");
        Long taskId = TestSupport.createTask(mockMvc, scenario.token(), scenario.projectId(), "Tarea");

        mockMvc.perform(patch("/api/v1/projects/{projectId}/tasks/{taskId}", scenario.projectId(), taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("Una transicion prohibida devuelve 422")
    void forbiddenTransitionReturnsUnprocessable() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");
        Long taskId = TestSupport.createTaskWithBody(
                mockMvc, scenario.token(), scenario.projectId(), "{\"title\":\"Tarea\",\"status\":\"DONE\"}");

        mockMvc.perform(patch("/api/v1/projects/{projectId}/tasks/{taskId}", scenario.projectId(), taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"status":"TODO"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("Una transicion al mismo estado es un no-op que no modifica la version")
    void sameStatusIsNoOp() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");
        Long taskId = TestSupport.createTask(mockMvc, scenario.token(), scenario.projectId(), "Tarea");

        mockMvc.perform(patch("/api/v1/projects/{projectId}/tasks/{taskId}", scenario.projectId(), taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"status":"TODO"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("Un EDITOR puede borrar la tarea que ha creado")
    void editorCanDeleteOwnTask() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");
        Long taskId = TestSupport.createTask(mockMvc, scenario.token(), scenario.projectId(), "Tarea propia");

        mockMvc.perform(delete("/api/v1/projects/{projectId}/tasks/{taskId}", scenario.projectId(), taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks/{taskId}", scenario.projectId(), taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un EDITOR no puede borrar una tarea que no ha creado")
    void editorCannotDeleteOtherTask() throws Exception {
        Scenario owner = scenarioWithRole("OWNER");
        Long taskId = TestSupport.createTask(mockMvc, owner.token(), owner.projectId(), "Tarea del owner");

        Scenario editor = addMemberTo(owner, "EDITOR");

        mockMvc.perform(delete("/api/v1/projects/{projectId}/tasks/{taskId}", owner.projectId(), taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + editor.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TASK_DELETION_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("El OWNER puede borrar cualquier tarea")
    void ownerCanDeleteAnyTask() throws Exception {
        Scenario owner = scenarioWithRole("OWNER");
        Scenario editor = addMemberTo(owner, "EDITOR");
        Long taskId = TestSupport.createTask(mockMvc, editor.token(), owner.projectId(), "Tarea del editor");

        mockMvc.perform(delete("/api/v1/projects/{projectId}/tasks/{taskId}", owner.projectId(), taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Filtra por estado")
    void filterByStatus() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");
        TestSupport.createTaskWithBody(mockMvc, scenario.token(), scenario.projectId(), "{\"title\":\"T1\",\"status\":\"TODO\"}");
        TestSupport.createTaskWithBody(mockMvc, scenario.token(), scenario.projectId(), "{\"title\":\"T2\",\"status\":\"DONE\"}");

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                        .param("status", "DONE")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.status == 'DONE')]").exists())
                .andExpect(jsonPath("$.content[?(@.status == 'TODO')]").doesNotExist());
    }

    @Test
    @DisplayName("Filtra por asignado")
    void filterByAssignee() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");
        Long assigned = TestSupport.createTaskWithBody(
                mockMvc, scenario.token(), scenario.projectId(), "{\"title\":\"Asignada\"}");
        TestSupport.createTask(mockMvc, scenario.token(), scenario.projectId(), "Sin asignar");

        mockMvc.perform(put("/api/v1/projects/{projectId}/tasks/{taskId}/assignee", scenario.projectId(), assigned)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"assigneeId":%d}
                                """
                                .formatted(scenario.memberId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                        .param("assigneeId", String.valueOf(scenario.memberId()))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Asignada"));
    }

    @Test
    @DisplayName("Filtra por texto libre")
    void filterByText() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");
        TestSupport.createTask(mockMvc, scenario.token(), scenario.projectId(), "Tarea conneedle");
        TestSupport.createTask(mockMvc, scenario.token(), scenario.projectId(), "Otra tarea");

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                        .param("q", "needle")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Tarea conneedle"));
    }

    @Test
    @DisplayName("Un estado de filtro invalido devuelve 400 y no 500")
    void invalidStatusFilterReturnsBadRequest() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                        .param("status", "NO_EXISTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("Un size fuera de rango devuelve 400")
    void invalidPageSizeReturnsBadRequest() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                        .param("size", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("Una tarea de otro proyecto no es accesible")
    void taskOfAnotherProjectIsNotAccessible() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");
        Long taskId = TestSupport.createTask(mockMvc, scenario.token(), scenario.projectId(), "Tarea");

        Scenario other = scenarioWithRole("EDITOR");

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks/{taskId}", other.projectId(), taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    // ---------------------------------------------------------------------------------------------

    private record Scenario(Long projectId, String token, Long memberId) {}

    private Scenario scenarioWithRole(String role) throws Exception {
        String ownerEmail = TestSupport.uniqueEmail();
        Long ownerId = TestSupport.registerAndGetId(mockMvc, ownerEmail, TestSupport.validPassword());
        String ownerToken = TestSupport.login(mockMvc, ownerEmail, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        if ("OWNER".equals(role)) {
            return new Scenario(projectId, ownerToken, ownerId);
        }

        String memberEmail = TestSupport.uniqueEmail();
        Long memberId = TestSupport.registerAndGetId(mockMvc, memberEmail, TestSupport.validPassword());
        TestSupport.addMember(mockMvc, ownerToken, projectId, memberId, role);
        String memberToken = TestSupport.login(mockMvc, memberEmail, TestSupport.validPassword());

        return new Scenario(projectId, memberToken, memberId);
    }

    private Scenario addMemberTo(Scenario owner, String role) throws Exception {
        String email = TestSupport.uniqueEmail();
        Long memberId = TestSupport.registerAndGetId(mockMvc, email, TestSupport.validPassword());
        TestSupport.addMember(mockMvc, owner.token(), owner.projectId(), memberId, role);
        String token = TestSupport.login(mockMvc, email, TestSupport.validPassword());
        return new Scenario(owner.projectId(), token, memberId);
    }
}
