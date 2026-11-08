package com.gravitycanvas.controller;

import com.gravitycanvas.dto.ChangePasswordRequest;
import com.gravitycanvas.dto.CreateUserRequest;
import com.gravitycanvas.dto.ErrorResponse;
import com.gravitycanvas.dto.UserDto;
import com.gravitycanvas.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller exposing operations for User Accounts, Admin User Creation, and Double-Validation.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "User Management", description = "Endpoints for user registration, user directory listing, admin provisioning, and credential updates.")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /api/users
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD, ROLE_TEAM_MEMBER, ROLE_TESTER
     */
    @Operation(
        summary = "List all registered users",
        description = "Retrieves the complete user directory for assignment selectors and profile displays. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD, ROLE_TEAM_MEMBER, ROLE_TESTER]"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved user list",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = UserDto.class)))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * POST /api/users/register
     * Public Registration Endpoint. Delegates to userService.registerUser() which
     * ALWAYS assigns ROLE_TEAM_MEMBER regardless of any role field in the request body.
     */
    @Operation(
        summary = "Public self-service user registration",
        description = "Public endpoint allowing self-registration for new users. Note: Public registration ALWAYS assigns ROLE_TEAM_MEMBER for security; any requested role in payload is discarded.",
        security = {} // Explicitly public, no authentication required
    )
    @SecurityRequirements // Clears global security requirement for this endpoint in Swagger
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "User registered successfully with ROLE_TEAM_MEMBER",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDto.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Validation Error - Invalid username, password complexity failure, or duplicate username",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @PostMapping("/register")
    public ResponseEntity<UserDto> registerUser(@Valid @RequestBody CreateUserRequest request) {
        UserDto created = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * POST /api/users
     * Allowed Roles: ROLE_PROJECT_MANAGER
     * Project Manager creates credentials for Developers, Leads, or Testers.
     */
    @Operation(
        summary = "Admin user provisioning (Project Manager only)",
        description = "Allows a Project Manager to provision credentials with specific roles (ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD, ROLE_TEAM_MEMBER, ROLE_TESTER). [Required Role: ROLE_PROJECT_MANAGER]"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "User created successfully with assigned role",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDto.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Validation Error - Invalid role or field constraints violated",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Requires ROLE_PROJECT_MANAGER privilege",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDto created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/users/change-password
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD, ROLE_TEAM_MEMBER, ROLE_TESTER
     * Authenticated user changes their own password.
     */
    @Operation(
        summary = "Change own password",
        description = "Allows an authenticated user to change their own password by verifying current password and setting a new OWASP-compliant password. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD, ROLE_TEAM_MEMBER, ROLE_TESTER]"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "204",
            description = "Password updated successfully"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Bad Request - Incorrect current password or new password complexity failure",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        userService.changePassword(currentUsername, request);
        return ResponseEntity.noContent().build();
    }
}
