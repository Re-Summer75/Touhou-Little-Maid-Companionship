package com.laixia.maidintelligence.feature.atmosphere.port;

/**
 * Allocation-free bridge to an adapter-owned mutable three-component vector.
 *
 * @param <V> mutable vector type
 */
public interface MutableWindVectorPort<V> {
    V zero(V output);

    V set(V output, float x, float y, float z);

    V copy(V output, V source);

    V lerp(V output, V target, float amount);
}
