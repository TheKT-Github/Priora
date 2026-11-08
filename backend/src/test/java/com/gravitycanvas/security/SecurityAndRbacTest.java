package com.gravitycanvas.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gravitycanvas.dto.CreateUserRequest;
import com.gravitycanvas.dto.UserDto;
import com.gravitycanvas.entity.UserAccount;
import com.gravitycanvas.repository.UserAccountRepository;
import com.gravitycanvas.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security and RBAC integration tests.
 *
 * Uses @SpringBootTest + @AutoConfigureMockMvc so requests pass through the REAL
 * Spring Security filter chain (SecurityConfig rules, authentication, RBAC).
 * These tests genuinely verify that HTTP Basic authentication and role checks are enforced,
 * unlike standaloneSetup tests which bypass the filter chain entirely.
 *
 * Profile: dev (H2 in-memory, auto-schema creation via JPA).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = "priora.demo-data.enabled=false")
@Transactional
class SecurityAndRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    // -----------------------------------------------------------------------
    // Test fixtures: one user per role, seeded fresh per test via @Transactional
    // -----------------------------------------------------------------------

    private static final String PM_USER   = "test.pm";
    private static final String TL_USER   = "test.tl";
    private static final String DEV_USER  = "test.dev";
    private static final String TST_USER  = "test.tstr";
    private static final String RAW_PASS  = "TestPass1!";

    @BeforeEach
    void seedUsers() {
        seedUser(PM_USER,  "ROLE_PROJECT_MANAGER", "Test PM",       "EMP-901");
        seedUser(TL_USER,  "ROLE_TEAM_LEAD",       "Test TL",       "EMP-902");
        seedUser(DEV_USER, "ROLE_TEAM_MEMBER",      "Test Dev",      "EMP-903");
        seedUser(TST_USER, "ROLE_TESTER",           "Test Tester",   "EMP-904");
    }

    private void seedUser(String username, String role, String fullName, String empNo) {
        if (!userRepository.existsByUsernameIgnoreCase(username)) {
            UserAccount u = new UserAccount();
            u.setUsername(username);
            u.setPassword(passwordEncoder.encode(RAW_PASS));
            u.setRole(role);
            u.setFullName(fullName);
            u.setEmployeeNo(empNo);
            u.setDepartment("Engineering");
            userRepository.save(u);
        }
    }

    /** Produce the Authorization: Basic <base64> header value. */
    private String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    // -----------------------------------------------------------------------
    // 1. PRIVILEGE ESCALATION PREVENTION
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Public registration with ROLE_PROJECT_MANAGER in body is saved as ROLE_TEAM_MEMBER")
    void testPublicRegistrationIgnoresPrivilegedRole() throws Exception {
        CreateUserRequest req = validRequest("sec.reg1", "EMP-801");
        req.setRole("ROLE_PROJECT_MANAGER");  // crafted privileged role

        String responseBody = mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("ROLE_TEAM_MEMBER"))  // backend enforces TEAM_MEMBER
            .andReturn().getResponse().getContentAsString();

        // Verify in database: confirm the role was not ROLE_PROJECT_MANAGER
        Optional<UserAccount> saved = userRepository.findByUsername("sec.reg1");
        assertTrue(saved.isPresent(), "User should be saved");
        assertEquals("ROLE_TEAM_MEMBER", saved.get().getRole(),
            "Stored role must be ROLE_TEAM_MEMBER regardless of request body");
        assertNotEquals("ROLE_PROJECT_MANAGER", saved.get().getRole());
    }

    @Test
    @DisplayName("Public registration with ROLE_SUPERADMIN in body is saved as ROLE_TEAM_MEMBER")
    void testPublicRegistrationIgnoresFabricatedRole() throws Exception {
        CreateUserRequest req = validRequest("sec.reg2", "EMP-802");
        req.setRole("ROLE_SUPERADMIN");  // invented role

        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("ROLE_TEAM_MEMBER"));

        Optional<UserAccount> saved = userRepository.findByUsername("sec.reg2");
        assertTrue(saved.isPresent());
        assertEquals("ROLE_TEAM_MEMBER", saved.get().getRole());
    }

    @Test
    @DisplayName("Public registration with ROLE_TEAM_LEAD in body is still saved as ROLE_TEAM_MEMBER")
    void testPublicRegistrationIgnoresTeamLeadRole() throws Exception {
        CreateUserRequest req = validRequest("sec.reg3", "EMP-803");
        req.setRole("ROLE_TEAM_LEAD");

        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("ROLE_TEAM_MEMBER"));
    }

    // -----------------------------------------------------------------------
    // 2. PASSWORD SECURITY
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Password is stored as BCrypt hash, not plaintext")
    void testPasswordStoredAsBcryptHash() throws Exception {
        CreateUserRequest req = validRequest("sec.hashtest", "EMP-810");
        req.setRole("ROLE_TEAM_MEMBER");

        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated());

        Optional<UserAccount> saved = userRepository.findByUsername("sec.hashtest");
        assertTrue(saved.isPresent());
        String storedPassword = saved.get().getPassword();
        assertNotEquals(RAW_PASS, storedPassword, "Raw password must not be stored");
        assertTrue(storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$"),
            "Password must be a BCrypt hash (starts with $2a$ or $2b$)");
        assertTrue(passwordEncoder.matches(RAW_PASS, storedPassword),
            "BCrypt hash must verify against the original password");
    }

    @Test
    @DisplayName("Password field is absent from UserDto API responses")
    void testPasswordNotReturnedInApiResponse() throws Exception {
        CreateUserRequest req = validRequest("sec.nopwd", "EMP-811");
        req.setRole("ROLE_TEAM_MEMBER");

        String body = mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("password"),
            "API response must not contain any password or password hash field");
        assertFalse(body.contains("$2a$"), "BCrypt hash must not appear in response");
    }

    // -----------------------------------------------------------------------
    // 3. AUTHENTICATION: UNAUTHENTICATED ACCESS TO PROTECTED ENDPOINTS
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Unauthenticated request to GET /api/tasks returns 401")
    void testUnauthenticatedAccessToTasksReturns401() throws Exception {
        mockMvc.perform(get("/api/tasks"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to GET /api/users returns 401")
    void testUnauthenticatedAccessToUsersReturns401() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to GET /api/settings returns 401")
    void testUnauthenticatedAccessToSettingsReturns401() throws Exception {
        mockMvc.perform(get("/api/settings"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Invalid credentials return 401")
    void testInvalidCredentialsReturn401() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", basicAuth(PM_USER, "WrongPassword99!")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authenticated user can access GET /api/auth/me")
    void testAuthenticatedUserCanAccessMe() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", basicAuth(PM_USER, RAW_PASS)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(PM_USER))
            .andExpect(jsonPath("$.role").value("ROLE_PROJECT_MANAGER"));
    }

    // -----------------------------------------------------------------------
    // 4. DEPENDENCY ENDPOINT — REAL FILTER CHAIN AUTHORIZATION
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Developer (TEAM_MEMBER) receives 403 on PUT /api/tasks/{id}/dependencies")
    void testDeveloperCannotModifyDependencies() throws Exception {
        mockMvc.perform(put("/api/tasks/1/dependencies")
                .header("Authorization", basicAuth(DEV_USER, RAW_PASS))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentTaskIds\":[]}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Tester receives 403 on PUT /api/tasks/{id}/dependencies")
    void testTesterCannotModifyDependencies() throws Exception {
        mockMvc.perform(put("/api/tasks/1/dependencies")
                .header("Authorization", basicAuth(TST_USER, RAW_PASS))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentTaskIds\":[]}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Project Manager receives non-403 on PUT /api/tasks/{id}/dependencies (endpoint is accessible)")
    void testProjectManagerCanAccessDependenciesEndpoint() throws Exception {
        // Task ID 9999 does not exist, so expect 404 (ResourceNotFoundException), not 403.
        // A 404 proves the request passed the Security filter chain and reached the service.
        mockMvc.perform(put("/api/tasks/9999/dependencies")
                .header("Authorization", basicAuth(PM_USER, RAW_PASS))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentTaskIds\":[]}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Team Lead receives non-403 on PUT /api/tasks/{id}/dependencies (endpoint is accessible)")
    void testTeamLeadCanAccessDependenciesEndpoint() throws Exception {
        mockMvc.perform(put("/api/tasks/9999/dependencies")
                .header("Authorization", basicAuth(TL_USER, RAW_PASS))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentTaskIds\":[]}"))
            .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // 5. PRIVILEGED USER CREATION — ROLE WHITELIST
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Project Manager creating user with unsupported role ROLE_SUPERADMIN returns 400")
    void testAdminCreateUserWithInvalidRoleReturns400() throws Exception {
        CreateUserRequest req = validRequest("sec.badpm", "EMP-820");
        req.setRole("ROLE_SUPERADMIN");

        mockMvc.perform(post("/api/users")
                .header("Authorization", basicAuth(PM_USER, RAW_PASS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not a supported system role")));
    }

    @Test
    @DisplayName("Project Manager can create a user with ROLE_TESTER (whitelisted role)")
    void testAdminCreateUserWithValidRoleSucceeds() throws Exception {
        CreateUserRequest req = validRequest("sec.tester2", "EMP-821");
        req.setRole("ROLE_TESTER");

        mockMvc.perform(post("/api/users")
                .header("Authorization", basicAuth(PM_USER, RAW_PASS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("ROLE_TESTER"));
    }

    @Test
    @DisplayName("Developer cannot access POST /api/users (Project Manager only) — returns 403")
    void testDeveloperCannotCreateUser() throws Exception {
        CreateUserRequest req = validRequest("sec.devuser", "EMP-822");
        req.setRole("ROLE_TEAM_MEMBER");

        mockMvc.perform(post("/api/users")
                .header("Authorization", basicAuth(DEV_USER, RAW_PASS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // 6. SETTINGS AUTHORIZATION
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Developer cannot update Gravity Engine settings — returns 403")
    void testDeveloperCannotUpdateSettings() throws Exception {
        String settingsBody = "{\"enableDependencyScore\":false,\"enableDeadlineProximityScore\":true,"
            + "\"enableStagnationScore\":true,\"enableWorkloadDensityScore\":true,"
            + "\"enableCustomFactor\":false,\"customFactorName\":\"Risk\","
            + "\"customFactorPoints\":10.0,\"requireApprovalForDevTasks\":false}";

        mockMvc.perform(put("/api/settings")
                .header("Authorization", basicAuth(DEV_USER, RAW_PASS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(settingsBody))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Tester cannot update Gravity Engine settings — returns 403")
    void testTesterCannotUpdateSettings() throws Exception {
        String settingsBody = "{\"enableDependencyScore\":false,\"enableDeadlineProximityScore\":true,"
            + "\"enableStagnationScore\":true,\"enableWorkloadDensityScore\":true,"
            + "\"enableCustomFactor\":false,\"customFactorName\":\"Risk\","
            + "\"customFactorPoints\":10.0,\"requireApprovalForDevTasks\":false}";

        mockMvc.perform(put("/api/settings")
                .header("Authorization", basicAuth(TST_USER, RAW_PASS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(settingsBody))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Team Lead can read Gravity Engine settings")
    void testTeamLeadCanReadSettings() throws Exception {
        mockMvc.perform(get("/api/settings")
                .header("Authorization", basicAuth(TL_USER, RAW_PASS)))
            .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    /**
     * Creates a valid CreateUserRequest with all required fields populated.
     * Role is left at default "ROLE_TEAM_MEMBER" — override per test as needed.
     */
    private CreateUserRequest validRequest(String username, String empNo) {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername(username);
        req.setPassword(RAW_PASS);
        req.setFullName("Security Test");
        req.setRole("ROLE_TEAM_MEMBER");
        req.setEmployeeNo(empNo);
        req.setDepartment("Engineering");
        return req;
    }
}
