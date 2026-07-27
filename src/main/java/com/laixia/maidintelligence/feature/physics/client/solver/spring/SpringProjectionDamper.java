package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import org.joml.Vector3f;

/**
 * Settles a segment that two constraints cannot both satisfy.
 *
 * <p>A projection solves for a legal pose, but a squeezed segment has none: a
 * seated pose can close a gap below the thickness of what hangs in it, and two
 * colliders then demand opposite things. Relaxation answers whichever spoke
 * last, the ranking that picks it is redone from the live pose every frame, and
 * the segment ends up hopping between two surfaces at frame rate. That reads as
 * a buzz, and the rate limit only bounds how far each hop travels.
 *
 * <p>The signature of that state is a correction that reverses every frame, so
 * this watches for it and, while it lasts, applies successively less of what
 * the projection asks for. Alternating demands then converge on the pose in
 * between — overlapping both colliders a little, which is what a squeeze
 * physically is — instead of oscillating between them. One-sided contact never
 * reverses and is left at full strength.
 */
final class SpringProjectionDamper {
    /** How long a settled segment takes to regain full response. */
    private static final float RECOVERY_SECONDS = 0.25F;
    /** Response floor, low enough to stop a reversal within a few frames. */
    private static final float MINIMUM_RESPONSE = 0.12F;
    private static final float RISE = 0.5F;
    /** Below this a correction is numerical noise rather than a demand. */
    private static final float SIGNIFICANT = 1.0E-4F;
    /**
     * Consecutive reversing frames before the segment counts as stuck.
     *
     * <p>A single reversal is not evidence of anything: a collider that sweeps
     * across a segment and back — a leg while walking — reverses once per
     * stride, and damping on that basis is what makes a skirt stop reacting to
     * being kicked, which is the one thing it most needs to react to. An
     * unsatisfiable squeeze is a period-2 cycle instead: it reverses on every
     * single frame, so requiring a streak separates the two without needing to
     * measure anything about the collider.
     */
    private static final int SUSTAINED = 3;

    private SpringProjectionDamper() {
    }

    static void apply(
            int drivenSlot,
            Vector3f before,
            Vector3f after,
            float dt,
            SpringBoneState state
    ) {
        Vector3f previous = state.projectionCorrections[drivenSlot];
        float cx = after.x - before.x;
        float cy = after.y - before.y;
        float cz = after.z - before.z;
        if (cx * cx + cy * cy + cz * cz <= SIGNIFICANT * SIGNIFICANT) {
            state.projectionReversals[drivenSlot] = 0;
            state.projectionDamping[drivenSlot] = decay(
                    state.projectionDamping[drivenSlot],
                    dt
            );
            previous.zero();
            return;
        }
        boolean reversed = previous.lengthSquared()
                > SIGNIFICANT * SIGNIFICANT
                && cx * previous.x + cy * previous.y + cz * previous.z < 0.0F;
        int streak = reversed ? state.projectionReversals[drivenSlot] + 1 : 0;
        state.projectionReversals[drivenSlot] = streak;
        float damping = streak >= SUSTAINED
                ? Math.min(1.0F, state.projectionDamping[drivenSlot] + RISE)
                : decay(state.projectionDamping[drivenSlot], dt);
        state.projectionDamping[drivenSlot] = damping;
        // Detection reads the demand, not the damped answer to it.
        previous.set(cx, cy, cz);
        if (damping <= 0.0F) {
            return;
        }
        float response = 1.0F - (1.0F - MINIMUM_RESPONSE) * damping;
        after.set(
                before.x + cx * response,
                before.y + cy * response,
                before.z + cz * response
        );
        if (after.lengthSquared() <= SpringBoneMath.EPSILON) {
            after.set(before);
        } else {
            after.normalize();
        }
    }

    private static float decay(float damping, float dt) {
        if (damping <= 0.0F) {
            return 0.0F;
        }
        if (!Float.isFinite(dt) || dt <= 0.0F) {
            return damping;
        }
        float next = damping * (float) Math.exp(
                -Math.min(dt, 0.1F) / RECOVERY_SECONDS
        );
        return next <= 1.0E-3F ? 0.0F : next;
    }
}
