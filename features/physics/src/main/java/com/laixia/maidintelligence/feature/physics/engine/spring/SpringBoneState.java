package com.laixia.maidintelligence.feature.physics.engine.spring;


import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
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

    final SpringIntegrationState integration;
    final SpringContactState contact;
    final SpringOscillationState oscillation;
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
        integration = new SpringIntegrationState(drivenCount);
        contact = new SpringContactState(drivenCount);
        oscillation = new SpringOscillationState(layout);
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
        return integration.copyCurrentDirection(drivenSlot, output);
    }

    boolean copyPreviousDirection(int drivenSlot, Vector3f output) {
        return integration.copyPreviousDirection(drivenSlot, output);
    }

    void reset() {
        integration.reset();
        contact.reset();
        oscillation.reset();
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

}
