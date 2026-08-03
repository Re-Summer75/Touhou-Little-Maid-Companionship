package com.laixia.maidintelligence.feature.behavior.api;

import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityActivationRequest;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityActivationSource;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityDefinition;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityGrantSet;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityRuntimeState;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaidAbilityApi<M> {
    long catalogGeneration();

    List<AbilityDefinition> definitions();

    AbilityGrantSet grants(M subject);

    boolean grant(
            M subject,
            OrchestrationId ability,
            long gameTime,
            String source
    );

    boolean revoke(M subject, OrchestrationId ability);

    boolean granted(M subject, OrchestrationId ability);

    Optional<AbilityActivationRequest> request(
            M subject,
            OrchestrationId ability,
            AbilityActivationSource source,
            long gameTime
    );

    default Optional<AbilityActivationRequest> request(
            M subject,
            OrchestrationId ability,
            AbilityActivationSource source,
            long gameTime,
            UUID sharedRequestId
    ) {
        return request(subject, ability, source, gameTime);
    }

    Optional<AbilityActivationRequest> activeRequest(
            M subject,
            OrchestrationId ability,
            long gameTime
    );

    List<AbilityCandidate> candidates(M subject, long gameTime);

    boolean beginExecution(
            M subject,
            UUID requestId,
            long gameTime
    );

    void complete(
            M subject,
            UUID requestId,
            boolean succeeded,
            long gameTime
    );

    AbilityRuntimeState inspect(M subject, long gameTime);

    void forget(M subject);
}
