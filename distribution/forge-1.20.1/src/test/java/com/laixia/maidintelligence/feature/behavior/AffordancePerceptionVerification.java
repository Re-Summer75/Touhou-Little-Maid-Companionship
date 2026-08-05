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
        commodityValuesShareOneScale();
        anEmptyAdvertiserLosesToAStockedFurtherOne();
        looseFoodDoesNotCrowdOutContainers();
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
        // Spread across the one scale every advertiser shares, so "best" is
        // best on its own merit rather than by leaving the scale behind.
        index.upsert(advertisement(
                "far",
                1L,
                0L,
                100L,
                5.0D,
                0.5D
        ));
        index.upsert(advertisement(
                "near",
                1L,
                0L,
                100L,
                1.0D,
                0.4D
        ));
        index.upsert(advertisement(
                "best",
                1L,
                0L,
                100L,
                2.0D,
                1.0D
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

    /**
     * Ranking subtracts a distance penalty that is a fraction of the search
     * radius, so an advertiser answering outside that range would not merely
     * rank high — it would switch distance off for every query it appeared in.
     */
    private static void commodityValuesShareOneScale() {
        for (double outside : new double[]{1.5D, -0.1D, 100.0D}) {
            boolean rejected = false;
            try {
                advertisement("scale", 1L, 0L, 10L, 1.0D, outside);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            require(rejected,
                    "A commodity of " + outside + " was accepted");
        }
        advertisement("scale", 1L, 0L, 10L, 1.0D, 0.0D);
        advertisement("scale", 1L, 0L, 10L, 1.0D, 1.0D);
    }

    /**
     * The point of reading a commodity from real stock: with every advertiser
     * claiming the same value, ranking collapsed to distance and the nearest
     * empty cabinet always won.
     */
    private static void anEmptyAdvertiserLosesToAStockedFurtherOne() {
        DefaultAffordanceIndex index = new DefaultAffordanceIndex(() -> 32);
        // Empty, and two blocks away.
        index.upsert(advertisement("empty", 1L, 0L, 100L, 2.0D, 0.0D));
        // Well stocked, and six times further off.
        index.upsert(advertisement("stocked", 1L, 0L, 100L, 12.0D, 0.8D));

        List<AffordanceCandidate> ranked = index.query(query(1L, 4));
        require(ranked.size() == 2,
                "Both cabinets should be found, got " + ranked.size());
        require(ranked.get(0).advertisement().target().value().equals("stocked"),
                "The nearer empty cabinet outranked the stocked one");
    }

    /**
     * Food on the ground and food in a cabinet both relieve hunger, so both
     * advertise take_food. A caller that can only walk to a block asks for
     * open_container as well — without that, nearby dropped items fill the
     * top-K and are then discarded for having no block position, and she stops
     * finding cabinets whenever anything edible is lying about.
     */
    private static void looseFoodDoesNotCrowdOutContainers() {
        DefaultAffordanceIndex index = new DefaultAffordanceIndex(() -> 32);
        index.upsert(loose("dropped_near", 1.0D));
        index.upsert(loose("dropped_nearer", 0.5D));
        index.upsert(container("cabinet", 9.0D));

        List<AffordanceCandidate> containers = index.query(new AffordanceQuery(
                Set.of(
                        CompanionAffordanceIds.TAKE_FOOD,
                        CompanionAffordanceIds.OPEN_CONTAINER
                ),
                CompanionAffordanceIds.HUNGER_RELIEF,
                new AffordancePosition("test:dimension", 0.0D, 0.0D, 0.0D),
                16.0D,
                2,
                1L
        ));
        require(containers.size() == 1,
                "Loose food answered a container query, got "
                        + containers.size());
        require(containers.get(0).advertisement()
                        .target().value().equals("cabinet"),
                "The container query did not find the cabinet");

        // Anything hungry still sees all three.
        require(index.query(query(2L, 8)).size() == 3,
                "A take_food query missed the loose items");
    }

    /** Food kept in something that has to be opened first. */
    private static AffordanceAdvertisement container(String name, double x) {
        return new AffordanceAdvertisement(
                new AffordanceTargetId("test", name),
                Set.of(
                        CompanionAffordanceIds.TAKE_FOOD,
                        CompanionAffordanceIds.OPEN_CONTAINER
                ),
                Map.of(CompanionAffordanceIds.HUNGER_RELIEF, 0.9D),
                new AffordancePosition("test:dimension", x, 0.0D, 0.0D),
                1L,
                0L,
                100L,
                Map.of()
        );
    }

    /** Food lying on the ground: no container to open. */
    private static AffordanceAdvertisement loose(String name, double x) {
        return new AffordanceAdvertisement(
                new AffordanceTargetId("test", name),
                Set.of(CompanionAffordanceIds.TAKE_FOOD),
                Map.of(CompanionAffordanceIds.HUNGER_RELIEF, 0.6D),
                new AffordancePosition("test:dimension", x, 0.0D, 0.0D),
                1L,
                0L,
                100L,
                Map.of()
        );
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
