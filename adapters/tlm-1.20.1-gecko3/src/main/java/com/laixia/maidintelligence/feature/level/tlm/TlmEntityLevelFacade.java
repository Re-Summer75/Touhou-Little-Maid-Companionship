package com.laixia.maidintelligence.feature.level.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.level.api.ExperienceSource;
import com.laixia.maidintelligence.feature.level.api.LevelChange;
import com.laixia.maidintelligence.feature.level.api.MaidLevelApi;
import com.laixia.maidintelligence.feature.level.domain.LevelProgress;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/**
 * Narrows the TLM maid level API to the generic entity type used by commands.
 */
public final class TlmEntityLevelFacade implements MaidLevelApi<Entity> {
    private final MaidLevelApi<EntityMaid> delegate;

    public TlmEntityLevelFacade(MaidLevelApi<EntityMaid> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public LevelProgress getProgress(Entity subject) {
        return delegate.getProgress((EntityMaid) subject);
    }

    @Override
    public LevelChange awardExperience(
            Entity subject,
            int amount,
            ExperienceSource source
    ) {
        return delegate.awardExperience(
                (EntityMaid) subject,
                amount,
                source
        );
    }

    @Override
    public LevelProgress setProgress(
            Entity subject,
            int level,
            int experience
    ) {
        return delegate.setProgress(
                (EntityMaid) subject,
                level,
                experience
        );
    }
}
