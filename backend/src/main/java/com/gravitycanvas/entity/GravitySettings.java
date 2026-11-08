package com.gravitycanvas.entity;

import jakarta.persistence.*;

/**
 * Entity holding Manager-customized Dynamic Gravity Score Formula Settings,
 * factor checkboxes, custom weight factor, and ticket authorization policies.
 */
@Entity
@Table(name = "GRAVITY_SETTINGS")
public class GravitySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ENABLE_DEPENDENCY_SCORE", nullable = false)
    private boolean enableDependencyScore = true;

    @Column(name = "ENABLE_DEADLINE_PROXIMITY", nullable = false)
    private boolean enableDeadlineProximityScore = true;

    @Column(name = "ENABLE_STAGNATION_SCORE", nullable = false)
    private boolean enableStagnationScore = true;

    @Column(name = "ENABLE_WORKLOAD_DENSITY", nullable = false)
    private boolean enableWorkloadDensityScore = true;

    @Column(name = "REQUIRE_DEV_APPROVAL", nullable = false)
    private boolean requireApprovalForDevTasks = true;

    // Custom Manager Factor Fields
    @Column(name = "ENABLE_CUSTOM_FACTOR", nullable = false)
    private boolean enableCustomFactor = false;

    @Column(name = "CUSTOM_FACTOR_NAME", length = 100)
    private String customFactorName = "Custom Risk Weight";

    @Column(name = "CUSTOM_FACTOR_POINTS")
    private Double customFactorPoints = 0.0;

    @Version
    @Column(name = "VERSION")
    private Long version;

    public GravitySettings() {}

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public GravitySettings(Long id, boolean enableDependencyScore, boolean enableDeadlineProximityScore,
                           boolean enableStagnationScore, boolean enableWorkloadDensityScore, boolean requireApprovalForDevTasks) {
        this.id = id;
        this.enableDependencyScore = enableDependencyScore;
        this.enableDeadlineProximityScore = enableDeadlineProximityScore;
        this.enableStagnationScore = enableStagnationScore;
        this.enableWorkloadDensityScore = enableWorkloadDensityScore;
        this.requireApprovalForDevTasks = requireApprovalForDevTasks;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
