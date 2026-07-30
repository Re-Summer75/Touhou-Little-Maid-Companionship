package com.laixia.maidintelligence.feature.physics.engine.spring;

import org.joml.Vector3f;

/**
 * Preallocated Verlet directions and per-step diagnostics.
 */
final class SpringIntegrationState {
    final Vector3f[] currentDirections;
    final Vector3f[] previousDirections;
    final Vector3f[] restDirections;
    final float[] previousDeltaSeconds;
    final boolean[] initialized;
    final float[] lastIntegratorStep;
    final float[] lastProjectionStep;
    final int[] lastProjectionSource;

    SpringIntegrationState(int count) {
        currentDirections = vectors(count);
        previousDirections = vectors(count);
        restDirections = vectors(count);
        previousDeltaSeconds = new float[count];
        initialized = new boolean[count];
        lastIntegratorStep = new float[count];
        lastProjectionStep = new float[count];
        lastProjectionSource = new int[count];
    }

    void reset() {
        for (int slot = 0; slot < currentDirections.length; slot++) {
            currentDirections[slot].zero();
            previousDirections[slot].zero();
            restDirections[slot].zero();
            previousDeltaSeconds[slot] = 0.0F;
            initialized[slot] = false;
            lastIntegratorStep[slot] = 0.0F;
            lastProjectionStep[slot] = 0.0F;
            lastProjectionSource[slot] = 0;
        }
    }

    boolean copyCurrentDirection(int slot, Vector3f output) {
        if (!valid(slot)) {
            output.zero();
            return false;
        }
        output.set(currentDirections[slot]);
        return true;
    }

    boolean copyPreviousDirection(int slot, Vector3f output) {
        if (!valid(slot)) {
            output.zero();
            return false;
        }
        output.set(previousDirections[slot]);
        return true;
    }

    private boolean valid(int slot) {
        return slot >= 0 && slot < currentDirections.length
                && initialized[slot];
    }

    private static Vector3f[] vectors(int count) {
        Vector3f[] vectors = new Vector3f[count];
        for (int index = 0; index < count; index++) {
            vectors[index] = new Vector3f();
        }
        return vectors;
    }
}
