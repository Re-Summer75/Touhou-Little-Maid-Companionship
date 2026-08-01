package com.laixia.maidintelligence.feature.physics.engine.spring;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProjection;
import org.joml.Vector3f;

/**
 * Keeps secondary motion centred on the current authored pose.
 *
 * <p>Idle return travel is capped in visible pixels rather than radians, so a
 * large mesh cannot snap through the pose while a small strand remains slow.
 * When wind reaches a collider suppressed by an unsatisfiable contact set, the
 * nearest legal point on the arc back to the authored pose becomes the boundary.
 * Ordinary animation and moving-collider response stay outside this fallback.</p>
 */
final class SpringAuthoredPoseAnchor {
    private static final float LIMIT_START_PIXELS = 2.0F;
    private static final float MAX_RECOVERY_PIXELS_PER_SECOND = 96.0F;
    private static final float PENETRATION_TOLERANCE = 0.01F;

    private SpringAuthoredPoseAnchor() {
    }

    static boolean undriven(
            Vector3f modelAcceleration,
            Vector3f animationAcceleration,
            float yawRate,
            Vector3f poseDriveBias
    ) {
        float x = modelAcceleration.x + animationAcceleration.x;
        float y = modelAcceleration.y + animationAcceleration.y;
        float z = modelAcceleration.z + animationAcceleration.z;
        float activitySquared = x * x + y * y + z * z
                + yawRate * yawRate + poseDriveBias.lengthSquared();
        return Float.isFinite(activitySquared) && activitySquared <= 1.0E-6F;
    }

    static boolean limitRecovery(
            Vector3f current,
            Vector3f requested,
            Vector3f authoredRest,
            float visibleLeverArm,
            float dt
    ) {
        if (!Float.isFinite(visibleLeverArm)
                || visibleLeverArm <= SpringBoneMath.EPSILON
                || !Float.isFinite(dt)
                || dt <= 0.0F
                || requested.dot(authoredRest)
                <= current.dot(authoredRest)) {
            return false;
        }
        float pixelScale = visibleLeverArm * 16.0F;
        float restDistanceSquared =
                current.distanceSquared(authoredRest) * pixelScale * pixelScale;
        if (restDistanceSquared
                <= LIMIT_START_PIXELS * LIMIT_START_PIXELS) {
            return false;
        }
        float requestedDistanceSquared =
                current.distanceSquared(requested) * pixelScale * pixelScale;
        float maximum = maximumRecoveryDistance(dt);
        if (requestedDistanceSquared <= maximum * maximum) {
            return false;
        }
        float ratio = maximum / (float) Math.sqrt(requestedDistanceSquared);
        requested.set(
                current.x + (requested.x - current.x) * ratio,
                current.y + (requested.y - current.y) * ratio,
                current.z + (requested.z - current.z) * ratio
        ).normalize();
        return true;
    }

    static boolean recoverPenetration(
            Vector3f output,
            Vector3f authoredRest,
            CollisionProjection collisions,
            CollisionScratch scratch
    ) {
        if (collisions.isClear(
                output, PENETRATION_TOLERANCE, scratch
        ) || output.distanceSquared(authoredRest) <= 1.0E-10F) {
            return false;
        }
        float requestedX = output.x;
        float requestedY = output.y;
        float requestedZ = output.z;
        float blocked = 0.0F;
        float clear = 1.0F;
        /*
         * Suppressed colliders cannot contribute a projection normal. Search the
         * short arc back to the known-legal authored pose and keep the nearest
         * legal point, so a steady gust rests on the boundary instead of cycling
         * between a penetrated wind target and a full pose reset.
         */
        for (int iteration = 0; iteration < 12; iteration++) {
            float ratio = (blocked + clear) * 0.5F;
            output.set(
                    requestedX + (authoredRest.x - requestedX) * ratio,
                    requestedY + (authoredRest.y - requestedY) * ratio,
                    requestedZ + (authoredRest.z - requestedZ) * ratio
            ).normalize();
            if (collisions.isClear(
                    output, PENETRATION_TOLERANCE, scratch
            )) {
                clear = ratio;
            } else {
                blocked = ratio;
            }
        }
        output.set(
                requestedX + (authoredRest.x - requestedX) * clear,
                requestedY + (authoredRest.y - requestedY) * clear,
                requestedZ + (authoredRest.z - requestedZ) * clear
        ).normalize();
        return true;
    }

    static float maximumRecoveryDistance(float dt) {
        return Float.isFinite(dt) && dt > 0.0F
                ? MAX_RECOVERY_PIXELS_PER_SECOND * Math.min(dt, 0.1F)
                : 0.0F;
    }
}
