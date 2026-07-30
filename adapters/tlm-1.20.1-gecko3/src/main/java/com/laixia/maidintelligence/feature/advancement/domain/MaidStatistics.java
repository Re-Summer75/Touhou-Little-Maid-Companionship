package com.laixia.maidintelligence.feature.advancement.domain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * 女仆的累计统计量。原版 criteria 只描述「这一次发生了什么」，说不出「累计喂了 8 次蛋糕」，
 * 所以阈值类条件改为由自定义触发器读这里的统计量来判定。
 * <p>
 * 与进度本身不同，统计量随女仆物品走 TLM TaskData，因此收纳、跨存档都不会丢。
 */
public record MaidStatistics(
        int feedCount,
        Map<ResourceLocation, Integer> feedByItem,
        int experienceGained
) {
    public static final Codec<MaidStatistics> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("feed_count", 0).forGetter(MaidStatistics::feedCount),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("feed_by_item", Map.of())
                    .forGetter(MaidStatistics::feedByItem),
            Codec.INT.optionalFieldOf("experience_gained", 0).forGetter(MaidStatistics::experienceGained)
    ).apply(instance, MaidStatistics::new));

    private static final MaidStatistics EMPTY = new MaidStatistics(0, Map.of(), 0);

    public MaidStatistics {
        feedByItem = Map.copyOf(feedByItem);
    }

    public static MaidStatistics empty() {
        return EMPTY;
    }

    public int feedCount(ResourceLocation item) {
        return feedByItem.getOrDefault(item, 0);
    }

    public boolean isEmpty() {
        return equals(EMPTY);
    }

    public MaidStatistics withFeed(ResourceLocation item) {
        Map<ResourceLocation, Integer> updated = new HashMap<>(feedByItem);
        updated.merge(item, 1, Integer::sum);
        return new MaidStatistics(feedCount + 1, updated, experienceGained);
    }

    public MaidStatistics withExperience(int amount) {
        if (amount <= 0) {
            return this;
        }
        return new MaidStatistics(feedCount, feedByItem, experienceGained + amount);
    }

    /**
     * 迁移旧成就进度时用：只在比现有统计更大时抬高，重复迁移不会翻倍。
     */
    public MaidStatistics atLeast(int feeds, ResourceLocation feedItem, int itemFeeds, int experience) {
        Map<ResourceLocation, Integer> updated = new HashMap<>(feedByItem);
        if (itemFeeds > 0) {
            updated.merge(feedItem, itemFeeds, Math::max);
        }
        return new MaidStatistics(
                Math.max(feedCount, feeds),
                updated,
                Math.max(experienceGained, experience)
        );
    }
}
