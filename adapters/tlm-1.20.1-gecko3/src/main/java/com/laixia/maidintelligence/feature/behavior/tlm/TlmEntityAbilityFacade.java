package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.MaidAbilityApi;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityActivationRequest;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityActivationSource;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityDefinition;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityGrantSet;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityRuntimeState;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TlmEntityAbilityFacade
        implements MaidAbilityApi<Entity> {
    private final MaidAbilityApi<EntityMaid> delegate;

    public TlmEntityAbilityFacade(
            MaidAbilityApi<EntityMaid> delegate
    ) {
        this.delegate = java.util.Objects.requireNonNull(
                delegate,
                "delegate"
        );
    }

    @Override
    public long catalogGeneration() {
        return delegate.catalogGeneration();
    }

    @Override
    public List<AbilityDefinition> definitions() {
        return delegate.definitions();
    }

    @Override
    public AbilityGrantSet grants(Entity subject) {
        return subject instanceof EntityMaid maid
                ? delegate.grants(maid)
                : AbilityGrantSet.empty();
    }

    @Override
    public boolean grant(
            Entity subject,
            OrchestrationId ability,
            long gameTime,
            String source
    ) {
        return subject instanceof EntityMaid maid
                && delegate.grant(maid, ability, gameTime, source);
    }

    @Override
    public boolean revoke(Entity subject, OrchestrationId ability) {
        return subject instanceof EntityMaid maid
                && delegate.revoke(maid, ability);
    }

    @Override
    public boolean granted(Entity subject, OrchestrationId ability) {
        return subject instanceof EntityMaid maid
                && delegate.granted(maid, ability);
    }

    @Override
    public Optional<AbilityActivationRequest> request(
            Entity subject,
            OrchestrationId ability,
            AbilityActivationSource source,
            long gameTime
    ) {
        return subject instanceof EntityMaid maid
                ? delegate.request(
                        maid,
                        ability,
                        source,
                        gameTime
                )
                : Optional.empty();
    }

    @Override
    public Optional<AbilityActivationRequest> request(
            Entity subject,
            OrchestrationId ability,
            AbilityActivationSource source,
            long gameTime,
            UUID sharedRequestId
    ) {
        return subject instanceof EntityMaid maid
                ? delegate.request(
                        maid,
                        ability,
                        source,
                        gameTime,
                        sharedRequestId
                )
                : Optional.empty();
    }

    @Override
    public Optional<AbilityActivationRequest> activeRequest(
            Entity subject,
            OrchestrationId ability,
            long gameTime
    ) {
        return subject instanceof EntityMaid maid
                ? delegate.activeRequest(maid, ability, gameTime)
                : Optional.empty();
    }

    @Override
    public List<AbilityCandidate> candidates(
            Entity subject,
            long gameTime
    ) {
        return subject instanceof EntityMaid maid
                ? delegate.candidates(maid, gameTime)
                : List.of();
    }

    @Override
    public boolean beginExecution(
            Entity subject,
            UUID requestId,
            long gameTime
    ) {
        return subject instanceof EntityMaid maid
                && delegate.beginExecution(maid, requestId, gameTime);
    }

    @Override
    public void complete(
            Entity subject,
            UUID requestId,
            boolean succeeded,
            long gameTime
    ) {
        if (subject instanceof EntityMaid maid) {
            delegate.complete(
                    maid,
                    requestId,
                    succeeded,
                    gameTime
            );
        }
    }

    @Override
    public AbilityRuntimeState inspect(
            Entity subject,
            long gameTime
    ) {
        return subject instanceof EntityMaid maid
                ? delegate.inspect(maid, gameTime)
                : AbilityRuntimeState.idle();
    }

    @Override
    public void forget(Entity subject) {
        if (subject instanceof EntityMaid maid) {
            delegate.forget(maid);
        }
    }
}
