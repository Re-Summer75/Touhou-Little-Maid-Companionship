package com.laixia.maidintelligence.feature.atmosphere.domain;

import java.util.Objects;

/**
 * Stable wind defaults keyed by a platform-neutral dimension identifier.
 */
public final class DimensionWindProfile {
    private DimensionWindProfile() {
    }

    public static float baseStrength(String dimensionId) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        return switch (dimensionId) {
            case "minecraft:the_nether" -> 0.045F;
            case "minecraft:the_end" -> 0.090F;
            case "minecraft:overworld" -> 0.075F;
            default -> 0.060F;
        };
    }

    public static float minimumExposure(String dimensionId) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        return switch (dimensionId) {
            case "minecraft:the_nether" -> 0.25F;
            case "minecraft:the_end" -> 0.10F;
            default -> 0.0F;
        };
    }
}
