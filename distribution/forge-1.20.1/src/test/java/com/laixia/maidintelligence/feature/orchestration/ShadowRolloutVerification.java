package com.laixia.maidintelligence.feature.orchestration;

import com.laixia.maidintelligence.feature.orchestration.api.IntentRolloutMode;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTraceComparison;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.application.ShadowingMaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;

import java.util.List;

import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.*;

public final class ShadowRolloutVerification {
    private ShadowRolloutVerification() {
    }

    public static void main(String[] args) {
        verify();
    }

    private static void verify() {
        IntentVerificationFixture live = new IntentVerificationFixture();
        IntentVerificationFixture shadow = new IntentVerificationFixture();
        PlanDefinition plan = plan(id("plan/shadow"), WAIT, 20);
        IntentDefinition intent = definition(
                id("intent/shadow"),
                plan.id(),
                List.of(condition(
                        SIGNAL,
                        FactComparison.GREATER_OR_EQUAL,
                        1.0D
                )),
                1.0D,
                0,
                0,
                0.0D
        );
        live.publish(1L, List.of(intent), List.of(plan));
        shadow.publish(1L, List.of(intent), List.of(plan));
        MaidIntentApi<String> dual = new ShadowingMaidIntentApi<>(
                live.intents,
                shadow.intents,
                () -> IntentRolloutMode.SHADOW_COMPARE
        );

        require(dual.signal("maid", SIGNAL, 0L, 20),
                "Live pipeline rejected a valid signal");
        require(dual.tick("maid", 0L),
                "Live pipeline did not run through shadow facade");
        require(live.actions.executions == 1,
                "Authoritative action did not execute exactly once");
        require(shadow.actions.executions == 1,
                "Shadow action did not execute exactly once");

        IntentTraceComparison comparison = dual.compare("maid");
        require(comparison.matches(),
                "Identical dry-run selection did not match live trace");
    }
}
