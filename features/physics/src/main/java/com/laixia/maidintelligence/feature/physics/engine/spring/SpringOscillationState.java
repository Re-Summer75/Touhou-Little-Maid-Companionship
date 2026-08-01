package com.laixia.maidintelligence.feature.physics.engine.spring;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import org.joml.Vector3f;

/**
 * Integration/projection cycle detection and deterministic wind phase state.
 */
final class SpringOscillationState {
    final float[] integrationOffsetSignals;
    final Vector3f[] integrationRestDirections;
    final float[] integrationOffsetSteps;
    final int[] integrationReversalHistories;
    final float[] integrationDamping;
    final boolean[] integrationOffsetValid;
    final Vector3f[] projectionCorrections;
    final int[] projectionReversals;
    final int[] projectionQuiet;
    final long[] projectionReentries;
    final int[] projectionCorrectionGap;
    final float[] projectionDamping;
    final double[] turbulencePhases;
    final double[] wavePhases;
    final int[] turbulenceSeeds;
    final int[] waveSeeds;
    final float[] turbulenceLags;

    SpringOscillationState(PhysicsSolverLayout layout) {
        int count = layout.drivenBoneCount();
        integrationOffsetSignals = new float[count];
        integrationRestDirections = new Vector3f[count];
        integrationOffsetSteps = new float[count];
        integrationDamping = new float[count];
        integrationOffsetValid = new boolean[count];
        projectionCorrections = new Vector3f[count];
        for (int slot = 0; slot < count; slot++) {
            integrationRestDirections[slot] = new Vector3f();
            projectionCorrections[slot] = new Vector3f();
        }
        integrationReversalHistories = new int[count];
        projectionReversals = new int[count];
        projectionQuiet = new int[count];
        projectionReentries = new long[count];
        projectionCorrectionGap = new int[count];
        projectionDamping = new float[count];
        turbulencePhases = new double[count];
        wavePhases = new double[count];
        turbulenceSeeds = new int[count];
        waveSeeds = new int[count];
        turbulenceLags = new float[count];
        seed(layout);
    }

    void reset() {
        for (int slot = 0; slot < projectionCorrections.length; slot++) {
            integrationOffsetSignals[slot] = 0.0F;
            integrationRestDirections[slot].zero();
            integrationOffsetSteps[slot] = 0.0F;
            integrationReversalHistories[slot] = 0;
            integrationDamping[slot] = 0.0F;
            integrationOffsetValid[slot] = false;
            projectionCorrections[slot].zero();
            projectionReversals[slot] = 0;
            projectionQuiet[slot] = 0;
            projectionReentries[slot] = 0L;
            projectionCorrectionGap[slot] = 0;
            projectionDamping[slot] = 0.0F;
            turbulencePhases[slot] = 0.0D;
            wavePhases[slot] = 0.0D;
        }
    }

    private void seed(PhysicsSolverLayout layout) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (!node.driven()) {
                continue;
            }
            PhysicsBoneSelectionPlan.ChainSegment segment =
                    node.decision().chainSegment();
            int slot = node.drivenSlot();
            turbulenceSeeds[slot] = node.bone().getName().hashCode();
            waveSeeds[slot] = segment.count() > 1
                    ? segment.rootPath().hashCode()
                    : turbulenceSeeds[slot];
            turbulenceLags[slot] = segment.count() > 1
                    ? (float) segment.index() / segment.count()
                    : 0.0F;
        }
    }
}
