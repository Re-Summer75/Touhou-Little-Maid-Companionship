package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;

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

        WalkTarget walkTarget = currentWalkTarget(maid);
        boolean newWalkTarget = walkTarget != null
                && walkTarget != state.lastWalkTarget;
        state.lastWalkTarget = walkTarget;

        if (!isBuiltInTask(maid)) {
            state.resetWork();
            return;
        }
        boolean hasWorkTarget = maid.getBrain().hasMemoryValue(
                InitEntities.TARGET_POS.get()
        );
        if (hasWorkTarget) {
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

        if (newWalkTarget
                && !hasWorkTarget
                && walkTarget.getTarget() instanceof BlockPosTracker
                && maid.getBrain().isActive(Activity.WORK)
                && maid.getTask().enableLookAndRandomWalk(maid)) {
            intents().signal(
                    maid,
                    CompanionIntentIds.RANDOM_STROLL_RETURN,
                    gameTime,
                    RANDOM_STROLL_SIGNAL_TTL
            );
        }
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

    private static WalkTarget currentWalkTarget(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
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
        private WalkTarget lastWalkTarget;

        private void resetWork() {
            workTargetActive = false;
            workReleasedAtTick = -1L;
        }

        private void reset() {
            lastTick = Long.MIN_VALUE;
            resetWork();
            lastWalkTarget = null;
        }
    }
}
