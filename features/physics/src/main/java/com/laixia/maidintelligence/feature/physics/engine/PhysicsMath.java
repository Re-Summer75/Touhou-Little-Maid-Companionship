package com.laixia.maidintelligence.feature.physics.engine;

/**
 * Small platform-neutral scalar helpers used by the frame loop.
 */
public final class PhysicsMath {
    private PhysicsMath() {
    }

    public static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }
}
