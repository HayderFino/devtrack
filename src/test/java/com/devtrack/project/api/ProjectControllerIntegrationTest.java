package com.devtrack.project.api;

import com.devtrack.support.IntegrationTest;
import com.devtrack.support.TestSupport;
import com.devtrack.user.domain.PlatformRole;
import com.devtrack.user.domain.User;
import com.devtrack.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de proyectos y membresia, incluyendo la matriz de autorizacion.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ProjectControllerIntegrationTest {

    private static final String PASSWORD = TestSupport.validPassword();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Crear un proyecto devuelve 201 y el creador es OWNER")
    void createProjectReturnsCreated() throws Exception {
        String token = TestSupport.registerAndLogin(mockMvc, PASSWORD);
        String name = TestSupport.uniqueProjectName();

        mockMvc.perform(post("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Descripcion"}
                                """
                                .formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", startsWith("/api/v1/projects/")))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.ownerId").isNumber());
    }

    @Test
    @DisplayName("Un no miembro recibe 404 igual que con un proyecto inexistente")
    void nonMemberGetsNotFound() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, PASSWORD);
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());
        String outsiderToken = TestSupport.registerAndLogin(mockMvc, PASSWORD);

        mockMvc.perform(get("/api/v1/projects/{id}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/projects/{id}", 999_999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    @DisplayName("El listado solo incluye proyectos donde el llamador es miembro")
    void listReturnsOnlyMemberships() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, PASSWORD);
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        String memberEmail = TestSupport.uniqueEmail();
        Long memberId = TestSupport.registerAndGetId(mockMvc, memberEmail, PASSWORD);
        String memberToken = TestSupport.login(mockMvc, memberEmail, PASSWORD);

        mockMvc.perform(get("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        TestSupport.addMember(mockMvc, ownerToken, projectId, memberId, "VIEWER");

        mockMvc.perform(get("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(projectId))
                .andExpect(jsonPath("$.content[0].role").value("VIEWER"));
    }

    @Test
    @DisplayName("Un VIEWER no puede editar el proyecto; el OWNER si")
    void viewerCannotEditProject() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, PASSWORD);
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());
        String viewerEmail = TestSupport.uniqueEmail();
        Long viewerId = TestSupport.registerAndGetId(mockMvc, viewerEmail, PASSWORD);
        String viewerToken = TestSupport.login(mockMvc, viewerEmail, PASSWORD);
        TestSupport.addMember(mockMvc, ownerToken, projectId, viewerId, "VIEWER");

        mockMvc.perform(patch("/api/v1/projects/{id}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"No permitido\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(patch("/api/v1/projects/{id}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Nombre actualizado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nombre actualizado"))
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    @DisplayName("Un EDITOR no puede gestionar miembros; el OWNER si")
    void editorCannotManageMembers() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, PASSWORD);
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        String editorEmail = TestSupport.uniqueEmail();
        Long editorId = TestSupport.registerAndGetId(mockMvc, editorEmail, PASSWORD);
        String editorToken = TestSupport.login(mockMvc, editorEmail, PASSWORD);
        TestSupport.addMember(mockMvc, ownerToken, projectId, editorId, "EDITOR");

        Long candidateId = TestSupport.registerAndGetId(mockMvc, TestSupport.uniqueEmail(), PASSWORD);

        mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(editorToken))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"role":"VIEWER"}
                                """
                                .formatted(candidateId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        TestSupport.addMember(mockMvc, ownerToken, projectId, candidateId, "VIEWER");
    }

    @Test
    @DisplayName("Anadir dos veces el mismo miembro devuelve 409")
    void duplicateMemberReturnsConflict() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, PASSWORD);
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());
        Long memberId = TestSupport.registerAndGetId(mockMvc, TestSupport.uniqueEmail(), PASSWORD);

        TestSupport.addMember(mockMvc, ownerToken, projectId, memberId, "VIEWER");

        mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"role":"EDITOR"}
                                """
                                .formatted(memberId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("Un no miembro no puede anadir miembros y recibe 404")
    void nonMemberCannotAddMembers() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, PASSWORD);
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());
        String outsiderToken = TestSupport.registerAndLogin(mockMvc, PASSWORD);
        Long candidateId = TestSupport.registerAndGetId(mockMvc, TestSupport.uniqueEmail(), PASSWORD);

        mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderToken))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"role":"VIEWER"}
                                """
                                .formatted(candidateId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    @DisplayName("El listado de miembros pone al OWNER primero")
    void memberListShowsOwnerFirst() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, PASSWORD);
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        String memberEmail = TestSupport.uniqueEmail();
        Long memberId = TestSupport.registerAndGetId(mockMvc, memberEmail, PASSWORD);
        String memberToken = TestSupport.login(mockMvc, memberEmail, PASSWORD);
        TestSupport.addMember(mockMvc, ownerToken, projectId, memberId, "VIEWER");

        mockMvc.perform(get("/api/v1/projects/{id}/members", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[1].role").value("VIEWER"))
                .andExpect(jsonPath("$[1].userId").value(memberId));
    }

    @Test
    @DisplayName("Transferir la propiedad deja un unico OWNER y el anterior pasa a EDITOR")
    void transferOwnershipKeepsSingleOwner() throws Exception {
        String ownerEmail = TestSupport.uniqueEmail();
        String ownerToken = TestSupport.registerAndLogin(mockMvc, ownerEmail, PASSWORD);
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        String newOwnerEmail = TestSupport.uniqueEmail();
        Long newOwnerId = TestSupport.registerAndGetId(mockMvc, newOwnerEmail, PASSWORD);
        String newOwnerToken = TestSupport.login(mockMvc, newOwnerEmail, PASSWORD);
        TestSupport.addMember(mockMvc, ownerToken, projectId, newOwnerId, "EDITOR");

        mockMvc.perform(put("/api/v1/projects/{id}/members/{userId}/ownership", projectId, newOwnerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"newOwnerId\":%d}".formatted(newOwnerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(newOwnerId))
                .andExpect(jsonPath("$.role").value("OWNER"));

        mockMvc.perform(get("/api/v1/projects/{id}/members", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(newOwnerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].userId").value(newOwnerId))
                .andExpect(jsonPath("$[1].role").value("EDITOR"));

        mockMvc.perform(delete("/api/v1/projects/{id}/members/{userId}", projectId, newOwnerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(newOwnerToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_CANNOT_BE_REMOVED"));
    }

    @Test
    @DisplayName("Archivar es un borrado logico y se filtra de los listados por defecto")
    void archiveIsLogicalDelete() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, PASSWORD);
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        mockMvc.perform(delete("/api/v1/projects/{id}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/{id}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        mockMvc.perform(get("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        mockMvc.perform(get("/api/v1/projects")
                        .param("archived", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(projectId))
                .andExpect(jsonPath("$.content[0].archived").value(true));
    }

    @Test
    @DisplayName("Transferir con un userId de ruta distinto al cuerpo devuelve 400")
    void transferWithMismatchedPathReturnsBadRequest() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, PASSWORD);
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());
        Long memberId = TestSupport.registerAndGetId(mockMvc, TestSupport.uniqueEmail(), PASSWORD);
        TestSupport.addMember(mockMvc, ownerToken, projectId, memberId, "EDITOR");

        mockMvc.perform(put("/api/v1/projects/{id}/members/{userId}/ownership", projectId, memberId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"newOwnerId\":999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("Un size fuera de rango devuelve 400 en vez de recortarse en silencio")
    void pageSizeIsBounded() throws Exception {
        String token = TestSupport.registerAndLogin(mockMvc, PASSWORD);

        mockMvc.perform(get("/api/v1/projects")
                        .param("size", "1000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mockMvc.perform(get("/api/v1/projects")
                        .param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mockMvc.perform(get("/api/v1/projects")
                        .param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un ADMIN de plataforma puede editar un proyecto sin ser miembro")
    void adminCanEditAnyProject() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, PASSWORD);
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        String adminEmail = TestSupport.uniqueEmail();
        TestSupport.register(mockMvc, adminEmail, PASSWORD);
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        admin.setPlatformRole(PlatformRole.ADMIN);
        userRepository.save(admin);
        String adminToken = TestSupport.login(mockMvc, adminEmail, PASSWORD);

        mockMvc.perform(patch("/api/v1/projects/{id}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Editado por admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Editado por admin"));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
