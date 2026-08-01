package com.laixia.maidintelligence.feature.physics.engine.spring;

import org.joml.Vector3f;

/**
 * Per-segment history for escaping a collision path that cannot reach rest.
 */
final class SpringRecoveryState {
    final float[] blockedSeconds;
    final boolean[] active;
    final Vector3f[] authoredTargets;
    final Vector3f[] targetRestDirections;
    final boolean[] targetValid;

    SpringRecoveryState(int count) {
        blockedSeconds = new float[count];
        active = new boolean[count];
        authoredTargets = new Vector3f[count];
        targetRestDirections = new Vector3f[count];
        targetValid = new boolean[count];
        for (int slot = 0; slot < count; slot++) {
            authoredTargets[slot] = new Vector3f();
            targetRestDirections[slot] = new Vector3f();
        }
    }

    void clear(int slot) {
        blockedSeconds[slot] = 0.0F;
        active[slot] = false;
    }

    void reset() {
        for (int slot = 0; slot < blockedSeconds.length; slot++) {
            clear(slot);
            authoredTargets[slot].zero();
            targetRestDirections[slot].zero();
            targetValid[slot] = false;
        }
    }
}
