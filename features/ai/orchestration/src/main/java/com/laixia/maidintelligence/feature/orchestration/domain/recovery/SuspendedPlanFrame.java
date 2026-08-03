package com.laixia.maidintelligence.feature.orchestration.domain.recovery;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.ResumePolicy;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Semantic continuation state; no Java stack or entity reference is retained.
 */
public record SuspendedPlanFrame(
        OrchestrationId intent,
        OrchestrationId plan,
        String state,
        String checkpoint,
        OrchestrationId action,
        Map<String, String> parameters,
        ResumePolicy policy,
        long catalogGeneration,
        long activeSinceTick,
        long suspendedAtTick,
        long expiresAtTick,
        double score,
        UUID correlationId,
        String reason
) {
    public SuspendedPlanFrame {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(reason, "reason");
        if (!policy.suspendable()
                || activeSinceTick > suspendedAtTick
                || expiresAtTick <= suspendedAtTick
                || !Double.isFinite(score)) {
            throw new IllegalArgumentException(
                    "Invalid suspended plan frame"
            );
        }
        parameters = Map.copyOf(parameters);
    }

    public boolean activeAt(long gameTime, long generation) {
        return catalogGeneration == generation
                && gameTime >= suspendedAtTick
                && gameTime < expiresAtTick;
    }
}
