package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMode;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.BiConsumer;

public final class MaidIntentBehavior extends Behavior<EntityMaid> {
    private static final int RUNTIME_DURATION_TICKS = Integer.MAX_VALUE;

    private final MaidIntentApi<EntityMaid> intents;
    private final BiConsumer<EntityMaid, Long> preTick;
    private long lastProcessedTick = Long.MIN_VALUE;

    public MaidIntentBehavior(MaidIntentApi<EntityMaid> intents) {
        this(intents, (maid, gameTime) -> {
        });
    }

    public MaidIntentBehavior(
            MaidIntentApi<EntityMaid> intents,
            BiConsumer<EntityMaid, Long> preTick
    ) {
        super(ImmutableMap.of(), RUNTIME_DURATION_TICKS);
        this.intents = Objects.requireNonNull(intents, "intents");
        this.preTick = Objects.requireNonNull(preTick, "preTick");
    }

    /**
     * Free mode only — this is the whole of the mod's own judgement.
     *
     * <p>It arrives through {@code IExtraMaidBrain}, which puts it in {@code
     * CORE}, which runs under every work mode. So a maid told to farm was also
     * being told, by us, when to follow her owner, when to eat and when to
     * fight, on top of the host's own decisions about the same things. That is
     * the largest single way the host's other modes stopped being the host's.
     *
     * <p>Free mode is where the player asked for our judgement. Everywhere else
     * they asked for the host's, and it costs one comparison to honour that.
     */
    @Override
    protected boolean checkExtraStartConditions(
            @Nonnull ServerLevel level,
            @Nonnull EntityMaid maid
    ) {
        // Orchestrator budgets evaluation internally; explicit signals and
        // active plan actions still need progression on every Brain tick.
        return FreedomMode.isActive(maid) && !maid.isDeadOrDying();
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
        return FreedomMode.isActive(maid) && !maid.isDeadOrDying();
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
        preTick.accept(maid, gameTime);
        intents.tick(maid, gameTime);
    }
}
