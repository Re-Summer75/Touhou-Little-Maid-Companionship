package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.laixia.maidintelligence.feature.physics.client.solver.SecondaryMotionConstraint;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * Transports Verlet history through reference-bone frame changes.
 */
final class SpringReferenceTransport {
    private SpringReferenceTransport() {
    }

    static void apply(
            SecondaryMotionConstraint constraint,
            int slot,
            Quaternionf referenceOrientation,
            SpringBoneState state,
            SpringBoneScratch scratch
    ) {
        int referenceIndex = constraint.referenceNodeIndex();
        if (!constraint.enabled() || referenceIndex < 0) {
            return;
        }
        prepareDelta(referenceIndex, referenceOrientation, state, scratch);
        if (state.frameReferenceAbrupt[referenceIndex]) {
            state.currentDirections[slot].set(scratch.restDirection);
            state.previousDirections[slot].set(scratch.restDirection);
            state.previousDeltaSeconds[slot] = 0.0F;
            return;
        }

        float follow = 1.0F - constraint.rotationInertiaScale();
        Quaternionf delta = state.frameReferenceDeltas[referenceIndex];
        if (follow <= SpringBoneMath.EPSILON
                || delta.x() * delta.x()
                + delta.y() * delta.y()
                + delta.z() * delta.z() <= 1.0E-12F) {
            return;
        }
        scratch.referenceTransport.identity().slerp(delta, follow);
        scratch.referenceTransport.transform(state.currentDirections[slot]);
        scratch.referenceTransport.transform(state.previousDirections[slot]);
        state.currentDirections[slot].normalize();
        state.previousDirections[slot].normalize();
    }

    private static void prepareDelta(
            int referenceIndex,
            Quaternionf referenceOrientation,
            SpringBoneState state,
            SpringBoneScratch scratch
    ) {
        if (state.frameReferenceGeneration[referenceIndex]
                == state.referenceGeneration) {
            return;
        }
        state.frameReferenceGeneration[referenceIndex] =
                state.referenceGeneration;
        Quaternionf previous =
                state.previousReferenceOrientations[referenceIndex];
        Quaternionf delta = state.frameReferenceDeltas[referenceIndex];
        if (!state.referenceInitialized[referenceIndex]) {
            previous.set(referenceOrientation);
            delta.identity();
            state.frameReferenceAbrupt[referenceIndex] = false;
            state.referenceInitialized[referenceIndex] = true;
            return;
        }

        float dot = previous.x() * referenceOrientation.x()
                + previous.y() * referenceOrientation.y()
                + previous.z() * referenceOrientation.z()
                + previous.w() * referenceOrientation.w();
        if (dot < 0.0F) {
            delta.set(
                    -referenceOrientation.x(),
                    -referenceOrientation.y(),
                    -referenceOrientation.z(),
                    -referenceOrientation.w()
            );
        } else {
            delta.set(referenceOrientation);
        }
        scratch.referenceInverse.set(previous).conjugate();
        delta.mul(scratch.referenceInverse).normalize();
        float angle = 2.0F * (float) Math.acos(
                Mth.clamp(Math.abs(delta.w()), 0.0F, 1.0F)
        );
        state.frameReferenceAbrupt[referenceIndex] =
                !Float.isFinite(angle)
                        || angle > SpringBoneMath.MAX_REFERENCE_DELTA;
        previous.set(referenceOrientation);
    }
}
