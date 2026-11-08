package com.gravitycanvas.service;

/**
 * Single immutable calculation result containing all Gravity Engine breakdown metrics,
 * multipliers, total gravity score, and canvas zone classification.
 */
public record GravityCalculationResult(
    double dependencyScore,
    int directCount,
    int indirectCount,
    double deadlineProximityScore,
    long hoursUntilDeadline,
    boolean overdue,
    double stagnationScore,
    long hoursInactive,
    double workloadDensityScore,
    long assigneeActiveTasksCount,
    double blockerPenalty,
    String customFactorName,
    double customFactorScore,
    double workloadMultiplier,
    double environmentMultiplier,
    double totalGravityScore,
    String canvasZone
) {}
