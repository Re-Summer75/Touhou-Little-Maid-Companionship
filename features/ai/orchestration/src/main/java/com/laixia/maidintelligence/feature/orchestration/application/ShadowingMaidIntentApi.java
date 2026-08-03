package com.laixia.maidintelligence.feature.orchestration.application;

import com.laixia.maidintelligence.feature.orchestration.api.IntentMetrics;
import com.laixia.maidintelligence.feature.orchestration.api.CompanionObservationSnapshot;
import com.laixia.maidintelligence.feature.orchestration.api.DecisionTrace;
import com.laixia.maidintelligence.feature.orchestration.api.IntentRolloutMode;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTraceComparison;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.Belief;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.EpisodicEvent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Delivers identical inputs to a dry-run pipeline before authoritative work.
 */
public final class ShadowingMaidIntentApi<M> implements MaidIntentApi<M> {
    private final MaidIntentApi<M> live;
    private final MaidIntentApi<M> shadow;
    private final Supplier<IntentRolloutMode> mode;

    public ShadowingMaidIntentApi(
            MaidIntentApi<M> live,
            MaidIntentApi<M> shadow,
            Supplier<IntentRolloutMode> mode
    ) {
        this.live = Objects.requireNonNull(live, "live");
        this.shadow = Objects.requireNonNull(shadow, "shadow");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    @Override
    public boolean tick(M subject, long gameTime) {
        if (shadowEnabled()) {
            // Dry-run first so authoritative actions cannot alter its input.
            shadow.tick(subject, gameTime);
        }
        return live.tick(subject, gameTime);
    }

    @Override
    public boolean signal(
            M subject,
            OrchestrationId signal,
            long gameTime,
            int ttlTicks
    ) {
        if (shadowEnabled()) {
            shadow.signal(subject, signal, gameTime, ttlTicks);
        }
        return live.signal(subject, signal, gameTime, ttlTicks);
    }

    @Override
    public IntentTrace inspect(M subject) {
        return live.inspect(subject);
    }

    @Override
    public IntentTrace inspectShadow(M subject) {
        if (!shadowEnabled()) {
            return IntentTrace.idle();
        }
        return shadow.inspect(subject);
    }

    @Override
    public IntentTraceComparison compare(M subject) {
        IntentTrace liveTrace = live.inspect(subject);
        if (!shadowEnabled()) {
            return IntentTraceComparison.unavailable(liveTrace);
        }
        return IntentTraceComparison.compare(
                liveTrace,
                shadow.inspect(subject)
        );
    }

    @Override
    public DecisionTrace inspectDecision(M subject) {
        return live.inspectDecision(subject);
    }

    @Override
    public CompanionObservationSnapshot observations(
            M subject,
            long gameTime
    ) {
        return live.observations(subject, gameTime);
    }

    @Override
    public boolean publishEvent(
            M subject,
            EpisodicEvent event,
            long gameTime
    ) {
        if (shadowEnabled()) {
            shadow.publishEvent(subject, event, gameTime);
        }
        return live.publishEvent(subject, event, gameTime);
    }

    @Override
    public boolean rememberBelief(
            M subject,
            Belief belief,
            long gameTime
    ) {
        if (shadowEnabled()) {
            shadow.rememberBelief(subject, belief, gameTime);
        }
        return live.rememberBelief(subject, belief, gameTime);
    }

    @Override
    public void forget(M subject) {
        shadow.forget(subject);
        live.forget(subject);
    }

    @Override
    public IntentMetrics metrics() {
        return live.metrics();
    }

    @Override
    public void resetMetrics() {
        shadow.resetMetrics();
        live.resetMetrics();
    }

    private boolean shadowEnabled() {
        return mode.get().shadowEnabled();
    }
}
