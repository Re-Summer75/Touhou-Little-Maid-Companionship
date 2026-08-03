package com.laixia.maidintelligence.feature.behavior.domain.ability;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record AbilityRuntimeState(
        List<AbilityActivationRequest> requests,
        Map<OrchestrationId, Long> cooldownUntil,
        Set<OrchestrationId> executing
) {
    public AbilityRuntimeState {
        requests = List.copyOf(requests);
        cooldownUntil = Map.copyOf(cooldownUntil);
        executing = Set.copyOf(executing);
    }

    public static AbilityRuntimeState idle() {
        return new AbilityRuntimeState(List.of(), Map.of(), Set.of());
    }
}
