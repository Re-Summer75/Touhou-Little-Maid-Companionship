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
 * The shipped anticipation intent, driven by a shipped-shaped owner.
 *
 * <p>Written against the real {@code anticipate_departure.json} rather than an
 * inline copy, because the thing most likely to be wrong is the threshold in
 * that file. Travelling is close to half of all activity transitions, so a
 * probability that merely looks high says nothing; what the intent needs is a
 * context where travelling is likely <em>relative to how often it happens
 * anyway</em>, and only running the real numbers shows whether the authored
 * threshold sits there.
 */
public final class AnticipationBehaviourVerification {
    private static final String RESOURCE_ROOT = "data/tlm_companionship/";

    /** Comfortably past the shipped {@code evaluation_interval_ticks}. */
    private static final int EVALUATION_WINDOW_TICKS = 60;
    private static final OrchestrationId INTENT =
            id("intent/anticipate_departure");
    private static final UUID OWNER = UUID.nameUUIDFromBytes(
            "anticipation".getBytes(StandardCharsets.UTF_8)
    );

    private AnticipationBehaviourVerification() {
    }

    public static void main(String[] args) throws IOException {
        aColdOwnerIsNeverAnticipated();
        workingOwnerIsAnticipated();
        justMovedOwnerIsNotAnticipated();
        aNearbyOwnerIsNotChased();
        reportContexts();
    }

    private static void aColdOwnerIsNeverAnticipated() throws IOException {
        Harness harness = new Harness();
        require(!harness.tickAndCheck(),
                "The maid anticipated a departure before learning anything");
    }

    /**
     * Mid-task is exactly when the owner is about to move on, and it is the
     * case the whole behaviour exists for.
     */
    private static void workingOwnerIsAnticipated() throws IOException {
        Harness harness = new Harness();
        harness.learnRealisticRoutine();
        harness.settleInto(
                CompanionActivity.TRAVELLING,
                CompanionActivity.BUILDING
        );
        require(harness.tickAndCheck(),
                "A working owner was not anticipated (forecast "
                        + harness.forecast() + ")");
    }

    /**
     * The other half of the same claim. An owner who just relocated is the
     * least likely to relocate again, and a maid that chased them anyway would
     * simply be following, not anticipating.
     */
    private static void justMovedOwnerIsNotAnticipated() throws IOException {
        Harness harness = new Harness();
        harness.learnRealisticRoutine();
        harness.settleInto(
                CompanionActivity.MINING,
                CompanionActivity.TRAVELLING
        );
        require(!harness.tickAndCheck(),
                "The maid anticipated a departure straight after one "
                        + "(forecast " + harness.forecast() + ")");
    }

    private static void aNearbyOwnerIsNotChased() throws IOException {
        Harness harness = new Harness();
        harness.learnRealisticRoutine();
        harness.settleInto(
                CompanionActivity.TRAVELLING,
                CompanionActivity.BUILDING
        );
        harness.facts.put(CompanionIntentIds.OWNER_DISTANCE, 2.0D);
        require(!harness.tickAndCheck(),
                "The maid closed on an owner who was already beside her");
    }

    /**
     * Prints the contexts the threshold separates, so the authored value can be
     * re-derived rather than trusted after any change to the blend.
     */
    private static void reportContexts() throws IOException {
        StringBuilder report = new StringBuilder("anticipation contexts: ");
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
            /*
             * A fresh harness per probe. Reusing one carried the cooldown from
             * whichever context fired last, so two contexts with the same
             * forecast reported differently and the line implied a difference
             * in scoring that did not exist.
             */
            Harness harness = new Harness();
            harness.learnRealisticRoutine();
            harness.settleInto(probe[0], probe[1]);
            report.append(probe[0]).append("->").append(probe[1])
                    .append('=')
                    .append(String.format("%.2f", harness.forecast()))
                    .append(harness.tickAndCheck() ? "* " : " ");
        }
        System.out.println(report);
    }

    private static final class Harness {
        private final OwnerActivityTracker tracker = new OwnerActivityTracker();
        private final Map<OrchestrationId, Double> facts = new HashMap<>();
        private final MaidIntentApi<String> orchestrator;
        private long clock;

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
                    (subject, action, parameters, gameTime, elapsed) ->
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
         * the end of the day — the shape the threshold was measured against.
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
         * shipped definition is only considered every twenty ticks, so a single
         * tick would report "did not fire" for an intent that was merely not
         * due — which is how this check first failed on data that was correct.
         */
        private boolean tickAndCheck() {
            for (int tick = 0; tick < EVALUATION_WINDOW_TICKS; tick++) {
                orchestrator.tick("maid", clock + tick);
                if (INTENT.equals(
                        orchestrator.inspect("maid").activeIntent()
                )) {
                    return true;
                }
            }
            return false;
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
                id("approach_owner"),
                resource(
                        MaidIntentReloadListener.PLAN_PREFIX
                                + "/approach_owner.json"
                )
        ).result().orElseThrow(() ->
                new AssertionError("approach_owner.json does not parse"));
    }

    private static JsonElement resource(String path) throws IOException {
        InputStream stream = AnticipationBehaviourVerification.class
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
