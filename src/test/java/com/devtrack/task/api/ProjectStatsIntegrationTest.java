package com.devtrack.task.api;

import java.time.LocalDate;

import com.devtrack.support.IntegrationTest;
import com.devtrack.support.TestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Metricas de progreso de un proyecto.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ProjectStatsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Un proyecto sin tareas devuelve todos los contadores a cero")
    void emptyProjectReturnsZeroes() throws Exception {
        Scenario scenario = scenarioWithRole("VIEWER");

        mockMvc.perform(get("/api/v1/projects/{projectId}/stats", scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(scenario.projectId()))
                .andExpect(jsonPath("$.totalTasks").value(0))
                .andExpect(jsonPath("$.unassigned").value(0))
                .andExpect(jsonPath("$.overdue").value(0))
                .andExpect(jsonPath("$.completionRate").value(0.0))
                .andExpect(jsonPath("$.byStatus.TODO").value(0))
                .andExpect(jsonPath("$.byStatus.DONE").value(0))
                .andExpect(jsonPath("$.byStatus.IN_PROGRESS").value(0))
                .andExpect(jsonPath("$.byStatus.CANCELLED").value(0))
                .andExpect(jsonPath("$.byPriority.MEDIUM").value(0));
    }

    @Test
    @DisplayName("Cuenta las tareas por estado y por prioridad")
    void countsByStatusAndPriority() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");

        createTask(scenario, "{\"title\":\"T1\",\"status\":\"TODO\",\"priority\":\"LOW\"}");
        createTask(scenario, "{\"title\":\"T2\",\"status\":\"TODO\",\"priority\":\"LOW\"}");
        createTask(scenario, "{\"title\":\"T3\",\"status\":\"DONE\",\"priority\":\"HIGH\"}");
        createTask(scenario, "{\"title\":\"T4\",\"status\":\"IN_PROGRESS\",\"priority\":\"MEDIUM\"}");

        mockMvc.perform(get("/api/v1/projects/{projectId}/stats", scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(4))
                .andExpect(jsonPath("$.byStatus.TODO").value(2))
                .andExpect(jsonPath("$.byStatus.DONE").value(1))
                .andExpect(jsonPath("$.byStatus.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.byStatus.CANCELLED").value(0))
                .andExpect(jsonPath("$.byPriority.LOW").value(2))
                .andExpect(jsonPath("$.byPriority.HIGH").value(1))
                .andExpect(jsonPath("$.byPriority.MEDIUM").value(1))
                .andExpect(jsonPath("$.byPriority.URGENT").value(0));
    }

    @Test
    @DisplayName("El porcentaje de completitud es la proporcion de tareas DONE")
    void completionRateIsProportionOfDone() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");

        createTask(scenario, "{\"title\":\"T1\",\"status\":\"DONE\"}");
        createTask(scenario, "{\"title\":\"T2\",\"status\":\"DONE\"}");
        createTask(scenario, "{\"title\":\"T3\",\"status\":\"TODO\"}");
        createTask(scenario, "{\"title\":\"T4\",\"status\":\"TODO\"}");

        mockMvc.perform(get("/api/v1/projects/{projectId}/stats", scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(4))
                .andExpect(jsonPath("$.completionRate").value(0.5));
    }

    @Test
    @DisplayName("Cuenta las tareas sin asignar y las asignadas al llamador")
    void countsUnassignedAndAssignedToMe() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");

        createTask(scenario, "{\"title\":\"Sin asignar\"}");

        Long assigned = TestSupport.createTaskWithBody(
                mockMvc, scenario.token(), scenario.projectId(), "{\"title\":\"Asignada\"}");
        TestSupport.createTaskWithBody(
                mockMvc,
                scenario.token(),
                scenario.projectId(),
                "{\"title\":\"Asignada a otro\",\"assigneeId\":%d}".formatted(scenario.otherMemberId()));

        mockMvc.perform(
                        put("/api/v1/projects/{projectId}/tasks/{taskId}/assignee", scenario.projectId(), assigned)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token())
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {"assigneeId":%d}
                                        """
                                        .formatted(scenario.memberId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/{projectId}/stats", scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(3))
                .andExpect(jsonPath("$.unassigned").value(1))
                .andExpect(jsonPath("$.assignedToMe").value(1));
    }

    @Test
    @DisplayName("Cuenta las tareas vencidas y las que vencen en los proximos 7 dias")
    void countsOverdueAndDueSoon() throws Exception {
        Scenario scenario = scenarioWithRole("EDITOR");
        LocalDate today = LocalDate.now();

        createTask(scenario, "{\"title\":\"Vencida\",\"dueDate\":\"%s\"}".formatted(today.minusDays(3)));
        createTask(scenario, "{\"title\":\"Vencida pero hecha\",\"status\":\"DONE\",\"dueDate\":\"%s\"}".formatted(today.minusDays(3)));
        createTask(scenario, "{\"title\":\"Proxima\",\"dueDate\":\"%s\"}".formatted(today.plusDays(3)));
        createTask(scenario, "{\"title\":\"Lejana\",\"dueDate\":\"%s\"}".formatted(today.plusDays(30)));

        mockMvc.perform(get("/api/v1/projects/{projectId}/stats", scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(1))
                .andExpect(jsonPath("$.dueWithinNext7Days").value(1));
    }

    @Test
    @DisplayName("Un VIEWER puede consultar las metricas")
    void viewerCanReadStats() throws Exception {
        Scenario scenario = scenarioWithRole("VIEWER");

        mockMvc.perform(get("/api/v1/projects/{projectId}/stats", scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(0));
    }

    @Test
    @DisplayName("Un usuario que no es miembro recibe 404")
    void nonMemberGetsNotFound() throws Exception {
        Scenario scenario = scenarioWithRole("VIEWER");
        String intruderToken = TestSupport.registerAndLogin(mockMvc, TestSupport.validPassword());

        mockMvc.perform(get("/api/v1/projects/{projectId}/stats", scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    // ---------------------------------------------------------------------------------------------

    private record Scenario(Long projectId, String token, Long memberId, Long otherMemberId) {}

    private Scenario scenarioWithRole(String role) throws Exception {
        String ownerEmail = TestSupport.uniqueEmail();
        Long ownerId = TestSupport.registerAndGetId(mockMvc, ownerEmail, TestSupport.validPassword());
        String ownerToken = TestSupport.login(mockMvc, ownerEmail, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        if ("OWNER".equals(role)) {
            Long otherId = TestSupport.registerAndGetId(
                    mockMvc, TestSupport.uniqueEmail(), TestSupport.validPassword());
            TestSupport.addMember(mockMvc, ownerToken, projectId, otherId, "EDITOR");
            return new Scenario(projectId, ownerToken, ownerId, otherId);
        }

        String memberEmail = TestSupport.uniqueEmail();
        Long memberId = TestSupport.registerAndGetId(mockMvc, memberEmail, TestSupport.validPassword());
        TestSupport.addMember(mockMvc, ownerToken, projectId, memberId, role);
        String memberToken = TestSupport.login(mockMvc, memberEmail, TestSupport.validPassword());

        Long otherId = TestSupport.registerAndGetId(
                mockMvc, TestSupport.uniqueEmail(), TestSupport.validPassword());
        TestSupport.addMember(mockMvc, ownerToken, projectId, otherId, "EDITOR");

        return new Scenario(projectId, memberToken, memberId, otherId);
    }

    private void createTask(Scenario scenario, String body) throws Exception {
        TestSupport.createTaskWithBody(mockMvc, scenario.token(), scenario.projectId(), body);
    }
}
