package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.MaidLearningApi;
import com.laixia.maidintelligence.feature.behavior.domain.learning.CompanionLearningProfile;
import com.laixia.maidintelligence.feature.behavior.domain.learning.LearningMode;
import net.minecraft.world.entity.Entity;

public final class TlmEntityLearningFacade
        implements MaidLearningApi<Entity> {
    private final MaidLearningApi<EntityMaid> delegate;

    public TlmEntityLearningFacade(
            MaidLearningApi<EntityMaid> delegate
    ) {
        this.delegate = java.util.Objects.requireNonNull(
                delegate,
                "delegate"
        );
    }

    @Override
    public LearningMode mode() {
        return delegate.mode();
    }

    @Override
    public CompanionLearningProfile profile(Entity subject) {
        return subject instanceof EntityMaid maid
                ? delegate.profile(maid)
                : CompanionLearningProfile.empty();
    }

    @Override
    public boolean freeze(Entity subject, boolean frozen) {
        return subject instanceof EntityMaid maid
                && delegate.freeze(maid, frozen);
    }

    @Override
    public void reset(Entity subject) {
        if (subject instanceof EntityMaid maid) {
            delegate.reset(maid);
        }
    }

    @Override
    public String export(Entity subject) {
        return subject instanceof EntityMaid maid
                ? delegate.export(maid)
                : "{}";
    }
}
