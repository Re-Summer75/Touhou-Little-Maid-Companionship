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
    final float[] previousDeltaSeconds;
    final boolean[] initialized;
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
        for (int slot = 0; slot < drivenCount; slot++) {
            currentDirections[slot] = new Vector3f();
            previousDirections[slot] = new Vector3f();
        }
        previousDeltaSeconds = new float[drivenCount];
        initialized = new boolean[drivenCount];
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
            previousDeltaSeconds[slot] = 0.0F;
            initialized[slot] = false;
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
