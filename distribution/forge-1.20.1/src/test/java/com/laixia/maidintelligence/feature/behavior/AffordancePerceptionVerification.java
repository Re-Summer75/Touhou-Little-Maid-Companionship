package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.application.perception.DefaultAffordanceIndex;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceAdvertisement;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordancePosition;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceQuery;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceTargetId;
import com.laixia.maidintelligence.feature.behavior.domain.perception.CompanionAffordanceIds;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AffordancePerceptionVerification {
    private AffordancePerceptionVerification() {
    }

    public static void main(String[] args) {
        verify();
    }

    private static void verify() {
        revisionAndExpiryProtectIndex();
        reverseIndexUsesTopKAndSharedTickBudget();
    }

    private static void revisionAndExpiryProtectIndex() {
        DefaultAffordanceIndex index = new DefaultAffordanceIndex(
                () -> 32
        );
        AffordanceAdvertisement revisionTwo = advertisement(
                "cabinet",
                2L,
                0L,
                10L,
                1.0D,
                1.0D
        );
        require(index.upsert(revisionTwo),
                "Initial affordance advertisement was rejected");
        require(!index.upsert(advertisement(
                        "cabinet",
                        1L,
                        0L,
                        10L,
                        1.0D,
                        1.0D
                )),
                "Stale affordance revision replaced current state");
        require(!index.remove(revisionTwo.target(), 1L),
                "Stale remove deleted a newer advertisement");
        require(index.query(query(9L, 8)).size() == 1,
                "Active advertisement was not queryable");
        require(index.query(query(10L, 8)).isEmpty(),
                "Expired advertisement remained queryable");
    }

    private static void reverseIndexUsesTopKAndSharedTickBudget() {
        DefaultAffordanceIndex index = new DefaultAffordanceIndex(() -> 2);
        index.upsert(advertisement(
                "far",
                1L,
                0L,
                100L,
                5.0D,
                1.0D
        ));
        index.upsert(advertisement(
                "near",
                1L,
                0L,
                100L,
                1.0D,
                1.0D
        ));
        index.upsert(advertisement(
                "best",
                1L,
                0L,
                100L,
                2.0D,
                2.0D
        ));

        List<AffordanceCandidate> first = index.query(query(1L, 2));
        require(first.size() == 2,
                "Top-K query ignored the examination budget");
        require(first.get(0).coarseUtility()
                        >= first.get(1).coarseUtility(),
                "Affordance candidates were not utility ordered");
        require(index.query(query(1L, 2)).isEmpty(),
                "Per-tick perception budget was not shared");
        require(index.query(query(2L, 1)).size() == 1,
                "Perception budget did not reset on the next tick");
    }

    private static AffordanceAdvertisement advertisement(
            String name,
            long revision,
            long observed,
            long expires,
            double x,
            double commodity
    ) {
        return new AffordanceAdvertisement(
                new AffordanceTargetId("test", name),
                Set.of(CompanionAffordanceIds.TAKE_FOOD),
                Map.of(
                        CompanionAffordanceIds.HUNGER_RELIEF,
                        commodity
                ),
                new AffordancePosition("test:dimension", x, 0.0D, 0.0D),
                revision,
                observed,
                expires,
                Map.of()
        );
    }

    private static AffordanceQuery query(long gameTime, int topK) {
        return new AffordanceQuery(
                Set.of(CompanionAffordanceIds.TAKE_FOOD),
                CompanionAffordanceIds.HUNGER_RELIEF,
                new AffordancePosition(
                        "test:dimension",
                        0.0D,
                        0.0D,
                        0.0D
                ),
                16.0D,
                topK,
                gameTime
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
