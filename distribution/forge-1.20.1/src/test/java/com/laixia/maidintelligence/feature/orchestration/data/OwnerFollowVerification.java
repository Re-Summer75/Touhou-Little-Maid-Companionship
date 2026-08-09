package com.laixia.maidintelligence.feature.orchestration.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.behavior.application.forecast.OwnerActivityTracker;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.domain.forecast.CompanionActivity;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.application.DefaultMaidIntentOrchestrator;
import com.laixia.maidintelligence.feature.orchestration.application.MutableIntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The one intent that keeps her with her owner, driven by a shipped-shaped one.
 *
 * <p>Written against the real {@code anticipate_departure.json} rather than an
 * inline copy, because the thing most likely to be wrong is the tuning in that
 * file. There used to be two intents here — a forecast-driven one that ran
 * ahead of a departure and a plain one that closed a gap — and the plain one
 * interrupted everything she was doing every time her owner crossed a room.
 * Merging them left a single question worth verifying: at what distance does
 * she put down what she is doing, and does the forecast move that distance the
 * way it is supposed to?
 *
 * <p>So the checks below are about entry distance, not about firing. Travelling
 * is close to half of all activity transitions, so a probability that merely
 * looks high says nothing; what matters is whether travelling is likely
 * <em>relative to how often it happens anyway</em>, and only running the real
 * numbers shows where the authored weights put the line.
 */
public final class OwnerFollowVerification {
    private static final String RESOURCE_ROOT = "data/tlm_companionship/";

    /** Comfortably past the shipped {@code evaluation_interval_ticks}. */
    private static final int EVALUATION_WINDOW_TICKS = 60;
    private static final OrchestrationId INTENT =
            id("intent/anticipate_departure");
    private static final UUID OWNER = UUID.nameUUIDFromBytes(
            "anticipation".getBytes(StandardCharsets.UTF_8)
    );

    /** The far edge of what she can see, and of the distance consideration. */
    private static final double PERCEPTION_BLOCKS = 16.0D;

    private OwnerFollowVerification() {
    }

    public static void main(String[] args) throws IOException {
        anOwnerAcrossTheRoomIsLeftAlone();
        anOwnerWhoWalkedOffIsFollowed();
        aColdMaidStillFollows();
        anOwnerWhoJustArrivedIsGivenRoom();
        followingSurvivesClosingTheGap();
        reportEntryDistances();
    }

    /**
     * The complaint that collapsed the two intents into one. At conversational
     * distance she is meant to carry on with whatever she was doing.
     */
    private static void anOwnerAcrossTheRoomIsLeftAlone() throws IOException {
        Harness harness = new Harness();
        harness.learnRealisticRoutine();
        harness.settleInto(
                CompanionActivity.MINING,
                CompanionActivity.TRAVELLING
        );
        harness.distance(4.0D);
        require(!harness.tickAndCheck(),
                "The maid dropped everything for an owner four blocks away");
    }

    /**
     * The other half of it. An owner who has already left is the case the
     * forecast cannot speak for — it reports departure as unlikely precisely
     * because one just happened — so distance has to carry this alone.
     */
    private static void anOwnerWhoWalkedOffIsFollowed() throws IOException {
        Harness harness = new Harness();
        harness.learnRealisticRoutine();
        harness.settleInto(
                CompanionActivity.MINING,
                CompanionActivity.TRAVELLING
        );
        harness.distance(PERCEPTION_BLOCKS - 1.0D);
        require(harness.tickAndCheck(),
                "The maid watched her owner leave from fifteen blocks "
                        + "(forecast " + harness.forecast() + ")");
    }

    /**
     * A maid in a fresh world has watched nobody. Gating following on a
     * forecast she has not had time to learn would leave her standing still
     * until the stranding teleport dragged her along.
     */
    private static void aColdMaidStillFollows() throws IOException {
        Harness harness = new Harness();
        harness.distance(PERCEPTION_BLOCKS - 1.0D);
        require(harness.tickAndCheck(),
                "A maid who has learnt nothing refused to follow at all");
    }

    /**
     * The only thing the forecast still changes, and it is worth being precise
     * about which direction it works in.
     *
     * <p>It does not bring her out early so much as hold her back late. The
     * two observed lifts cluster at roughly 0.63 mid-task and 0.10 straight
     * after a relocation, and a maid who has learnt nothing sits near the
     * upper cluster — absence of evidence that her owner is settled is not
     * evidence that they are. So mid-task and untaught behave alike, and the
     * one context that measurably differs is the owner who has just arrived
     * somewhere and is unlikely to leave again. She gives that owner room.
     */
    private static void anOwnerWhoJustArrivedIsGivenRoom() throws IOException {
        double anticipated = entryDistance(
                CompanionActivity.TRAVELLING,
                CompanionActivity.BUILDING
        );
        double justArrived = entryDistance(
                CompanionActivity.MINING,
                CompanionActivity.TRAVELLING
        );
        require(justArrived > anticipated,
                "An owner who had just relocated was chased as eagerly as one "
                        + "about to leave: " + justArrived + " vs "
                        + anticipated + " blocks");
    }

    /**
     * The distance condition is entry-only on purpose. Scored every tick it
     * would cancel her the moment she got close enough for the score to sag,
     * leaving her hovering at the distance she set out from instead of
     * arriving.
     */
    private static void followingSurvivesClosingTheGap() throws IOException {
        Harness harness = new Harness();
        harness.learnRealisticRoutine();
        harness.settleInto(
                CompanionActivity.MINING,
                CompanionActivity.TRAVELLING
        );
        harness.distance(PERCEPTION_BLOCKS - 1.0D);
        require(harness.tickAndCheck(), "She never set off");
        harness.distance(4.0D);
        require(harness.stillActive(),
                "She gave up on the way in, four blocks short of her owner");
    }

    /**
     * Prints the distance each context brings her out at, so the authored
     * weights can be re-derived rather than trusted after any change to the
     * blend.
     */
    private static void reportEntryDistances() throws IOException {
        StringBuilder report = new StringBuilder("follow entry distances: ");
        report.append("cold=")
                .append(format(entryDistance(null, null)))
                .append(' ');
        CompanionActivity[][] probes = {
                {CompanionActivity.TRAVELLING, CompanionActivity.BUILDING},
                {CompanionActivity.TRAVELLING, CompanionActivity.MINING},
                // No same-activity probe: the tracker records changes, so
                // asking for X -> X leaves whatever context preceded it in
                // place and the printed number would not be the one labelled.
                {CompanionActivity.MINING, CompanionActivity.TRAVELLING},
                {CompanionActivity.BUILDING, CompanionActivity.TRAVELLING}
        };
        for (CompanionActivity[] probe : probes) {
            report.append(probe[0]).append("->").append(probe[1])
                    .append('=')
                    .append(format(entryDistance(probe[0], probe[1])))
                    .append(' ');
        }
        System.out.println(report);
    }

    /**
     * The nearest her owner can be before this context brings her out, or
     * {@link Double#NaN} when nothing inside her perception does.
     *
     * <p>Scanned inwards a block at a time from the edge of what she can see.
     * A fresh harness per step: reusing one carries the commit and the active
     * plan from the previous distance, and the answer would then depend on the
     * order the distances were tried in.
     */
    private static double entryDistance(
            CompanionActivity previous,
            CompanionActivity current
    ) throws IOException {
        double entered = Double.NaN;
        for (double blocks = PERCEPTION_BLOCKS; blocks >= 1.0D; blocks -= 1.0D) {
            Harness harness = new Harness();
            if (previous != null) {
                harness.learnRealisticRoutine();
                harness.settleInto(previous, current);
            }
            harness.distance(blocks);
            if (!harness.tickAndCheck()) {
                return entered;
            }
            entered = blocks;
        }
        return entered;
    }

    private static String format(double blocks) {
        return Double.isNaN(blocks) ? "never" : String.format("%.0f", blocks);
    }

    private static final class Harness {
        private final OwnerActivityTracker tracker = new OwnerActivityTracker();
        private final Map<OrchestrationId, Double> facts = new HashMap<>();
        private final MaidIntentApi<String> orchestrator;
        private long clock;
        private long elapsed;

        private Harness() throws IOException {
            facts.put(CompanionIntentIds.OWNER_VALID, 1.0D);
            facts.put(CompanionIntentIds.OWNER_DISTANCE, 9.0D);
            facts.put(CompanionIntentIds.FOLLOW_MODE, 1.0D);
            facts.put(CompanionIntentIds.CAN_MOVE, 1.0D);
            facts.put(CompanionIntentIds.PASSENGER, 0.0D);
            facts.put(CompanionIntentIds.BEHAVIOR_OCCUPANCY_LEVEL, 0.0D);

            MutableIntentCatalog catalog = new MutableIntentCatalog();
            orchestrator = new DefaultMaidIntentOrchestrator<>(
                    catalog,
                    (subject, gameTime, requested, output) -> {
                        for (int index = 0; index < requested.size(); index++) {
                            output[index] = fact(
                                    requested.get(index),
                                    gameTime
                            );
                        }
                    },
                    (subject, action, parameters, gameTime, elapsedTicks) ->
                            ActionResult.RUNNING,
                    String::hashCode,
                    () -> true,
                    () -> 1,
                    () -> 64,
                    () -> true
            );
            catalog.publish(IntentCatalog.compile(
                    1L,
                    List.of(shippedIntent()),
                    List.of(shippedPlan()),
                    CompanionIntentIds.vocabulary()
            ));
        }

        /** Where her owner is standing, in blocks. */
        private void distance(double blocks) {
            facts.put(CompanionIntentIds.OWNER_DISTANCE, blocks);
        }

        private double fact(OrchestrationId requested, long gameTime) {
            for (CompanionActivity activity : CompanionActivity.values()) {
                if (requested.equals(activity.forecastFact())) {
                    return tracker.probability(OWNER, activity, gameTime);
                }
                if (requested.equals(activity.forecastLiftFact())) {
                    return tracker.lift(OWNER, activity);
                }
            }
            return facts.getOrDefault(requested, 0.0D);
        }

        /**
         * Work in stretches, travel between them, fight now and then, sleep at
         * the end of the day — the shape the weights were measured against.
         */
        private void learnRealisticRoutine() {
            Random random = new Random(42L);
            CompanionActivity[] work = {
                    CompanionActivity.MINING,
                    CompanionActivity.BUILDING,
                    CompanionActivity.FARMING
            };
            for (int day = 0; day < 60; day++) {
                for (int stint = 0; stint < 3 + random.nextInt(3); stint++) {
                    observe(CompanionActivity.TRAVELLING);
                    observe(work[random.nextInt(work.length)]);
                    if (random.nextInt(6) == 0) {
                        observe(CompanionActivity.COMBAT);
                    }
                }
                observe(CompanionActivity.TRAVELLING);
                observe(CompanionActivity.RESTING);
            }
        }

        private void settleInto(
                CompanionActivity previous,
                CompanionActivity current
        ) {
            observe(previous);
            observe(current);
        }

        private void observe(CompanionActivity activity) {
            tracker.observe(OWNER, activity, clock);
            clock += 200L;
        }

        /** Lift, matching what the shipped intent actually reads. */
        private double forecast() {
            return tracker.lift(OWNER, CompanionActivity.TRAVELLING);
        }

        /**
         * Ticks past the intent's evaluation interval before judging. The
         * shipped definition is only considered every so many ticks, so a
         * single tick would report "did not fire" for an intent that was merely
         * not due — which is how this check first failed on data that was
         * correct.
         */
        private boolean tickAndCheck() {
            for (int tick = 0; tick < EVALUATION_WINDOW_TICKS; tick++) {
                orchestrator.tick("maid", clock + elapsed);
                elapsed++;
                if (stillActive()) {
                    return true;
                }
            }
            return false;
        }

        /** Whether the follow is the intent she is running right now. */
        private boolean stillActive() {
            for (int tick = 0; tick < EVALUATION_WINDOW_TICKS; tick++) {
                if (!INTENT.equals(
                        orchestrator.inspect("maid").activeIntent()
                )) {
                    return false;
                }
                orchestrator.tick("maid", clock + elapsed);
                elapsed++;
            }
            return true;
        }
    }

    private static IntentDefinition shippedIntent() throws IOException {
        return IntentDefinitionCodec.parse(
                INTENT,
                resource(
                        MaidIntentReloadListener.INTENT_PREFIX
                                + "/anticipate_departure.json"
                )
        ).result().orElseThrow(() ->
                new AssertionError("anticipate_departure.json does not parse"));
    }

    private static PlanDefinition shippedPlan() throws IOException {
        return PlanDefinitionCodec.parse(
                id("follow_owner"),
                resource(
                        MaidIntentReloadListener.PLAN_PREFIX
                                + "/follow_owner.json"
                )
        ).result().orElseThrow(() ->
                new AssertionError("follow_owner.json does not parse"));
    }

    private static JsonElement resource(String path) throws IOException {
        InputStream stream = OwnerFollowVerification.class
                .getClassLoader()
                .getResourceAsStream(RESOURCE_ROOT + path);
        if (stream == null) {
            throw new IOException("Missing resource " + RESOURCE_ROOT + path);
        }
        try (InputStreamReader reader = new InputStreamReader(
                stream,
                StandardCharsets.UTF_8
        )) {
            return JsonParser.parseReader(reader);
        }
    }

    private static OrchestrationId id(String path) {
        return new OrchestrationId("tlm_companionship", path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
