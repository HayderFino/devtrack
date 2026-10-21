package com.devtrack.shared.config;

import com.devtrack.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
class OpenApiDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Con springdoc deshabilitado el documento y la UI devuelven 404")
    void apiDocsIsDisabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }
}
