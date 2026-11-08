package com.gravitycanvas.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gravitycanvas.dto.CreateTaskRequest;
import com.gravitycanvas.dto.CreateUserRequest;
import com.gravitycanvas.dto.RejectTaskRequest;
import com.gravitycanvas.dto.TaskResponseDto;
import com.gravitycanvas.dto.UpdateTaskDependenciesRequest;
import com.gravitycanvas.dto.UserDto;
import com.gravitycanvas.entity.ApprovalStatus;
import com.gravitycanvas.entity.TaskEnvironment;
import com.gravitycanvas.entity.TaskStatus;
import com.gravitycanvas.exception.GlobalExceptionHandler;
import com.gravitycanvas.exception.ResourceNotFoundException;
import com.gravitycanvas.service.TaskService;
import com.gravitycanvas.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller integration test validating request DTO Bean Validation
 * and centralized exception mapping using MockMvc.
 */
class TaskValidationAndControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    // Lightweight test stub for TaskService to ensure 100% JDK 26 compatibility without dynamic bytecode agents
    private static class StubTaskService extends TaskService {
        public StubTaskService() {
            super(null, null, null, null);
        }

        @Override
        public TaskResponseDto createTask(CreateTaskRequest req) {
            TaskResponseDto dto = new TaskResponseDto();
            dto.setId(101L);
            dto.setTitle(req.getTitle());
            dto.setStatus(TaskStatus.TODO);
            dto.setApprovalStatus(ApprovalStatus.APPROVED);
            dto.setTotalGravityScore(85.5);
            dto.setCanvasZone("FOCUS");
            return dto;
        }

        @Override
        public TaskResponseDto approveTask(Long id) {
            if (id == 999L) {
                throw new ResourceNotFoundException("Task not found with ID: " + id);
            }
            TaskResponseDto dto = new TaskResponseDto();
            dto.setId(id);
            return dto;
        }

        @Override
        public TaskResponseDto rejectTask(Long id, RejectTaskRequest req) {
            TaskResponseDto dto = new TaskResponseDto();
            dto.setId(id);
            dto.setApprovalStatus(ApprovalStatus.REJECTED);
            dto.setRejectionReason(req != null ? req.getRejectionReason() : null);
            return dto;
        }

        @Override
        public TaskResponseDto updateTaskDependencies(Long id, UpdateTaskDependenciesRequest req) {
            TaskResponseDto dto = new TaskResponseDto();
            dto.setId(id);
            dto.setDependencyIds(req.getParentTaskIds() != null ? req.getParentTaskIds() : List.of());
            return dto;
        }
    }

    // Lightweight test stub for UserService
    private static class StubUserService extends UserService {
        public StubUserService() {
            super(null, null, null);
        }

        @Override
        public UserDto createUser(CreateUserRequest req) {
            return new UserDto(1L, req.getUsername(), req.getFullName(), req.getRole(), req.getEmployeeNo(), req.getDepartment());
        }

        @Override
        public UserDto registerUser(CreateUserRequest req) {
            return new UserDto(1L, req.getUsername(), req.getFullName(), "ROLE_TEAM_MEMBER", req.getEmployeeNo(), req.getDepartment());
        }

        @Override
        public UserDto getUserByUsername(String username) {
            if ("missing_user".equals(username)) {
                throw new ResourceNotFoundException("User not found with username: " + username);
            }
            return new UserDto(1L, username, "Test User", "ROLE_PROJECT_MANAGER", "EMP-101", "Engineering");
        }
    }

    @BeforeEach
    void setUp() {
        TaskService taskService = new StubTaskService();
        UserService userService = new StubUserService();

        TaskController taskController = new TaskController(taskService);
        UserController userController = new UserController(userService);
        AuthController authController = new AuthController(userService);

        mockMvc = MockMvcBuilders
            .standaloneSetup(taskController, userController, authController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Task Creation fails with 400 when title is missing")
    void testCreateTaskMissingTitle() throws Exception {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setDescription("Fix payment timeout bug");
        request.setEnvironment(TaskEnvironment.PRODUCTION);
        request.setDeadline(LocalDateTime.now().plusDays(2));

        mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.errors.title").value("Task title is required"));
    }

    @Test
    @DisplayName("Task Creation fails with 400 when title exceeds 150 characters")
    void testCreateTaskTitleTooLong() throws Exception {
        String longTitle = "A".repeat(151);
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle(longTitle);
        request.setEnvironment(TaskEnvironment.DEVELOPMENT);
        request.setDeadline(LocalDateTime.now().plusDays(2));

        mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.title").value("Task title must not exceed 150 characters"));
    }

    @Test
    @DisplayName("Task Creation fails with 400 when target environment is null")
    void testCreateTaskMissingEnvironment() throws Exception {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("Upgrade Spring Security");
        request.setDeadline(LocalDateTime.now().plusDays(2));

        mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.environment").value("Target environment is required"));
    }

    @Test
    @DisplayName("Task Creation fails with 400 when deadline is missing")
    void testCreateTaskMissingDeadline() throws Exception {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("Valid Task Title");
        request.setEnvironment(TaskEnvironment.DEVELOPMENT);

        mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.deadline").value("Task deadline is required"));
    }

    @Test
    @DisplayName("Valid Task Creation returns 201 Created")
    void testCreateTaskSuccess() throws Exception {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("Database Migration v2.4");
        request.setDescription("Run DDL scripts on staging");
        request.setEnvironment(TaskEnvironment.STAGING);
        request.setDeadline(LocalDateTime.now().plusHours(48));

        mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(101))
            .andExpect(jsonPath("$.title").value("Database Migration v2.4"))
            .andExpect(jsonPath("$.canvasZone").value("FOCUS"))
            .andExpect(jsonPath("$.totalGravityScore").value(85.5));
    }

    @Test
    @DisplayName("Approve task with non-existent ID returns 404 Not Found")
    void testApproveTaskNotFound() throws Exception {
        mockMvc.perform(put("/api/tasks/999/approve"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message").value("Task not found with ID: 999"));
    }

    @Test
    @DisplayName("Rejecting task without a rejection reason fails with 400 Bad Request")
    void testRejectTaskBlankReason() throws Exception {
        RejectTaskRequest request = new RejectTaskRequest("");

        mockMvc.perform(put("/api/tasks/1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.rejectionReason").value("Rejection reason is required"));
    }

    @Test
    @DisplayName("User registration fails with 400 when username is blank")
    void testRegisterUserBlankUsername() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("");
        request.setPassword("StrongPass123!");
        request.setFullName("John Doe");
        request.setRole("ROLE_TEAM_MEMBER");
        request.setEmployeeNo("EMP-101");
        request.setDepartment("Engineering");

        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.username").exists());
    }

    @Test
    @DisplayName("User registration fails with 400 when password is under 8 characters")
    void testRegisterUserPasswordTooShort() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("john.doe");
        request.setPassword("Short1!");
        request.setFullName("John Doe");
        request.setRole("ROLE_TEAM_MEMBER");
        request.setEmployeeNo("EMP-101");
        request.setDepartment("Engineering");

        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.password").value("Password must be between 8 and 128 characters"));
    }

    @Test
    @DisplayName("Valid User Registration returns 201 Created")
    void testRegisterUserSuccess() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("alice.smith");
        request.setPassword("ComplexPassword123!");
        request.setFullName("Alice Smith");
        request.setRole("ROLE_TEAM_MEMBER");
        request.setEmployeeNo("EMP-202");
        request.setDepartment("QA");

        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("alice.smith"))
            .andExpect(jsonPath("$.fullName").value("Alice Smith"));
    }

    // -----------------------------------------------------------------------
    // DEPENDENCY ENDPOINT VALIDATION TESTS
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Dependency update fails with 400 when parentTaskIds is null")
    void testUpdateDependenciesFailsWhenParentTaskIdsIsNull() throws Exception {
        String jsonPayload = "{\"parentTaskIds\": null}";

        mockMvc.perform(put("/api/tasks/101/dependencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors.parentTaskIds").value("Parent task IDs list cannot be null"));
    }

    @Test
    @DisplayName("Dependency update fails with 400 when body is empty object")
    void testUpdateDependenciesFailsWhenBodyIsEmpty() throws Exception {
        String jsonPayload = "{}";

        mockMvc.perform(put("/api/tasks/101/dependencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors.parentTaskIds").value("Parent task IDs list cannot be null"));
    }

    @Test
    @DisplayName("Dependency update accepts empty list (clearing all dependencies)")
    void testUpdateDependenciesAcceptsEmptyList() throws Exception {
        UpdateTaskDependenciesRequest request = new UpdateTaskDependenciesRequest(List.of());

        mockMvc.perform(put("/api/tasks/101/dependencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(101))
            .andExpect(jsonPath("$.dependencyIds").isArray())
            .andExpect(jsonPath("$.dependencyIds.length()").value(0));
    }

    @Test
    @DisplayName("Dependency update accepts valid list of parent task IDs")
    void testUpdateDependenciesAcceptsValidList() throws Exception {
        UpdateTaskDependenciesRequest request = new UpdateTaskDependenciesRequest(List.of(1L, 2L));

        mockMvc.perform(put("/api/tasks/101/dependencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(101))
            .andExpect(jsonPath("$.dependencyIds.length()").value(2));
    }

    // -----------------------------------------------------------------------
    // AUTH CONTROLLER DELEGATION & ERROR HANDLING TESTS
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/auth/me returns 401 when unauthenticated")
    void testAuthMeReturns401WhenUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/auth/me delegates to UserService and returns user profile")
    void testAuthMeDelegatesToUserService() throws Exception {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("pm_user", "password", "ROLE_PROJECT_MANAGER");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("pm_user"))
            .andExpect(jsonPath("$.role").value("ROLE_PROJECT_MANAGER"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 404 when user is not found in database")
    void testAuthMeReturns404WhenUserNotFound() throws Exception {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("missing_user", "password", "ROLE_PROJECT_MANAGER");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("User not found with username: missing_user"));
    }
}

