package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Preallocated orientation, reference-space, and spring direction state.
 */
final class SpringBoneState {
    final Quaternionf[] animationOrientations;
    final Quaternionf[] renderedOrientations;
    final Quaternionf[] previousReferenceOrientations;
    final Quaternionf[] frameReferenceDeltas;
    final boolean[] frameReferenceAbrupt;
    final int[] frameReferenceGeneration;
    final boolean[] referenceInitialized;

    final Vector3f[] currentDirections;
    final Vector3f[] previousDirections;
    /** Last constraint correction, used to spot a segment being squeezed. */
    final Vector3f[] projectionCorrections;
    /** Consecutive frames the correction has reversed on. */
    final int[] projectionReversals;
    final float[] projectionDamping;
    /**
     * Direction the last correction pushed the segment, and how sure of it we
     * still are, together standing in for the surface the segment rests on.
     *
     * <p>Without one, a part held against a collider by a steady force never
     * settles: the force presses it in by a frame's worth of travel, the
     * projection lifts it back out, and nothing about either changes, so it
     * repeats for as long as the pose is held. Real contact does not work that
     * way — the surface pushes back, exactly hard enough — and reading the push
     * off the correction gives us that normal for free, since a projection can
     * only ever move a segment along it.
     *
     * <p>Confidence decays rather than being dropped the moment a frame goes by
     * without a correction, because that is the normal state of resting contact:
     * the support holds the segment still, so there is nothing left to correct,
     * and forgetting the surface then would restart the cycle it prevents.
     */
    final Vector3f[] contactNormals;
    final float[] contactSupport;
    final float[] previousDeltaSeconds;
    final boolean[] initialized;
    /** Integrated buffeting phase per bone; only stepped frames advance it. */
    final double[] turbulencePhases;
    /** Stable per-bone eddy identity, resolved once to keep frames allocation-free. */
    final int[] turbulenceSeeds;
    int referenceGeneration;

    SpringBoneState(PhysicsSolverLayout layout) {
        int activeCount = layout.activeNodeCount();
        animationOrientations = new Quaternionf[activeCount];
        renderedOrientations = new Quaternionf[activeCount];
        previousReferenceOrientations = new Quaternionf[activeCount];
        frameReferenceDeltas = new Quaternionf[activeCount];
        for (int index = 0; index < activeCount; index++) {
            animationOrientations[index] = new Quaternionf();
            renderedOrientations[index] = new Quaternionf();
            previousReferenceOrientations[index] = new Quaternionf();
            frameReferenceDeltas[index] = new Quaternionf();
        }
        frameReferenceAbrupt = new boolean[activeCount];
        frameReferenceGeneration = new int[activeCount];
        referenceInitialized = new boolean[activeCount];

        int drivenCount = layout.drivenBoneCount();
        currentDirections = new Vector3f[drivenCount];
        previousDirections = new Vector3f[drivenCount];
        projectionCorrections = new Vector3f[drivenCount];
        contactNormals = new Vector3f[drivenCount];
        for (int slot = 0; slot < drivenCount; slot++) {
            currentDirections[slot] = new Vector3f();
            previousDirections[slot] = new Vector3f();
            projectionCorrections[slot] = new Vector3f();
            contactNormals[slot] = new Vector3f();
        }
        projectionReversals = new int[drivenCount];
        projectionDamping = new float[drivenCount];
        contactSupport = new float[drivenCount];
        previousDeltaSeconds = new float[drivenCount];
        initialized = new boolean[drivenCount];
        turbulencePhases = new double[drivenCount];
        turbulenceSeeds = new int[drivenCount];
        for (int index = 0; index < activeCount; index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (node.driven()) {
                turbulenceSeeds[node.drivenSlot()] =
                        node.bone().getName().hashCode();
            }
        }
    }

    void beginReferenceFrame(boolean constraintsEnabled) {
        if (!constraintsEnabled || ++referenceGeneration != 0) {
            return;
        }
        for (int index = 0;
             index < frameReferenceGeneration.length;
             index++) {
            frameReferenceGeneration[index] = 0;
        }
        referenceGeneration = 1;
    }

    boolean copyCurrentDirection(int drivenSlot, Vector3f output) {
        if (!validSlot(drivenSlot)) {
            output.zero();
            return false;
        }
        output.set(currentDirections[drivenSlot]);
        return true;
    }

    boolean copyPreviousDirection(int drivenSlot, Vector3f output) {
        if (!validSlot(drivenSlot)) {
            output.zero();
            return false;
        }
        output.set(previousDirections[drivenSlot]);
        return true;
    }

    void reset() {
        for (int slot = 0; slot < currentDirections.length; slot++) {
            currentDirections[slot].zero();
            previousDirections[slot].zero();
            projectionCorrections[slot].zero();
            contactNormals[slot].zero();
            projectionReversals[slot] = 0;
            projectionDamping[slot] = 0.0F;
            contactSupport[slot] = 0.0F;
            previousDeltaSeconds[slot] = 0.0F;
            initialized[slot] = false;
            turbulencePhases[slot] = 0.0D;
        }
        for (int index = 0;
             index < previousReferenceOrientations.length;
             index++) {
            referenceInitialized[index] = false;
            previousReferenceOrientations[index].identity();
            frameReferenceDeltas[index].identity();
            frameReferenceAbrupt[index] = false;
            frameReferenceGeneration[index] = 0;
        }
        for (Quaternionf orientation : animationOrientations) {
            orientation.identity();
        }
        for (Quaternionf orientation : renderedOrientations) {
            orientation.identity();
        }
        referenceGeneration = 0;
    }

    private boolean validSlot(int drivenSlot) {
        return drivenSlot >= 0
                && drivenSlot < currentDirections.length
                && initialized[drivenSlot];
    }
}
