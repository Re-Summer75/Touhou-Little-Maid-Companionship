package com.laixia.maidintelligence.feature.status.application;

import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.feature.status.domain.DefaultHungerPolicy;
import com.laixia.maidintelligence.feature.status.domain.MaidStatusState;
import com.laixia.maidintelligence.feature.status.port.MaidStatusStore;

import java.util.Objects;

public final class MaidStatusApplication<S> implements MaidStatusApi<S> {
    private final MaidStatusStore<S> store;
    private final DefaultHungerPolicy hungerPolicy;

    public MaidStatusApplication(
            MaidStatusStore<S> store,
            DefaultHungerPolicy hungerPolicy
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.hungerPolicy = Objects.requireNonNull(hungerPolicy, "hungerPolicy");
    }

    @Override
    public MaidStatusState getState(S subject) {
        return store.get(subject);
    }

    @Override
    public boolean isSaturationFull(S subject) {
        return hungerPolicy.isSaturationFull(getState(subject));
    }

    @Override
    public void setHunger(S subject, int hunger) {
        int clamped = Math.max(0, Math.min(DefaultHungerPolicy.MAX_HUNGER, hunger));
        MaidStatusState current = getState(subject);
        MaidStatusState updated = new MaidStatusState(
                clamped,
                Math.min(current.saturation(), clamped),
                current.exhaustion()
        );
        store.set(subject, updated);
    }

    @Override
    public void restoreFromFood(
            S subject,
            int nutrition,
            float saturationModifier
    ) {
        MaidStatusState current = getState(subject);
        MaidStatusState restored = hungerPolicy.restoreFromFood(
                current,
                nutrition,
                saturationModifier
        );
        if (!restored.equals(current)) {
            store.set(subject, restored);
        }
    }
}
