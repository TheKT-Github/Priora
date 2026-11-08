package com.gravitycanvas.entity;

import jakarta.persistence.*;

/**
 * User Account entity for System Users (Admin, Managers, Developers).
 * Stores credentials, roles, employee numbers, and department details.
 */
@Entity
@Table(name = "USERS")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(name = "ROLE", nullable = false, length = 30)
    private String role; // "ROLE_ADMIN", "ROLE_MANAGER", "ROLE_TEAM_MEMBER"

    @Column(name = "FULL_NAME", nullable = false, length = 100)
    private String fullName;

    @Column(name = "EMPLOYEE_NO", length = 50)
    private String employeeNo;

    @Column(name = "DEPARTMENT", length = 100)
    private String department;

    public UserAccount() {}

    public UserAccount(Long id, String username, String password, String role, String fullName) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.fullName = fullName;
    }

    public UserAccount(Long id, String username, String password, String role, String fullName, String employeeNo, String department) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.fullName = fullName;
        this.employeeNo = employeeNo;
        this.department = department;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
}
