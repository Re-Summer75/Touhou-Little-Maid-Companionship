package com.laixia.maidintelligence.feature.advancement.application;

import com.laixia.maidintelligence.feature.advancement.api.MaidStatisticsApi;
import com.laixia.maidintelligence.feature.advancement.domain.ItemId;
import com.laixia.maidintelligence.feature.advancement.domain.MaidStatistics;
import com.laixia.maidintelligence.feature.advancement.port.MaidStatisticsStore;

import java.util.Objects;

public final class DefaultMaidStatisticsService<S>
        implements MaidStatisticsApi<S> {
    private final MaidStatisticsStore<S> store;

    public DefaultMaidStatisticsService(MaidStatisticsStore<S> store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public MaidStatistics get(S subject) {
        MaidStatistics statistics = store.get(
                Objects.requireNonNull(subject, "subject")
        );
        return statistics == null ? MaidStatistics.empty() : statistics;
    }

    @Override
    public MaidStatistics recordFeed(S subject, ItemId item) {
        MaidStatistics updated = get(subject).withFeed(item);
        store.set(subject, updated);
        return updated;
    }

    @Override
    public MaidStatistics recordExperience(S subject, int amount) {
        MaidStatistics before = get(subject);
        MaidStatistics updated = before.withExperience(amount);
        if (updated != before) {
            store.set(subject, updated);
        }
        return updated;
    }

    @Override
    public MaidStatistics liftToAtLeast(
            S subject,
            int feeds,
            ItemId feedItem,
            int itemFeeds,
            int experience
    ) {
        MaidStatistics before = get(subject);
        MaidStatistics updated = before.atLeast(
                feeds,
                feedItem,
                itemFeeds,
                experience
        );
        if (!updated.equals(before)) {
            store.set(subject, updated);
        }
        return updated;
    }
}
