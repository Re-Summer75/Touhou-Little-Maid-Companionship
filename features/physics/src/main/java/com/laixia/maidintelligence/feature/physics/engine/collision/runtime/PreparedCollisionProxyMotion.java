package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import org.joml.Vector3f;

/**
 * Accounts for motion against the measured clear-space reserves of one
 * prepared proxy. All state remains on the proxy so this helper adds no
 * per-frame allocation or object indirection.
 */
final class PreparedCollisionProxyMotion {
    /** Frame stamp of a pairing the solver is not driving frame by frame. */
    static final int NO_FRAME = Integer.MIN_VALUE;

    private PreparedCollisionProxyMotion() {
    }

    /**
     * Charges the reserves for everything that moved between frames while the
     * segment's own direction stood still: the collider under animation, the
     * pivot the segment hangs from, and any change in the scale that places
     * the sample points. Only then can a gap measured last frame still be
     * trusted this frame.
     */
    static void chargeFrameMotion(
            PreparedCollisionProxy proxy,
            Vector3f runtimePivotModel,
            float nextLeverArm,
            float nextHitRadius,
            int frame
    ) {
        boolean continuous = proxy.reserveValid
                && frame != NO_FRAME
                && frame == proxy.reserveFrame + 1;
        proxy.reserveFrame = frame;
        if (!continuous) {
            invalidate(proxy);
            return;
        }
        float px = runtimePivotModel.x - proxy.shape.referenceOrigin().x;
        float py = runtimePivotModel.y - proxy.shape.referenceOrigin().y;
        float pz = runtimePivotModel.z - proxy.shape.referenceOrigin().z;
        float dx = px - proxy.pivot.x;
        float dy = py - proxy.pivot.y;
        float dz = pz - proxy.pivot.z;
        float travel = proxy.shape.motionBound()
                + (float) Math.sqrt(dx * dx + dy * dy + dz * dz)
                + Math.abs(nextLeverArm - proxy.preparedLeverArm)
                + Math.abs(nextHitRadius - proxy.preparedHitRadius);
        proxy.tipReserve -= travel;
        proxy.bodyReserve -= travel;
    }

    /**
     * Drops the measured gaps wherever the pairing stops being updated every
     * frame. A reserve is sound only while all closing motion is charged.
     */
    static void invalidate(PreparedCollisionProxy proxy) {
        proxy.reserveValid = false;
        proxy.tipReserve = 0.0F;
        proxy.bodyReserve = 0.0F;
    }

    /**
     * Charges both reserves for the segment's own movement since they were
     * measured. The tip travels its arm times the turn; the body sample also
     * slides along the segment as the collider's bearing changes, so the
     * change in its arm is charged on top.
     */
    static void spend(
            PreparedCollisionProxy proxy,
            Vector3f direction,
            float bodyArm
    ) {
        float arm = Math.max(0.0F, bodyArm);
        if (!proxy.reserveValid) {
            proxy.tipReserve = 0.0F;
            proxy.bodyReserve = 0.0F;
            proxy.reserveValid = true;
        } else {
            float travel = proxy.reserveDirection.distance(direction);
            proxy.tipReserve -= proxy.preparedLeverArm * travel;
            proxy.bodyReserve -= proxy.reserveBodyArm * travel
                    + Math.abs(arm - proxy.reserveBodyArm);
        }
        proxy.reserveDirection.set(direction);
        proxy.reserveBodyArm = arm;
    }
}
