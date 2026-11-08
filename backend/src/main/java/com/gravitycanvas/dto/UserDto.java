package com.gravitycanvas.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lightweight user profile embedded in task responses")
public class UserDto {
    @Schema(description = "User primary key", example = "7")
    private Long id;
    @Schema(description = "Login username", example = "alice.dev")
    private String username;
    @Schema(description = "User's display name", example = "Alice Developer")
    private String fullName;
    @Schema(description = "Assigned system role", example = "TEAM_MEMBER")
    private String role;
    @Schema(description = "Company employee number", example = "EMP-00142")
    private String employeeNo;
    @Schema(description = "Department", example = "Engineering")
    private String department;

    public UserDto() {}

    public UserDto(Long id, String username, String fullName, String role) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
    }

    public UserDto(Long id, String username, String fullName, String role, String employeeNo, String department) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.employeeNo = employeeNo;
        this.department = department;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
}

