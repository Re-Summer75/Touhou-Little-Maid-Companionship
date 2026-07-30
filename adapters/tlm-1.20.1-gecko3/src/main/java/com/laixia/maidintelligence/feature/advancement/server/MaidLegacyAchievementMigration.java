package com.laixia.maidintelligence.feature.advancement.server;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.api.MaidStatisticsApi;
import com.laixia.maidintelligence.feature.advancement.domain.ItemId;
import com.laixia.maidintelligence.feature.advancement.tlm.LegacyAchievementData;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;

import java.util.List;

/**
 * 把旧的自定义成就进度补进新的 advancement。只在女仆还没有进度存档时跑一次。
 * <p>
 * 补齐走 {@link AdvancementProgress#grantProgress} 而不是 {@code award}：老进度是已经拿过的，
 * 不该再广播一次聊天、也不该再发一次奖励经验。
 */
final class MaidLegacyAchievementMigration {
    private static final List<String> ACHIEVEMENTS = List.of(
            "first_feed",
            "regular_meals",
            "sweet_tooth",
            "apprentice",
            "veteran",
            "paragon",
            "diligent_study",
            "acquainted",
            "trusted",
            "devoted",
            "perfect_maid"
    );
    private static final ItemId CAKE =
            ItemId.of("minecraft", "cake");
    private static final int REGULAR_MEALS_GOAL = 32;
    private static final int SWEET_TOOTH_GOAL = 8;
    private static final int DILIGENT_STUDY_GOAL = 1000;

    private MaidLegacyAchievementMigration() {
    }

    /**
     * @return 是否真的补过东西，调用方据此决定要不要立刻落盘
     */
    static boolean apply(
            EntityMaid maid,
            PlayerAdvancements advancements,
            ServerAdvancementManager manager,
            MaidStatisticsApi<EntityMaid> statistics
    ) {
        LegacyAchievementData.Progress legacy = LegacyAchievementData.get(maid);
        if (legacy.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (String achievement : ACHIEVEMENTS) {
            if (isUnlocked(legacy, achievement)) {
                changed |= grantSilently(advancements, manager, ModResources.id("maid/" + achievement));
            }
        }
        liftStatistics(maid, legacy, statistics);
        LegacyAchievementData.clear(maid);
        return changed;
    }

    /**
     * 阈值类条件现在读统计量，所以进行中的计数也要跟着搬，不然喂了 30 次的女仆会从 0 重来。
     */
    private static void liftStatistics(
            EntityMaid maid,
            LegacyAchievementData.Progress legacy,
            MaidStatisticsApi<EntityMaid> statistics
    ) {
        int feeds = Math.max(
                counterOf(legacy, "first_feed"),
                Math.max(
                        counterOf(legacy, "regular_meals"),
                        isUnlocked(legacy, "regular_meals") ? REGULAR_MEALS_GOAL : 0
                )
        );
        int cakes = isUnlocked(legacy, "sweet_tooth") ? SWEET_TOOTH_GOAL : counterOf(legacy, "sweet_tooth");
        int experience = isUnlocked(legacy, "diligent_study")
                ? DILIGENT_STUDY_GOAL
                : counterOf(legacy, "diligent_study");
        statistics.liftToAtLeast(
                maid,
                Math.max(feeds, cakes),
                CAKE,
                cakes,
                experience
        );
    }

    private static boolean grantSilently(
            PlayerAdvancements advancements,
            ServerAdvancementManager manager,
            ResourceLocation id
    ) {
        Advancement advancement = manager.getAdvancement(id);
        if (advancement == null) {
            return false;
        }
        AdvancementProgress progress = advancements.getOrStartProgress(advancement);
        boolean changed = false;
        for (String criterion : advancement.getCriteria().keySet()) {
            changed |= progress.grantProgress(criterion);
        }
        return changed;
    }

    /** 改过 mod id，老存档里的成就 ID 可能还是旧命名空间。 */
    private static boolean isUnlocked(LegacyAchievementData.Progress legacy, String achievement) {
        return legacy.isUnlocked(ModResources.id(achievement))
                || legacy.isUnlocked(ModResources.legacyId(achievement));
    }

    private static int counterOf(LegacyAchievementData.Progress legacy, String achievement) {
        return Math.max(
                legacy.counter(ModResources.id(achievement)),
                legacy.counter(ModResources.legacyId(achievement))
        );
    }
}
