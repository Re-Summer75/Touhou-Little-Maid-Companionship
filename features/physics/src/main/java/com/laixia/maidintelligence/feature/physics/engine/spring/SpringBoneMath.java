package com.laixia.maidintelligence.feature.physics.engine.spring;

import com.laixia.maidintelligence.feature.physics.engine.PhysicsMath;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Shared spring constants and allocation-free scalar helpers.
 */
public final class SpringBoneMath {
    static final double STIFFNESS = 6.0D;
    static final float GRAVITY_POWER = 0.9F;
    static final float DRAG = 0.35F;
    static final float INERTIA_GAIN = 7.0F;
    static final float TURN_GAIN = 3.0F;

    static final float MAX_DEFLECT_X = 0.6F;
    static final float MAX_DEFLECT_Y = 0.6F;
    static final float MAX_DEFLECT_Z = 0.6F;

    static final float REFERENCE_DELTA_SECONDS = 1.0F / 60.0F;
    static final float EPSILON = 1.0E-5F;
    static final float MAX_REFERENCE_DELTA =
            (float) Math.toRadians(75.0D);

    private SpringBoneMath() {
    }

    public static float dragRetention(float drag, float dt) {
        float clampedDrag = PhysicsMath.clamp(drag, 0.0F, 0.95F);
        if (dt <= 0.0F) {
            return 1.0F;
        }
        return (float) Math.pow(
                1.0F - clampedDrag,
                dt / REFERENCE_DELTA_SECONDS
        );
    }

    static float clampAbs(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    static void eulerZYXInto(Quaternionf rotation, Vector3f output) {
        float x = rotation.x;
        float y = rotation.y;
        float z = rotation.z;
        float w = rotation.w;
        output.set(
                (float) Math.atan2(
                        2.0F * (w * x + y * z),
                        1.0F - 2.0F * (x * x + y * y)
                ),
                (float) Math.asin(PhysicsMath.clamp(
                        2.0F * (w * y - z * x),
                        -1.0F,
                        1.0F
                )),
                (float) Math.atan2(
                        2.0F * (w * z + x * y),
                        1.0F - 2.0F * (y * y + z * z)
                )
        );
    }
}
