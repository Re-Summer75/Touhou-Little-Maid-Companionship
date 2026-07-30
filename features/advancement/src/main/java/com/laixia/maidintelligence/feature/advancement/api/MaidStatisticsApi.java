package com.laixia.maidintelligence.feature.advancement.api;

import com.laixia.maidintelligence.feature.advancement.domain.ItemId;
import com.laixia.maidintelligence.feature.advancement.domain.MaidStatistics;

/**
 * Platform-neutral cumulative-statistics use cases.
 */
public interface MaidStatisticsApi<S> {
    MaidStatistics get(S subject);

    MaidStatistics recordFeed(S subject, ItemId item);

    MaidStatistics recordExperience(S subject, int amount);

    MaidStatistics liftToAtLeast(
            S subject,
            int feeds,
            ItemId feedItem,
            int itemFeeds,
            int experience
    );
}
