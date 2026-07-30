package com.laixia.maidintelligence.feature.atmosphere.domain;

/**
 * Reusable pure-Java output buffer for wind-field consumers.
 */
public final class WindVector {
    private float x;
    private float y;
    private float z;

    public WindVector() {
    }

    public WindVector(float x, float y, float z) {
        set(x, y, z);
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float z() {
        return z;
    }

    public WindVector set(float newX, float newY, float newZ) {
        x = newX;
        y = newY;
        z = newZ;
        return this;
    }

    public WindVector zero() {
        return set(0.0F, 0.0F, 0.0F);
    }

    public WindVector set(WindVector source) {
        return set(source.x, source.y, source.z);
    }

    public WindVector lerp(WindVector target, float amount) {
        x += (target.x - x) * amount;
        y += (target.y - y) * amount;
        z += (target.z - z) * amount;
        return this;
    }
}
