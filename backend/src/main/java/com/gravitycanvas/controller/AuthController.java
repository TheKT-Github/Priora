package com.gravitycanvas.controller;

import com.gravitycanvas.dto.ErrorResponse;
import com.gravitycanvas.dto.UserDto;
import com.gravitycanvas.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller providing user context endpoints for authentication state verification.
 * Delegates user retrieval and business logic to UserService.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Authentication", description = "Endpoints for verifying authentication context and retrieving the authenticated user profile.")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /api/auth/me
     * Returns current authenticated user profile and assigned security role.
     */
    @Operation(
        summary = "Get current authenticated user profile",
        description = "Returns the UserDto for the currently authenticated principal via HTTP Basic Auth. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD, ROLE_TEAM_MEMBER, ROLE_TESTER]"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved authenticated user profile",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDto.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Missing or invalid credentials",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @GetMapping("/auth/me")
    public ResponseEntity<UserDto> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }

        String username = auth.getName();
        UserDto userDto = userService.getUserByUsername(username);

        return ResponseEntity.ok(userDto);
    }
}
