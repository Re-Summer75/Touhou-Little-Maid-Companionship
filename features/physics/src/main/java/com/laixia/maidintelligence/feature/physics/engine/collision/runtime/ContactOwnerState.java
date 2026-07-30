package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

/**
 * Collision-runtime ownership of the responder retained during hysteresis.
 *
 * <p>The stored owner is a stable baked proxy index, not a frame-local culling
 * slot. Only collision runtime mutates this state.</p>
 */
public final class ContactOwnerState {
    private int seriesResponderSlot = -1;
    private int ownerProxyIndex = -1;

    public int ownerProxyIndex() {
        return ownerProxyIndex;
    }

    public boolean hasOwner() {
        return ownerProxyIndex >= 0;
    }

    void beginSeries() {
        seriesResponderSlot = -1;
    }

    void recordResponder(int liveSlot) {
        seriesResponderSlot = liveSlot;
    }

    void clearResponder() {
        seriesResponderSlot = -1;
    }

    int seriesResponderSlot() {
        return seriesResponderSlot;
    }

    void retain(int proxyIndex) {
        ownerProxyIndex = proxyIndex;
        seriesResponderSlot = -1;
    }

    void release() {
        ownerProxyIndex = -1;
    }

    void reset() {
        seriesResponderSlot = -1;
        ownerProxyIndex = -1;
    }
}
