package com.gravitycanvas.entity;

/**
 * Defines deployment environments for tasks.
 * Used by the Gravity Calculator engine to scale severity.
 */
public enum TaskEnvironment {
    PRODUCTION(1.5),
    STAGING(1.2),
    DEVELOPMENT(1.0);

    private final double multiplier;

    TaskEnvironment(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }
}
