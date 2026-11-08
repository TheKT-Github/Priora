package com.gravitycanvas.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for provisioning or registering a new system user.
 */
@Schema(description = "Request body for registering a new user account")
public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    @Schema(description = "Unique login username (3–30 characters)", example = "alice.dev",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @Schema(description = "Account password (8–128 characters)", example = "Secure@123",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank(message = "Full Name is required")
    @Size(min = 2, max = 70, message = "Full Name must be between 2 and 70 characters")
    @Schema(description = "User's display name (2–70 characters)", example = "Alice Developer",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String fullName;

    @NotBlank(message = "Role is required")
    @Schema(description = "Role assigned on account creation. Allowed values: PROJECT_MANAGER, TEAM_LEAD, TEAM_MEMBER, TESTER",
            example = "TEAM_MEMBER", requiredMode = Schema.RequiredMode.REQUIRED)
    private String role;

    @NotBlank(message = "Employee Number is required")
    @Schema(description = "Company employee number for HR reference", example = "EMP-00142",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String employeeNo;

    @NotBlank(message = "Department is required")
    @Schema(description = "Department the user belongs to", example = "Engineering",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String department;

    public CreateUserRequest() {}

    public CreateUserRequest(String username, String password, String fullName, String role, String employeeNo, String department) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.employeeNo = employeeNo;
        this.department = department;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
}

