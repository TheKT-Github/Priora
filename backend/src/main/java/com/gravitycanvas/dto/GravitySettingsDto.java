package com.gravitycanvas.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/**
 * DTO representing customizable Gravity Engine formula weights and ticket authorization policies.
 */
@Schema(description = "Gravity Engine configuration — controls which scoring sub-formulas are active and the optional manager custom factor")
public class GravitySettingsDto {

    @Schema(description = "Enable the dependency blast-radius score (logarithmic). Rewards tasks that unblock many downstream tasks.",
            example = "true")
    private boolean enableDependencyScore;

    @Schema(description = "Enable the sigmoid deadline-proximity score. Score rises sharply as the deadline approaches.",
            example = "true")
    private boolean enableDeadlineProximityScore;

    @Schema(description = "Enable the stagnation score. Rewards tasks that have been inactive for a long time.",
            example = "true")
    private boolean enableStagnationScore;

    @Schema(description = "Enable the workload-density score. Penalises assignees that already have many active tasks.",
            example = "true")
    private boolean enableWorkloadDensityScore;

    @Schema(description = "When true, tasks submitted by developers enter PENDING_APPROVAL state and require manager sign-off.",
            example = "false")
    private boolean requireApprovalForDevTasks;

    // Custom Manager Factor Fields
    @Schema(description = "Enable the manager custom factor that adds a fixed bonus to selected tasks.", example = "false")
    private boolean enableCustomFactor;

    @Size(max = 100, message = "Custom factor name must not exceed 100 characters")
    @Schema(description = "Display name for the manager custom factor (max 100 characters)", example = "High Business Value",
            nullable = true)
    private String customFactorName;

    @DecimalMin(value = "0.0", message = "Custom factor points must be non-negative")
    @DecimalMax(value = "100.0", message = "Custom factor points must not exceed 100.0")
    @Schema(description = "Bonus points added when custom factor is applied to a task (0.0–100.0)", example = "20.0",
            nullable = true)
    private Double customFactorPoints;

    public GravitySettingsDto() {}

    public GravitySettingsDto(boolean enableDependencyScore, boolean enableDeadlineProximityScore,
                              boolean enableStagnationScore, boolean enableWorkloadDensityScore,
                              boolean requireApprovalForDevTasks) {
        this.enableDependencyScore = enableDependencyScore;
        this.enableDeadlineProximityScore = enableDeadlineProximityScore;
        this.enableStagnationScore = enableStagnationScore;
        this.enableWorkloadDensityScore = enableWorkloadDensityScore;
        this.requireApprovalForDevTasks = requireApprovalForDevTasks;
    }

    public GravitySettingsDto(boolean enableDependencyScore, boolean enableDeadlineProximityScore,
                              boolean enableStagnationScore, boolean enableWorkloadDensityScore,
                              boolean requireApprovalForDevTasks, boolean enableCustomFactor,
                              String customFactorName, Double customFactorPoints) {
        this.enableDependencyScore = enableDependencyScore;
        this.enableDeadlineProximityScore = enableDeadlineProximityScore;
        this.enableStagnationScore = enableStagnationScore;
        this.enableWorkloadDensityScore = enableWorkloadDensityScore;
        this.requireApprovalForDevTasks = requireApprovalForDevTasks;
        this.enableCustomFactor = enableCustomFactor;
        this.customFactorName = customFactorName;
        this.customFactorPoints = customFactorPoints;
    }

    public boolean isEnableDependencyScore() { return enableDependencyScore; }
    public void setEnableDependencyScore(boolean enableDependencyScore) { this.enableDependencyScore = enableDependencyScore; }

    public boolean isEnableDeadlineProximityScore() { return enableDeadlineProximityScore; }
    public void setEnableDeadlineProximityScore(boolean enableDeadlineProximityScore) { this.enableDeadlineProximityScore = enableDeadlineProximityScore; }

    public boolean isEnableStagnationScore() { return enableStagnationScore; }
    public void setEnableStagnationScore(boolean enableStagnationScore) { this.enableStagnationScore = enableStagnationScore; }

    public boolean isEnableWorkloadDensityScore() { return enableWorkloadDensityScore; }
    public void setEnableWorkloadDensityScore(boolean enableWorkloadDensityScore) { this.enableWorkloadDensityScore = enableWorkloadDensityScore; }

    public boolean isRequireApprovalForDevTasks() { return requireApprovalForDevTasks; }
    public void setRequireApprovalForDevTasks(boolean requireApprovalForDevTasks) { this.requireApprovalForDevTasks = requireApprovalForDevTasks; }

    public boolean isEnableCustomFactor() { return enableCustomFactor; }
    public void setEnableCustomFactor(boolean enableCustomFactor) { this.enableCustomFactor = enableCustomFactor; }

    public String getCustomFactorName() { return customFactorName; }
    public void setCustomFactorName(String customFactorName) { this.customFactorName = customFactorName; }

    public Double getCustomFactorPoints() { return customFactorPoints; }
    public void setCustomFactorPoints(Double customFactorPoints) { this.customFactorPoints = customFactorPoints; }
}
