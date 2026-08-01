package com.laixia.maidintelligence.feature.physics.engine.spring;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProjection;
import org.joml.Vector3f;

/**
 * Escapes a path-dependent collision lock without weakening ordinary contact.
 *
 * <p>The near-authored legal pose is remembered before a large displacement.
 * If the shortest route back crosses a collider, position projection otherwise
 * rejects the spring's recovery step forever and parks the segment on the wrong
 * side. After that cancellation persists, recovery walks directly to the cached
 * target while discarding stale contact velocity.
 */
final class SpringInterlockRecovery {
    /** Only severe displacement can qualify; ordinary drape stays untouched. */
    private static final float MINIMUM_REST_DOT =
            0.57357645F; // cos(55 degrees)
    private static final float MINIMUM_VISIBLE_OFFSET_PIXELS = 4.0F;
    private static final float MAXIMUM_TARGET_OFFSET_PIXELS = 1.0F;
    /** Author targets within one model pixel remain preferable to a remote lock. */
    private static final float TARGET_PENETRATION_TOLERANCE = 1.0F / 16.0F;
    private static final float BLOCKED_SECONDS = 0.25F;
    private static final float RECOVERY_RADIANS_PER_SECOND = 10.0F;
    private static final float PROGRESS_EPSILON = 1.0E-5F;
    private static final float STALL_EPSILON = 2.0E-5F;
    private static final float FINISHED_RADIANS = 1.0E-3F;

    private SpringInterlockRecovery() {
    }

    static boolean apply(
            int slot,
            Vector3f committedDirection,
            Vector3f integratedDirection,
            Vector3f projectedDirection,
            Vector3f restDirection,
            float runtimeLeverArm,
            boolean collisionCorrected,
            float dt,
            CollisionProjection collisions,
            CollisionScratch scratch,
            SpringRecoveryState state
    ) {
        float step = finiteStep(dt);
        Vector3f target = resolveTarget(
                slot,
                projectedDirection,
                restDirection,
                runtimeLeverArm,
                collisions,
                scratch,
                state
        );
        if (target == null) {
            state.clear(slot);
            return false;
        }
        if (state.active[slot]) {
            return advance(
                    slot,
                    committedDirection,
                    target,
                    projectedDirection,
                    step,
                    runtimeLeverArm,
                    state
            );
        }
        if (step <= 0.0F
                || !collisionCorrected
                || !blocked(
                committedDirection,
                integratedDirection,
                projectedDirection,
                target,
                runtimeLeverArm
        )) {
            state.blockedSeconds[slot] = 0.0F;
            return false;
        }
        float blocked = state.blockedSeconds[slot] + step;
        state.blockedSeconds[slot] = blocked;
        if (blocked < BLOCKED_SECONDS) {
            return false;
        }
        state.active[slot] = true;
        return advance(
                slot,
                committedDirection,
                target,
                projectedDirection,
                step,
                runtimeLeverArm,
                state
        );
    }

    private static Vector3f resolveTarget(
            int slot,
            Vector3f projectedDirection,
            Vector3f restDirection,
            float runtimeLeverArm,
            CollisionProjection collisions,
            CollisionScratch scratch,
            SpringRecoveryState state
    ) {
        if (state.targetValid[slot]) {
            SpringIntegrationDamper.transportInto(
                    state.targetRestDirections[slot],
                    restDirection,
                    state.authoredTargets[slot],
                    state.authoredTargets[slot]
            );
            state.targetRestDirections[slot].set(restDirection);
            return state.authoredTargets[slot];
        }
        float pixelScale = Math.max(0.0F, runtimeLeverArm) * 16.0F;
        float dot = clampDot(projectedDirection.dot(restDirection));
        float offsetSquared =
                2.0F * (1.0F - dot) * pixelScale * pixelScale;
        if (!scratch.unresolved()
                && offsetSquared <= MAXIMUM_TARGET_OFFSET_PIXELS
                * MAXIMUM_TARGET_OFFSET_PIXELS) {
            state.authoredTargets[slot].set(projectedDirection);
        } else if (targetReachable(collisions, restDirection, scratch)) {
            state.authoredTargets[slot].set(restDirection);
        } else {
            return null;
        }
        state.targetRestDirections[slot].set(restDirection);
        state.targetValid[slot] = true;
        return state.authoredTargets[slot];
    }

    private static boolean blocked(
            Vector3f committed,
            Vector3f integrated,
            Vector3f projected,
            Vector3f rest,
            float runtimeLeverArm
    ) {
        float committedDot = clampDot(committed.dot(rest));
        float pixelScale = Math.max(0.0F, runtimeLeverArm) * 16.0F;
        float visibleOffsetSquared =
                2.0F * (1.0F - committedDot) * pixelScale * pixelScale;
        if (committedDot >= MINIMUM_REST_DOT
                && visibleOffsetSquared
                < MINIMUM_VISIBLE_OFFSET_PIXELS
                * MINIMUM_VISIBLE_OFFSET_PIXELS) {
            return false;
        }
        float integratedDot = clampDot(integrated.dot(rest));
        float projectedDot = clampDot(projected.dot(rest));
        return integratedDot > committedDot + PROGRESS_EPSILON
                && projectedDot < integratedDot - PROGRESS_EPSILON
                && projectedDot <= committedDot + STALL_EPSILON;
    }

    private static boolean targetReachable(
            CollisionProjection collisions,
            Vector3f restDirection,
            CollisionScratch scratch
    ) {
        return collisions.isClear(
                restDirection,
                TARGET_PENETRATION_TOLERANCE,
                scratch
        );
    }

    private static boolean advance(
            int slot,
            Vector3f current,
            Vector3f rest,
            Vector3f output,
            float dt,
            float runtimeLeverArm,
            SpringRecoveryState state
    ) {
        if (dt <= 0.0F) {
            output.set(current);
            return false;
        }
        float dot = clampDot(current.dot(rest));
        float chord = (float) Math.sqrt(2.0F * (1.0F - dot));
        float angularChord = 2.0F * (float) Math.sin(
                Math.min((float) Math.PI, RECOVERY_RADIANS_PER_SECOND * dt)
                        * 0.5F
        );
        float pixelScale = Math.max(0.0F, runtimeLeverArm) * 16.0F;
        float linearChord = pixelScale > SpringBoneMath.EPSILON
                ? SpringAuthoredPoseAnchor.maximumRecoveryDistance(dt)
                / pixelScale
                : angularChord;
        float maximum = Math.min(angularChord, linearChord);
        float finished = 2.0F * (float) Math.sin(FINISHED_RADIANS * 0.5F);
        if (chord <= Math.max(finished, maximum)) {
            output.set(rest);
            state.clear(slot);
            return true;
        }
        float ratio = maximum / chord;
        output.set(
                current.x + (rest.x - current.x) * ratio,
                current.y + (rest.y - current.y) * ratio,
                current.z + (rest.z - current.z) * ratio
        );
        if (output.lengthSquared() <= SpringBoneMath.EPSILON) {
            output.set(current);
            return false;
        }
        output.normalize();
        return true;
    }

    private static float finiteStep(float dt) {
        return Float.isFinite(dt)
                ? Math.max(0.0F, Math.min(dt, 0.1F))
                : 0.0F;
    }

    private static float clampDot(float value) {
        return Math.max(-1.0F, Math.min(1.0F, value));
    }
}
