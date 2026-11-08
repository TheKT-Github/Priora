package com.gravitycanvas.controller;

import com.gravitycanvas.dto.ErrorResponse;
import com.gravitycanvas.dto.GravitySettingsDto;
import com.gravitycanvas.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for managing customizable Gravity Engine formula factors
 * and developer ticket authorization policies.
 */
@RestController
@RequestMapping("/api/settings")
@Tag(name = "Gravity Engine Settings", description = "Endpoints for configuring dynamic gravity scoring factors and developer authorization policies.")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * GET /api/settings
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD, ROLE_TEAM_MEMBER, ROLE_TESTER
     */
    @Operation(
        summary = "Get current Gravity Engine formula settings",
        description = "Retrieves current active scoring factor toggles, custom weight values, and developer ticket approval policies. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD, ROLE_TEAM_MEMBER, ROLE_TESTER]"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Active settings retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = GravitySettingsDto.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @GetMapping
    public ResponseEntity<GravitySettingsDto> getSettings() {
        return ResponseEntity.ok(settingsService.getSettingsDto());
    }

    /**
     * PUT /api/settings
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD
     */
    @Operation(
        summary = "Update Gravity Engine formula settings",
        description = "Updates formula factor checkboxes (dependency, deadline, stagnation, workload), custom weight factors, and developer approval requirement policy. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD]"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Settings updated successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = GravitySettingsDto.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Validation Error - Invalid settings payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Requires Project Manager or Team Lead role",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @PutMapping
    public ResponseEntity<GravitySettingsDto> updateSettings(@Valid @RequestBody GravitySettingsDto request) {
        return ResponseEntity.ok(settingsService.updateSettings(request));
    }
}
