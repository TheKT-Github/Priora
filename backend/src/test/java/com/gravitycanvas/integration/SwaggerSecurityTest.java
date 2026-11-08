package com.gravitycanvas.integration;

import com.gravitycanvas.entity.UserAccount;
import com.gravitycanvas.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 9 - Swagger / OpenAPI Security Test.
 *
 * Verifies:
 *   1. /v3/api-docs and /swagger-ui/** are publicly accessible (no credentials required).
 *   2. /api/tasks without credentials returns 401 Unauthorized.
 *   3. POST /api/users/register without credentials returns 400 (validation) proving it is public.
 *   4. PUT /api/tasks/{id}/approve with a TEAM_MEMBER credential returns 403 Forbidden.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = "priora.demo-data.enabled=false")
@Transactional
class SwaggerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String MEMBER_USERNAME = "swaggertest_member";
    private static final String MEMBER_PASSWORD = "Member@12345";

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername(MEMBER_USERNAME).isEmpty()) {
            UserAccount member = new UserAccount();
            member.setUsername(MEMBER_USERNAME);
            member.setPassword(passwordEncoder.encode(MEMBER_PASSWORD));
            member.setFullName("Swagger Test Member");
            member.setRole("TEAM_MEMBER");
            member.setEmployeeNo("EMP-SW001");
            member.setDepartment("QA");
            userRepository.save(member);
        }
    }

    @Test
    void v3_api_docs_is_accessible_without_authentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk());
    }

    @Test
    void swagger_ui_index_is_accessible_without_authentication() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
               .andExpect(status().isOk());
    }

    @Test
    void get_tasks_without_credentials_returns_401() throws Exception {
        mockMvc.perform(get("/api/tasks"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void post_register_without_credentials_returns_400_not_401() throws Exception {
        // Deliberately invalid body: if endpoint were protected we'd get 401 before validation.
        // A 400 proves Spring Security does NOT block this endpoint.
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
               .andExpect(status().isBadRequest());
    }

    @Test
    void put_approve_with_team_member_credentials_returns_403() throws Exception {
        String credentials = Base64.getEncoder()
                .encodeToString((MEMBER_USERNAME + ":" + MEMBER_PASSWORD).getBytes());
        // Authorization is checked before the service layer; we expect 403 not 404.
        mockMvc.perform(put("/api/tasks/999/approve")
                .header("Authorization", "Basic " + credentials))
               .andExpect(status().isForbidden());
    }
}
