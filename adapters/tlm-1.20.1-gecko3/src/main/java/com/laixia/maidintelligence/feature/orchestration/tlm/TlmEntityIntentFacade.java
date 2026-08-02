package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.api.IntentMetrics;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/**
 * Narrows generic Minecraft entity entry points to the TLM runtime type.
 */
public final class TlmEntityIntentFacade
        implements MaidIntentApi<Entity> {
    private final MaidIntentApi<EntityMaid> delegate;

    public TlmEntityIntentFacade(MaidIntentApi<EntityMaid> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public boolean tick(Entity subject, long gameTime) {
        return delegate.tick((EntityMaid) subject, gameTime);
    }

    @Override
    public boolean signal(
            Entity subject,
            OrchestrationId signal,
            long gameTime,
            int ttlTicks
    ) {
        return delegate.signal(
                (EntityMaid) subject,
                signal,
                gameTime,
                ttlTicks
        );
    }

    @Override
    public IntentTrace inspect(Entity subject) {
        return delegate.inspect((EntityMaid) subject);
    }

    @Override
    public void forget(Entity subject) {
        if (subject instanceof EntityMaid maid) {
            delegate.forget(maid);
        }
    }

    @Override
    public IntentMetrics metrics() {
        return delegate.metrics();
    }

    @Override
    public void resetMetrics() {
        delegate.resetMetrics();
    }
}
