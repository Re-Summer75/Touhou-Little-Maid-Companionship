package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.BiConsumer;

public final class MaidIntentBehavior extends Behavior<EntityMaid> {
    private static final int RUNTIME_DURATION_TICKS = Integer.MAX_VALUE;

    private final MaidIntentApi<EntityMaid> intents;
    private final TlmMaidIntentObserver observer;
    private final BiConsumer<EntityMaid, Long> preTick;
    private long lastProcessedTick = Long.MIN_VALUE;

    public MaidIntentBehavior(
            MaidIntentApi<EntityMaid> intents,
            TlmMaidIntentObserver observer
    ) {
        this(intents, observer, (maid, gameTime) -> {
        });
    }

    public MaidIntentBehavior(
            MaidIntentApi<EntityMaid> intents,
            TlmMaidIntentObserver observer,
            BiConsumer<EntityMaid, Long> preTick
    ) {
        super(ImmutableMap.of(), RUNTIME_DURATION_TICKS);
        this.intents = Objects.requireNonNull(intents, "intents");
        this.observer = Objects.requireNonNull(observer, "observer");
        this.preTick = Objects.requireNonNull(preTick, "preTick");
    }

    @Override
    protected boolean checkExtraStartConditions(
            @Nonnull ServerLevel level,
            @Nonnull EntityMaid maid
    ) {
        // Orchestrator budgets evaluation internally; explicit signals and
        // active plan actions still need progression on every Brain tick.
        return !maid.isDeadOrDying();
    }

    @Override
    protected void start(
            @Nonnull ServerLevel level,
            @Nonnull EntityMaid maid,
            long gameTime
    ) {
        advance(maid, gameTime);
    }

    @Override
    protected boolean canStillUse(
            @Nonnull ServerLevel level,
            @Nonnull EntityMaid maid,
            long gameTime
    ) {
        return !maid.isDeadOrDying();
    }

    @Override
    protected void tick(
            @Nonnull ServerLevel level,
            @Nonnull EntityMaid maid,
            long gameTime
    ) {
        advance(maid, gameTime);
    }

    private void advance(EntityMaid maid, long gameTime) {
        // Brain may start and tick a behavior in the same game tick.
        if (lastProcessedTick == gameTime) {
            return;
        }
        lastProcessedTick = gameTime;
        observer.observe(maid, gameTime);
        preTick.accept(maid, gameTime);
        intents.tick(maid, gameTime);
    }
}
