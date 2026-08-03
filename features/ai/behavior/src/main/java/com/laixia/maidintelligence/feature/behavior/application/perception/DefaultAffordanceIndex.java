package com.laixia.maidintelligence.feature.behavior.application.perception;

import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceAdvertisement;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceQuery;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceTargetId;
import com.laixia.maidintelligence.feature.behavior.port.AffordanceIndexPort;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;

/**
 * Revision-aware reverse index with a shared per-tick examination budget.
 */
public final class DefaultAffordanceIndex implements AffordanceIndexPort {
    private static final Comparator<AffordanceCandidate> CANDIDATE_ORDER =
            Comparator.comparingDouble(AffordanceCandidate::coarseUtility)
                    .reversed()
                    .thenComparingDouble(
                            AffordanceCandidate::distanceSquared
                    )
                    .thenComparing(
                            candidate -> candidate.advertisement().target()
                    );

    private final IntSupplier examinationBudget;
    private final Map<AffordanceTargetId, AffordanceAdvertisement> targets =
            new HashMap<>();
    private final Map<OrchestrationId, Set<AffordanceTargetId>>
            byAffordance = new HashMap<>();
    private final Map<OrchestrationId, Set<AffordanceTargetId>>
            byCommodity = new HashMap<>();
    private long budgetTick = Long.MIN_VALUE;
    private int examinedThisTick;

    public DefaultAffordanceIndex(IntSupplier examinationBudget) {
        this.examinationBudget = java.util.Objects.requireNonNull(
                examinationBudget,
                "examinationBudget"
        );
    }

    @Override
    public synchronized boolean upsert(
            AffordanceAdvertisement advertisement
    ) {
        AffordanceAdvertisement current = targets.get(
                advertisement.target()
        );
        if (current != null
                && current.revision() >= advertisement.revision()) {
            return false;
        }
        if (current != null) {
            removeReverse(current);
        }
        targets.put(advertisement.target(), advertisement);
        addReverse(advertisement);
        return true;
    }

    @Override
    public synchronized boolean remove(
            AffordanceTargetId target,
            long revision
    ) {
        AffordanceAdvertisement current = targets.get(target);
        if (current == null || current.revision() != revision) {
            return false;
        }
        removeInternal(current);
        return true;
    }

    @Override
    public synchronized List<AffordanceCandidate> query(
            AffordanceQuery query
    ) {
        resetBudget(query.gameTime());
        int remaining = Math.max(
                0,
                boundedBudget() - examinedThisTick
        );
        if (remaining == 0) {
            return List.of();
        }
        Set<AffordanceTargetId> indexed = narrowestIndex(query);
        if (indexed.isEmpty()) {
            return List.of();
        }

        double maximumDistanceSquared =
                query.maximumDistance() * query.maximumDistance();
        List<AffordanceCandidate> candidates = new ArrayList<>();
        for (AffordanceTargetId target : List.copyOf(indexed)) {
            if (remaining-- == 0) {
                break;
            }
            examinedThisTick++;
            AffordanceAdvertisement advertisement = targets.get(target);
            if (advertisement == null) {
                continue;
            }
            if (!advertisement.activeAt(query.gameTime())) {
                removeInternal(advertisement);
                continue;
            }
            if (!advertisement.affordances()
                    .containsAll(query.requiredAffordances())) {
                continue;
            }
            double distanceSquared = advertisement.position()
                    .distanceSquared(query.origin());
            if (distanceSquared > maximumDistanceSquared) {
                continue;
            }
            candidates.add(new AffordanceCandidate(
                    advertisement,
                    distanceSquared,
                    utility(query, advertisement, distanceSquared)
            ));
        }
        candidates.sort(CANDIDATE_ORDER);
        if (candidates.size() <= query.topK()) {
            return List.copyOf(candidates);
        }
        return List.copyOf(candidates.subList(0, query.topK()));
    }

    @Override
    public synchronized int size() {
        return targets.size();
    }

    private Set<AffordanceTargetId> narrowestIndex(
            AffordanceQuery query
    ) {
        Set<AffordanceTargetId> selected = null;
        if (query.commodity() != null) {
            selected = byCommodity.get(query.commodity());
        }
        for (OrchestrationId affordance
                : query.requiredAffordances()) {
            Set<AffordanceTargetId> candidate =
                    byAffordance.get(affordance);
            if (candidate == null) {
                return Set.of();
            }
            if (selected == null || candidate.size() < selected.size()) {
                selected = candidate;
            }
        }
        return selected == null ? Set.of() : selected;
    }

    private void addReverse(AffordanceAdvertisement advertisement) {
        for (OrchestrationId affordance
                : advertisement.affordances()) {
            byAffordance.computeIfAbsent(
                    affordance,
                    ignored -> new LinkedHashSet<>()
            ).add(advertisement.target());
        }
        for (OrchestrationId commodity
                : advertisement.commodities().keySet()) {
            byCommodity.computeIfAbsent(
                    commodity,
                    ignored -> new LinkedHashSet<>()
            ).add(advertisement.target());
        }
    }

    private void removeInternal(AffordanceAdvertisement advertisement) {
        targets.remove(advertisement.target());
        removeReverse(advertisement);
    }

    private void removeReverse(AffordanceAdvertisement advertisement) {
        for (OrchestrationId affordance
                : advertisement.affordances()) {
            removeFrom(byAffordance, affordance, advertisement.target());
        }
        for (OrchestrationId commodity
                : advertisement.commodities().keySet()) {
            removeFrom(byCommodity, commodity, advertisement.target());
        }
    }

    private static void removeFrom(
            Map<OrchestrationId, Set<AffordanceTargetId>> index,
            OrchestrationId key,
            AffordanceTargetId target
    ) {
        Set<AffordanceTargetId> values = index.get(key);
        if (values == null) {
            return;
        }
        values.remove(target);
        if (values.isEmpty()) {
            index.remove(key);
        }
    }

    private static double utility(
            AffordanceQuery query,
            AffordanceAdvertisement advertisement,
            double distanceSquared
    ) {
        double commodity = query.commodity() == null
                ? 1.0D
                : advertisement.commodities()
                .getOrDefault(query.commodity(), 0.0D);
        double distancePenalty =
                Math.sqrt(distanceSquared) / query.maximumDistance();
        return commodity - distancePenalty;
    }

    private void resetBudget(long gameTime) {
        if (budgetTick != gameTime) {
            budgetTick = gameTime;
            examinedThisTick = 0;
        }
    }

    private int boundedBudget() {
        return Math.max(1, Math.min(4096, examinationBudget.getAsInt()));
    }
}
