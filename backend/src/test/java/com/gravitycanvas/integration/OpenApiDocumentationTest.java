package com.gravitycanvas.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 9 - OpenAPI Documentation Test.
 *
 * Verifies that the springdoc-openapi endpoint is live and that the generated
 * OpenAPI JSON document contains the expected tags and security scheme.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openapi_docs_endpoint_returns_200() throws Exception {
        mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk());
    }

    @Test
    void openapi_json_contains_all_expected_api_tags() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                                   .andExpect(status().isOk())
                                   .andReturn();
        String json = result.getResponse().getContentAsString();
        assertThat(json).as("Should contain 'Authentication' tag").contains("Authentication");
        assertThat(json).as("Should contain 'User Management' tag").contains("User Management");
        assertThat(json).as("Should contain 'Task Management' tag").contains("Task Management");
        assertThat(json).as("Should contain 'Gravity Engine' tag").contains("Gravity Engine");
    }

    @Test
    void openapi_json_contains_basicAuth_security_scheme() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                                   .andExpect(status().isOk())
                                   .andReturn();
        String json = result.getResponse().getContentAsString();
        assertThat(json).as("Should declare the 'basicAuth' security scheme").contains("basicAuth");
        assertThat(json).as("Should declare http type for basicAuth").contains("\"type\":\"http\"");
    }

    @Test
    void openapi_json_contains_priora_api_title() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                                   .andExpect(status().isOk())
                                   .andReturn();
        String json = result.getResponse().getContentAsString();
        assertThat(json).as("Should contain the application title").contains("Priora");
    }

    @Test
    void swagger_ui_html_is_accessible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
               .andExpect(status().isOk());
    }
}
