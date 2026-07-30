package com.laixia.maidintelligence.feature.interaction.domain;

/**
 * Normalized target patch on a selected face plane.
 */
public final class MouthTargetRegion {
    public static final float MIN_U = 0.30F;
    public static final float MAX_U = 0.70F;
    public static final float MIN_V = 0.00F;
    public static final float MAX_V = 0.20F;
    public static final float CENTER_U = (MIN_U + MAX_U) * 0.5F;
    public static final float CENTER_V = (MIN_V + MAX_V) * 0.5F;

    private MouthTargetRegion() {
    }

    public static boolean contains(float u, float v) {
        return Float.isFinite(u)
                && Float.isFinite(v)
                && u >= MIN_U
                && u <= MAX_U
                && v >= MIN_V
                && v <= MAX_V;
    }
}
