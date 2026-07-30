package com.laixia.maidintelligence.feature.status.api;

import com.laixia.maidintelligence.feature.status.domain.MaidStatusState;

/**
 * Reusable status operations keyed by an adapter-owned subject or stable handle.
 */
public interface MaidStatusApi<S> {
    MaidStatusState getState(S subject);

    boolean isSaturationFull(S subject);

    void setHunger(S subject, int hunger);

    void restoreFromFood(
            S subject,
            int nutrition,
            float saturationModifier
    );
}
