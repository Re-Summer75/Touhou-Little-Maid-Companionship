package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.api.CompanionObservationSnapshot;
import com.laixia.maidintelligence.feature.orchestration.api.DecisionTrace;
import com.laixia.maidintelligence.feature.orchestration.api.IntentMetrics;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTraceComparison;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.Belief;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.EpisodicEvent;
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
    public IntentTrace inspectShadow(Entity subject) {
        return delegate.inspectShadow((EntityMaid) subject);
    }

    @Override
    public IntentTraceComparison compare(Entity subject) {
        return delegate.compare((EntityMaid) subject);
    }

    @Override
    public DecisionTrace inspectDecision(Entity subject) {
        return delegate.inspectDecision((EntityMaid) subject);
    }

    @Override
    public CompanionObservationSnapshot observations(
            Entity subject,
            long gameTime
    ) {
        return delegate.observations((EntityMaid) subject, gameTime);
    }

    @Override
    public boolean publishEvent(
            Entity subject,
            EpisodicEvent event,
            long gameTime
    ) {
        return delegate.publishEvent(
                (EntityMaid) subject,
                event,
                gameTime
        );
    }

    @Override
    public boolean rememberBelief(
            Entity subject,
            Belief belief,
            long gameTime
    ) {
        return delegate.rememberBelief(
                (EntityMaid) subject,
                belief,
                gameTime
        );
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
