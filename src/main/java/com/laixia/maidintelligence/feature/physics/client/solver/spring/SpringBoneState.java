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
    final float[] projectionDamping;
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
        for (int slot = 0; slot < drivenCount; slot++) {
            currentDirections[slot] = new Vector3f();
            previousDirections[slot] = new Vector3f();
            projectionCorrections[slot] = new Vector3f();
        }
        projectionDamping = new float[drivenCount];
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
            projectionDamping[slot] = 0.0F;
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
