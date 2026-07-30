package com.laixia.maidintelligence.feature.advancement.port;

import com.laixia.maidintelligence.feature.advancement.domain.MaidStatistics;

/**
 * Persistence boundary owned by the statistics use case.
 */
public interface MaidStatisticsStore<S> {
    MaidStatistics get(S subject);

    void set(S subject, MaidStatistics statistics);
}
