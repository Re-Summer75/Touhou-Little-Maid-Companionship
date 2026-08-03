package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancyReason;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancySnapshot;
import com.laixia.maidintelligence.feature.ai.tlm.TlmBehaviorOccupancyClassifier;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Converts TLM memory edges into bounded, one-shot orchestration signals.
 */
public final class TlmMaidIntentObserver {
    private static final String TLM_NAMESPACE = "touhou_little_maid";
    private static final int POST_TASK_SIGNAL_TTL = 200;
    private static final int RANDOM_STROLL_SIGNAL_TTL = 2;

    private MaidIntentApi<EntityMaid> intents;
    private final Map<EntityMaid, State> states = new WeakHashMap<>();

    public TlmMaidIntentObserver() {
    }

    public TlmMaidIntentObserver(MaidIntentApi<EntityMaid> intents) {
        bind(intents);
    }

    public synchronized void bind(MaidIntentApi<EntityMaid> intents) {
        if (this.intents != null) {
            throw new IllegalStateException(
                    "Maid intent observer is already bound"
            );
        }
        this.intents = Objects.requireNonNull(intents, "intents");
    }

    public void observe(EntityMaid maid, long gameTime) {
        State state = states.computeIfAbsent(maid, ignored -> new State());
        if (state.lastTick != Long.MIN_VALUE && gameTime < state.lastTick) {
            state.reset();
        }
        state.lastTick = gameTime;

        BehaviorOccupancySnapshot occupancy =
                TlmBehaviorOccupancyClassifier.snapshot(maid, gameTime);
        if (!isBuiltInTask(maid)) {
            state.resetWork();
            state.randomStrollActive = false;
            return;
        }
        boolean activeWork = occupancy.reason()
                == BehaviorOccupancyReason.WORK_TARGET
                || occupancy.reason()
                == BehaviorOccupancyReason.BUILT_IN_WORK;
        if (activeWork) {
            state.workTargetActive = true;
            state.workReleasedAtTick = -1L;
        } else if (state.workTargetActive) {
            state.workTargetActive = false;
            state.workReleasedAtTick = gameTime;
            intents().signal(
                    maid,
                    CompanionIntentIds.POST_TASK_RETURN,
                    gameTime,
                    POST_TASK_SIGNAL_TTL
            );
        }

        boolean randomStroll = occupancy.reason()
                == BehaviorOccupancyReason.RANDOM_STROLL;
        if (state.randomStrollActive
                && !randomStroll
                && !occupancy.ownerCommandOverrideActive()) {
            intents().signal(
                    maid,
                    CompanionIntentIds.RANDOM_STROLL_RETURN,
                    gameTime,
                    RANDOM_STROLL_SIGNAL_TTL
            );
        }
        state.randomStrollActive = randomStroll;
    }

    public double workReleaseAge(EntityMaid maid, long gameTime) {
        State state = states.get(maid);
        if (state == null
                || state.workReleasedAtTick < 0L
                || gameTime < state.workReleasedAtTick) {
            return -1.0D;
        }
        return gameTime - state.workReleasedAtTick;
    }

    private static boolean isBuiltInTask(EntityMaid maid) {
        return TLM_NAMESPACE.equals(
                maid.getTask().getUid().getNamespace()
        );
    }

    private MaidIntentApi<EntityMaid> intents() {
        if (intents == null) {
            throw new IllegalStateException(
                    "Maid intent observer is not bound"
            );
        }
        return intents;
    }

    private static final class State {
        private long lastTick = Long.MIN_VALUE;
        private boolean workTargetActive;
        private long workReleasedAtTick = -1L;
        private boolean randomStrollActive;

        private void resetWork() {
            workTargetActive = false;
            workReleasedAtTick = -1L;
        }

        private void reset() {
            lastTick = Long.MIN_VALUE;
            resetWork();
            randomStrollActive = false;
        }
    }
}
