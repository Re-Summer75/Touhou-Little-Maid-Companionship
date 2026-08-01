package com.laixia.maidintelligence.feature.physics.engine.spring;

import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import org.joml.Vector3f;

/**
 * Settles frame-rate-locked cycles on frames untouched by projection.
 *
 * <p>The detector uses a monotonic cosine proxy for the tip offset reported by
 * the live logger, preserving its reversal sign without an acos in the hot path.
 * Once that scalar reverses four times in eight frames, the last committed
 * direction is transported with the moving rest frame. This preserves physical
 * deflection instead of freezing it in model space, which would still shake
 * whenever the animation equilibrium moved.</p>
 */
final class SpringIntegrationDamper {
    /** Cosine contracts near rest, so this sits below the logger's pixel gate. */
    private static final float MINIMUM_OFFSET_SIGNAL = 0.001F;
    private static final int REVERSAL_WINDOW = 8;
    /** Four visible turns in eight frames cannot be an ordinary half-cycle. */
    private static final int REVERSALS_TO_DAMP = 4;
    private static final float RECOVERY_SECONDS = 0.12F;

    private SpringIntegrationDamper() {
    }

    private static int record(int history, boolean reversed) {
        return ((history << 1) | (reversed ? 1 : 0))
                & ((1 << REVERSAL_WINDOW) - 1);
    }

    static boolean apply(
            PhysicsSolverLayout.Node node,
            Vector3f before,
            Vector3f after,
            boolean stepped,
            boolean projected,
            float dt,
            SpringBoneState state
    ) {
        int slot = node.drivenSlot();
        SpringOscillationState oscillation = state.oscillation;
        float pixelScale = node.kinematics().leverArm() * 16.0F;
        Vector3f rest = state.integration.restDirections[slot];
        float requestedSignal = offsetSignal(after, rest);
        if (!stepped || projected) {
            oscillation.integrationOffsetSignals[slot] = requestedSignal;
            oscillation.integrationRestDirections[slot].set(rest);
            oscillation.integrationOffsetSteps[slot] = 0.0F;
            oscillation.integrationReversalHistories[slot] = 0;
            oscillation.integrationDamping[slot] = 0.0F;
            oscillation.integrationOffsetValid[slot] = true;
            return false;
        }
        if (!oscillation.integrationOffsetValid[slot]) {
            oscillation.integrationOffsetSignals[slot] = requestedSignal;
            oscillation.integrationRestDirections[slot].set(rest);
            oscillation.integrationOffsetValid[slot] = true;
            return false;
        }

        float offsetStep = (requestedSignal
                - oscillation.integrationOffsetSignals[slot]) * pixelScale;
        float previousStep = oscillation.integrationOffsetSteps[slot];
        boolean significant = Math.abs(offsetStep) >= MINIMUM_OFFSET_SIGNAL;
        boolean reversed = significant
                && Math.abs(previousStep) >= MINIMUM_OFFSET_SIGNAL
                && offsetStep * previousStep < 0.0F;
        int history = record(
                oscillation.integrationReversalHistories[slot],
                reversed
        );
        oscillation.integrationReversalHistories[slot] = history;
        // Detection keeps the raw demand; damping must not hide a live cycle.
        oscillation.integrationOffsetSteps[slot] = offsetStep;
        boolean cycling = Integer.bitCount(history) >= REVERSALS_TO_DAMP;
        float damping = cycling
                ? 1.0F
                : decay(oscillation.integrationDamping[slot], dt);
        oscillation.integrationDamping[slot] = damping;
        if (damping <= 0.0F) {
            oscillation.integrationOffsetSignals[slot] = requestedSignal;
            oscillation.integrationRestDirections[slot].set(rest);
            return false;
        }
        float rawX = after.x;
        float rawY = after.y;
        float rawZ = after.z;
        /*
         * Blend the raw integrator answer toward the previous physical pose
         * expressed in this frame's authored rest space. Holding the old
         * model-space vector would leave animation-driven offset flips intact.
         */
        transportInto(
                oscillation.integrationRestDirections[slot],
                rest,
                before,
                after
        );
        after.set(
                rawX + (after.x - rawX) * damping,
                rawY + (after.y - rawY) * damping,
                rawZ + (after.z - rawZ) * damping
        );
        if (after.lengthSquared() <= SpringBoneMath.EPSILON) {
            after.set(before);
        } else {
            after.normalize();
        }
        oscillation.integrationOffsetSignals[slot] =
                offsetSignal(after, rest);
        oscillation.integrationRestDirections[slot].set(rest);
        return true;
    }

    private static float offsetSignal(
            Vector3f direction,
            Vector3f rest
    ) {
        /*
         * Negative cosine is monotonic with angular offset. Its frame delta,
         * scaled by lever arm, is a conservative visible-motion proxy and keeps
         * both acos and sqrt out of the per-bone render hot path.
         */
        return -Math.max(-1.0F, Math.min(1.0F, direction.dot(rest)));
    }

    static void transportInto(
            Vector3f previousRest,
            Vector3f rest,
            Vector3f direction,
            Vector3f output
    ) {
        float dot = Math.max(-1.0F, Math.min(1.0F, previousRest.dot(rest)));
        if (dot > 1.0F - 1.0E-7F) {
            output.set(direction);
            return;
        }
        if (dot < -1.0F + 1.0E-5F) {
            output.set(rest);
            return;
        }
        // Minimal rotation from previousRest to rest, expanded from Rodrigues.
        float cx = previousRest.y * rest.z - previousRest.z * rest.y;
        float cy = previousRest.z * rest.x - previousRest.x * rest.z;
        float cz = previousRest.x * rest.y - previousRest.y * rest.x;
        float crossX = cy * direction.z - cz * direction.y;
        float crossY = cz * direction.x - cx * direction.z;
        float crossZ = cx * direction.y - cy * direction.x;
        float alongCross = cx * direction.x
                + cy * direction.y
                + cz * direction.z;
        float scale = alongCross / (1.0F + dot);
        output.set(
                direction.x * dot + crossX + cx * scale,
                direction.y * dot + crossY + cy * scale,
                direction.z * dot + crossZ + cz * scale
        );
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
