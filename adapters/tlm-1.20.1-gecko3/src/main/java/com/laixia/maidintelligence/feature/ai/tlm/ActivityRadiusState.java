package com.laixia.maidintelligence.feature.ai.tlm;

import com.laixia.maidintelligence.feature.ai.domain.OwnerMotionTracker;

/**
 * Transient per-maid state; never serialized into TLM entity data.
 */
public final class ActivityRadiusState {
    private final OwnerMotionTracker ownerMotion = new OwnerMotionTracker();
    private float lastEffectiveRadius = Float.NaN;

    public OwnerMotionTracker ownerMotion() {
        return ownerMotion;
    }

    public boolean updateEffectiveRadius(float effectiveRadius) {
        if (Float.floatToIntBits(lastEffectiveRadius)
                == Float.floatToIntBits(effectiveRadius)) {
            return false;
        }
        lastEffectiveRadius = effectiveRadius;
        return true;
    }

    public void reset() {
        ownerMotion.reset();
        lastEffectiveRadius = Float.NaN;
    }
}
