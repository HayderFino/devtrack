package com.devtrack.project.api;

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
 * Gestion de membresia de proyecto.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ProjectMemberControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Anade un miembro y devuelve 201")
    void addMemberReturnsCreated() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        Long userId = TestSupport.registerAndGetId(
                mockMvc, TestSupport.uniqueEmail(), TestSupport.validPassword());

        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"role":"EDITOR"}
                                """
                                .formatted(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.role").value("EDITOR"))
                .andExpect(jsonPath("$.email").isNotEmpty())
                .andExpect(jsonPath("$.addedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Devuelve 409 si el usuario ya es miembro")
    void addExistingMemberReturnsConflict() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        Long userId = TestSupport.registerAndGetId(
                mockMvc, TestSupport.uniqueEmail(), TestSupport.validPassword());
        TestSupport.addMember(mockMvc, ownerToken, projectId, userId, "VIEWER");

        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"role":"EDITOR"}
                                """
                                .formatted(userId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("Devuelve 404 si el usuario no existe")
    void addNonExistentUserReturnsNotFound() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":999999999,"role":"EDITOR"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("No permite asignar el rol OWNER por la ruta de membresia")
    void cannotAssignOwnerRole() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        Long userId = TestSupport.registerAndGetId(
                mockMvc, TestSupport.uniqueEmail(), TestSupport.validPassword());

        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"role":"OWNER"}
                                """
                                .formatted(userId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Cambia el rol de un miembro")
    void changeRoleUpdatesMember() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        Long userId = TestSupport.registerAndGetId(
                mockMvc, TestSupport.uniqueEmail(), TestSupport.validPassword());
        TestSupport.addMember(mockMvc, ownerToken, projectId, userId, "EDITOR");

        mockMvc.perform(patch("/api/v1/projects/{projectId}/members/{userId}", projectId, userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"VIEWER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"));
    }

    @Test
    @DisplayName("No permite degradar al propietario")
    void cannotDemoteOwner() throws Exception {
        String ownerEmail = TestSupport.uniqueEmail();
        Long ownerId = TestSupport.registerAndGetId(mockMvc, ownerEmail, TestSupport.validPassword());
        String ownerToken = TestSupport.login(mockMvc, ownerEmail, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        mockMvc.perform(patch("/api/v1/projects/{projectId}/members/{userId}", projectId, ownerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"VIEWER"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_CANNOT_BE_REMOVED"));
    }

    @Test
    @DisplayName("No permite quitar al propietario")
    void cannotRemoveOwner() throws Exception {
        String ownerEmail = TestSupport.uniqueEmail();
        Long ownerId = TestSupport.registerAndGetId(mockMvc, ownerEmail, TestSupport.validPassword());
        String ownerToken = TestSupport.login(mockMvc, ownerEmail, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        mockMvc.perform(delete("/api/v1/projects/{projectId}/members/{userId}", projectId, ownerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_CANNOT_BE_REMOVED"));
    }

    @Test
    @DisplayName("Quita a un miembro que no es el propietario")
    void removeMemberReturnsNoContent() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        Long userId = TestSupport.registerAndGetId(
                mockMvc, TestSupport.uniqueEmail(), TestSupport.validPassword());
        TestSupport.addMember(mockMvc, ownerToken, projectId, userId, "VIEWER");

        mockMvc.perform(delete("/api/v1/projects/{projectId}/members/{userId}", projectId, userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/{projectId}/members", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Transfiere la propiedad: el anterior pasa a EDITOR y el nuevo a OWNER")
    void transferOwnershipSwapsRoles() throws Exception {
        String ownerEmail = TestSupport.uniqueEmail();
        Long ownerId = TestSupport.registerAndGetId(mockMvc, ownerEmail, TestSupport.validPassword());
        String ownerToken = TestSupport.login(mockMvc, ownerEmail, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        String heirEmail = TestSupport.uniqueEmail();
        Long heirId = TestSupport.registerAndGetId(mockMvc, heirEmail, TestSupport.validPassword());
        TestSupport.addMember(mockMvc, ownerToken, projectId, heirId, "EDITOR");
        String heirToken = TestSupport.login(mockMvc, heirEmail, TestSupport.validPassword());

        mockMvc.perform(put("/api/v1/projects/{projectId}/members/{userId}/ownership", projectId, heirId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"newOwnerId":%d}
                                """
                                .formatted(heirId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(heirId));

        // El nuevo propietario puede administrar el proyecto.
        mockMvc.perform(patch("/api/v1/projects/{projectId}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + heirToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Renombrado por el nuevo propietario"}
                                """))
                .andExpect(status().isOk());

        // Y el anterior ya no puede.
        mockMvc.perform(patch("/api/v1/projects/{projectId}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Intento del antiguo propietario"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("No se puede transferir la propiedad a un usuario que no es miembro")
    void transferOwnershipToNonMemberReturnsNotFound() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        Long outsiderId = TestSupport.registerAndGetId(
                mockMvc, TestSupport.uniqueEmail(), TestSupport.validPassword());

        mockMvc.perform(put("/api/v1/projects/{projectId}/members/{userId}/ownership", projectId, outsiderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"newOwnerId":%d}
                                """
                                .formatted(outsiderId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("Los miembros se listan con el propietario primero")
    void listMembersPutsOwnerFirst() throws Exception {
        String ownerEmail = TestSupport.uniqueEmail();
        Long ownerId = TestSupport.registerAndGetId(mockMvc, ownerEmail, TestSupport.validPassword());
        String ownerToken = TestSupport.login(mockMvc, ownerEmail, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        Long userId = TestSupport.registerAndGetId(
                mockMvc, TestSupport.uniqueEmail(), TestSupport.validPassword());
        TestSupport.addMember(mockMvc, ownerToken, projectId, userId, "EDITOR");

        mockMvc.perform(get("/api/v1/projects/{projectId}/members", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(ownerId))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[1].userId").value(userId))
                .andExpect(jsonPath("$[1].role").value("EDITOR"));
    }

    @Test
    @DisplayName("Un miembro puede listar los miembros del proyecto")
    void memberCanListMembers() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        String viewerEmail = TestSupport.uniqueEmail();
        Long viewerId = TestSupport.registerAndGetId(mockMvc, viewerEmail, TestSupport.validPassword());
        TestSupport.addMember(mockMvc, ownerToken, projectId, viewerId, "VIEWER");
        String viewerToken = TestSupport.login(mockMvc, viewerEmail, TestSupport.validPassword());

        mockMvc.perform(get("/api/v1/projects/{projectId}/members", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Un no miembro recibe 404 al listar los miembros")
    void nonMemberCannotListMembers() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());

        String intruderToken = TestSupport.registerAndLogin(mockMvc, TestSupport.validPassword());

        mockMvc.perform(get("/api/v1/projects/{projectId}/members", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }
}
