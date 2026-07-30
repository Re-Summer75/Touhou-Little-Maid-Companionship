package com.laixia.maidintelligence.feature.atmosphere.client.wind;

import com.laixia.maidintelligence.feature.atmosphere.port.MutableWindVectorPort;
import org.joml.Vector3f;

/**
 * Zero-allocation bridge between the pure wind field and the physics vector.
 */
public enum JomlWindVectorPort
        implements MutableWindVectorPort<Vector3f> {
    INSTANCE;

    @Override
    public Vector3f zero(Vector3f output) {
        return output.zero();
    }

    @Override
    public Vector3f set(
            Vector3f output,
            float x,
            float y,
            float z
    ) {
        return output.set(x, y, z);
    }

    @Override
    public Vector3f copy(Vector3f output, Vector3f source) {
        return output.set(source);
    }

    @Override
    public Vector3f lerp(
            Vector3f output,
            Vector3f target,
            float amount
    ) {
        // Delegate interpolation to JOML to retain the established float path.
        return output.lerp(target, amount);
    }
}
