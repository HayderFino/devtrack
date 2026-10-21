package com.devtrack.shared.config;

import com.devtrack.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("El documento OpenAPI 3.1 incluye los paths y el esquema JWT")
    void apiDocsExposesContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(startsWith("3.1")))
                .andExpect(jsonPath("$.info.title").value("DevTrack API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/members'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/tasks'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/tasks/{taskId}'].patch").exists());
    }

    @Test
    @DisplayName("Los endpoints protegidos exigen bearerAuth y los publicos no")
    void securityIsPerOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/projects'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/tasks'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty());
    }

    @Test
    @DisplayName("El documento se puede descargar como YAML y la UI carga")
    void yamlAndUiAreAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }
}
