package com.laixia.maidintelligence.feature.advancement.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 女仆的累计统计量。原版 criteria 只描述「这一次发生了什么」，说不出「累计喂了 8 次蛋糕」，
 * 所以阈值类条件改为由自定义触发器读这里的统计量来判定。
 * <p>
 * 与进度本身不同，统计量随女仆物品走 TLM TaskData，因此收纳、跨存档都不会丢。
 */
public record MaidStatistics(
        int feedCount,
        Map<ItemId, Integer> feedByItem,
        int experienceGained
) {
    private static final MaidStatistics EMPTY =
            new MaidStatistics(0, Map.of(), 0);

    public MaidStatistics {
        Objects.requireNonNull(feedByItem, "feedByItem");
        feedByItem = Map.copyOf(feedByItem);
    }

    public static MaidStatistics empty() {
        return EMPTY;
    }

    public int feedCount(ItemId item) {
        return feedByItem.getOrDefault(
                Objects.requireNonNull(item, "item"),
                0
        );
    }

    public boolean isEmpty() {
        return equals(EMPTY);
    }

    public MaidStatistics withFeed(ItemId item) {
        Objects.requireNonNull(item, "item");
        Map<ItemId, Integer> updated = new HashMap<>(feedByItem);
        updated.merge(item, 1, Integer::sum);
        return new MaidStatistics(
                feedCount + 1,
                updated,
                experienceGained
        );
    }

    public MaidStatistics withExperience(int amount) {
        if (amount <= 0) {
            return this;
        }
        return new MaidStatistics(
                feedCount,
                feedByItem,
                experienceGained + amount
        );
    }

    /**
     * 迁移旧成就进度时用：只在比现有统计更大时抬高，重复迁移不会翻倍。
     */
    public MaidStatistics atLeast(
            int feeds,
            ItemId feedItem,
            int itemFeeds,
            int experience
    ) {
        Objects.requireNonNull(feedItem, "feedItem");
        Map<ItemId, Integer> updated = new HashMap<>(feedByItem);
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
