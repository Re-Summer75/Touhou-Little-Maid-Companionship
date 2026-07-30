package com.laixia.maidintelligence.feature.physics.engine.spring;

import org.joml.Vector3f;

/**
 * Persistent support normal and contact hysteresis for each driven bone.
 */
final class SpringContactState {
    final Vector3f[] normals;
    final float[] support;
    final int[] hold;

    SpringContactState(int count) {
        normals = new Vector3f[count];
        for (int slot = 0; slot < count; slot++) {
            normals[slot] = new Vector3f();
        }
        support = new float[count];
        hold = new int[count];
    }

    void reset() {
        for (int slot = 0; slot < normals.length; slot++) {
            normals[slot].zero();
            support[slot] = 0.0F;
            hold[slot] = 0;
        }
    }
}
