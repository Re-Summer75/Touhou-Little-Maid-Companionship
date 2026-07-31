package com.laixia.maidintelligence.feature.advancement.server;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.api.MaidStatisticsApi;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidProgressAdvancementTriggers;
import com.laixia.maidintelligence.feature.advancement.codec.MinecraftResourceIds;
import com.laixia.maidintelligence.feature.advancement.criterion.MaidCriteriaTriggers;
import com.laixia.maidintelligence.feature.advancement.domain.ItemId;
import com.laixia.maidintelligence.feature.advancement.domain.MaidStatistics;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Instance-scoped adapter for companionship progression criteria.
 */
public final class MaidProgressCriteria
        implements MaidProgressAdvancementTriggers {
    private final MaidAdvancementManager manager;
    private final MaidStatisticsApi<EntityMaid> statistics;

    public MaidProgressCriteria(
            MaidAdvancementManager manager,
            MaidStatisticsApi<EntityMaid> statistics
    ) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.statistics = Objects.requireNonNull(
                statistics,
                "statistics"
        );
    }

    private void fire(EntityMaid maid, Consumer<MaidMirrorPlayer> action) {
        manager.fire(maid, action);
    }

    /**
     * 玩家亲手喂食：先记进统计量，再让阈值类条件按更新后的累计次数判定。
     */
    @Override
    public void fed(EntityMaid maid, ItemStack food) {
        if (food.isEmpty() || maid.level().isClientSide()) {
            return;
        }
        ItemId item = MinecraftResourceIds.toCore(food.getItem());
        MaidStatistics updated = statistics.recordFeed(maid, item);
        ItemStack fed = food.copyWithCount(1);
        fire(maid, mirror -> MaidCriteriaTriggers.MAID_FED.trigger(
                mirror,
                fed,
                updated
        ));
    }

    @Override
    public void level(EntityMaid maid, int level) {
        fire(maid, mirror -> MaidCriteriaTriggers.MAID_LEVEL.trigger(
                mirror,
                level
        ));
    }

    @Override
    public void favorabilityLevel(EntityMaid maid, int level) {
        fire(maid, mirror ->
                MaidCriteriaTriggers.MAID_FAVORABILITY_LEVEL.trigger(
                        mirror,
                        level
                ));
    }

    /**
     * 女仆入账等级经验：统计量累加后判定累计阈值。
     */
    @Override
    public void experienceGained(EntityMaid maid, int amount) {
        if (amount <= 0 || maid.level().isClientSide()) {
            return;
        }
        MaidStatistics updated = statistics.recordExperience(
                maid,
                amount
        );
        fire(maid, mirror -> MaidCriteriaTriggers.MAID_EXPERIENCE.trigger(
                mirror,
                updated
        ));
    }

    /**
     * 定时对账用：等级与好感等级按当前值重放，用指令改过的数值也能算数。
     */
    @Override
    public void replaceStanding(EntityMaid maid, int level) {
        MaidStatistics currentStatistics = statistics.get(maid);
        fire(maid, mirror -> {
            MaidCriteriaTriggers.MAID_LEVEL.trigger(mirror, level);
            MaidCriteriaTriggers.MAID_FAVORABILITY_LEVEL.trigger(
                    mirror,
                    maid.getFavorabilityManager().getLevel()
            );
            MaidCriteriaTriggers.MAID_EXPERIENCE.trigger(
                    mirror,
                    currentStatistics
            );
        });
    }
}
