package com.laixia.maidintelligence.feature.orchestration;

import com.laixia.maidintelligence.feature.orchestration.api.IntentMetrics;
import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * How often she changes her mind, measured rather than assumed.
 *
 * <p>Two intents can each be correct on their own and still combine into a maid
 * who does nothing: fetching from a cabinet and asking her owner for food are
 * both right answers to being hungry, and they are answers that undo each
 * other. She sets off, something makes the cabinet momentarily unavailable, she
 * turns to ask instead, the cabinet returns, she sets off again — and the only
 * visible symptom is a maid twitching in place.
 *
 * <p>The engine already counts switches, interruptions and cancellations. This
 * suite drives it over a realistic stretch of ticks and asserts on those
 * counts, because "she changed her mind eleven times in five seconds" is a
 * statement no single-tick assertion can make.
 */
public final class IntentThrashingVerification {
    /** Ticks a scenario runs for; long enough for a walk across a room. */
    private static final int RUN_TICKS = 200;

    private IntentThrashingVerification() {
    }

    public static void main(String[] args) {
        aFlickeringOpportunityDoesNotThrash();
        rivalAnswersToOneNeedSettleOnOne();
        aSteadyWorldNeverSwitches();
        aFlickeringGateDoesNotAbandonTheErrand();
        reportsHowOftenSheChangesHerMind();
    }

    /**
     * The shape of the reported problem: an errand whose own precondition comes
     * and goes while she walks, with a rival intent waiting to take over.
     *
     * <p>Cabinet availability is not a steady fact. It is recomputed from a
     * cached search and gated on her being unoccupied, so it drops out for a
     * tick here and there without the cabinet having changed at all.
     */
    private static void aFlickeringOpportunityDoesNotThrash() {
        IntentVerificationFixture fixture = rivalScenario();
        int[] flickers = {0};
        IntentMetrics before = fixture.intents.metrics();
        run(fixture, tick -> {
            // Hungry throughout.
            fixture.facts.put(IntentVerificationFixture.FACT_A, 1.0D);
            // The cabinet blinks out every twentieth tick, as the cached
            // search and the occupancy gate together make it do.
            boolean available = tick % 20 != 0;
            if (!available) {
                flickers[0]++;
            }
            fixture.facts.put(
                    IntentVerificationFixture.FACT_B,
                    available ? 1.0D : 0.0D
            );
        });
        IntentMetrics after = fixture.intents.metrics();

        long switches = after.switches() - before.switches();
        require(flickers[0] >= 5,
                "The scenario did not actually flicker");
        /*
         * One switch per flicker would mean she abandons the errand every time
         * the fact blinks. A handful across two hundred ticks is her reacting
         * to real change; a switch for every blink is thrashing.
         */
        require(switches <= flickers[0],
                "She changed her mind " + switches + " times over "
                        + flickers[0] + " flickers");
    }

    /**
     * Two ways to meet one need must not take turns forever. Whichever she
     * picks, she should stay with it long enough to finish.
     */
    private static void rivalAnswersToOneNeedSettleOnOne() {
        IntentVerificationFixture fixture = rivalScenario();
        IntentMetrics before = fixture.intents.metrics();
        run(fixture, tick -> {
            fixture.facts.put(IntentVerificationFixture.FACT_A, 1.0D);
            fixture.facts.put(IntentVerificationFixture.FACT_B, 1.0D);
        });
        IntentMetrics after = fixture.intents.metrics();

        long switches = after.switches() - before.switches();
        require(switches <= 1,
                "Two rival answers to one need swapped " + switches
                        + " times in a world that never changed");
    }

    /** The control: nothing changes, so nothing should. */
    private static void aSteadyWorldNeverSwitches() {
        IntentVerificationFixture fixture = rivalScenario();
        fixture.facts.put(IntentVerificationFixture.FACT_A, 1.0D);
        fixture.facts.put(IntentVerificationFixture.FACT_B, 1.0D);
        IntentMetrics before = fixture.intents.metrics();
        run(fixture, tick -> {
        });
        IntentMetrics after = fixture.intents.metrics();

        require(after.switches() - before.switches() == 0,
                "She changed her mind with nothing to change it about");
    }

    /**
     * The shape the food intents were rebuilt into, and the guard on it.
     *
     * <p>Their availability facts used to fold her freedom in with the
     * opportunity: hunger, movement, combat, panic, item use and occupancy were
     * all re-checked inside "is there a meal to be had". Every one of those is
     * a fact the intent already tests, so the bundle bought nothing and cost
     * the errand — a single tick of soft occupancy read as the meal vanishing.
     *
     * <p>Split apart, the opportunity holds steady while she walks and the
     * freedom test decides only whether she may set off. So a gate that blinks
     * must now cost nothing at all.
     */
    private static void aFlickeringGateDoesNotAbandonTheErrand() {
        IntentVerificationFixture fixture = gatedScenario();
        IntentMetrics before = fixture.intents.metrics();
        run(fixture, tick -> {
            // The opportunity is steady: the cabinet has food throughout.
            fixture.facts.put(IntentVerificationFixture.FACT_A, 1.0D);
            // Her freedom blinks, as native behaviour brushing past makes it.
            fixture.facts.put(
                    IntentVerificationFixture.FACT_B,
                    tick % 5 == 0 ? 1.0D : 0.0D
            );
        });
        IntentMetrics after = fixture.intents.metrics();

        long switches = after.switches() - before.switches();
        long cancellations = after.cancellations() - before.cancellations();
        require(switches <= 1,
                "A blinking gate cost " + switches + " changes of mind");
        require(cancellations <= 1,
                "A blinking gate cancelled the errand " + cancellations
                        + " times");
    }

    /**
     * One errand needing both a steady opportunity and a momentary freedom,
     * with the freedom marked as deciding only whether she may begin.
     */
    private static IntentVerificationFixture gatedScenario() {
        IntentVerificationFixture fixture = new IntentVerificationFixture();
        PlanDefinition fetch = IntentVerificationFixture.plan(
                IntentVerificationFixture.id("plan/fetch"),
                IntentVerificationFixture.WAIT,
                RUN_TICKS * 2
        );
        IntentDefinition errand = IntentVerificationFixture.definition(
                IntentVerificationFixture.id("intent/fetch"),
                IntentVerificationFixture.id("plan/fetch"),
                List.of(
                        condition(IntentVerificationFixture.FACT_A, 1.0D),
                        new FactCondition(
                                IntentVerificationFixture.FACT_B,
                                FactComparison.EQUAL,
                                0.0D,
                                true
                        )
                ),
                1.0D,
                20,
                60,
                0.1D
        );
        fixture.publish(1L, List.of(errand), List.of(fetch));
        return fixture;
    }

    /**
     * Not an assertion so much as a measurement, printed so the numbers can be
     * compared against a change rather than guessed at.
     *
     * <p>Run at several flicker rates: the question is not whether she ever
     * changes her mind but whether the rate at which the world wobbles is the
     * rate at which she does.
     */
    private static void reportsHowOftenSheChangesHerMind() {
        System.out.println("  sustain condition (as shipped)");
        System.out.println("  flicker period | switches | interruptions"
                + " | cancellations");
        for (int period : new int[]{5, 10, 20, 40, 80}) {
            IntentVerificationFixture fixture = rivalScenario(false);
            IntentMetrics before = fixture.intents.metrics();
            run(fixture, tick -> {
                fixture.facts.put(IntentVerificationFixture.FACT_A, 1.0D);
                fixture.facts.put(
                        IntentVerificationFixture.FACT_B,
                        tick % period == 0 ? 0.0D : 1.0D
                );
            });
            IntentMetrics after = fixture.intents.metrics();
            System.out.printf(
                    "  %13d | %8d | %13d | %13d%n",
                    period,
                    after.switches() - before.switches(),
                    after.interruptions() - before.interruptions(),
                    after.cancellations() - before.cancellations()
            );
        }

        System.out.println("  opportunity marked entry_only");
        System.out.println("  flicker period | switches | interruptions"
                + " | cancellations");
        for (int period : new int[]{5, 10, 20, 40, 80}) {
            IntentVerificationFixture fixture = rivalScenario(true);
            IntentMetrics before = fixture.intents.metrics();
            run(fixture, tick -> {
                fixture.facts.put(IntentVerificationFixture.FACT_A, 1.0D);
                fixture.facts.put(
                        IntentVerificationFixture.FACT_B,
                        tick % period == 0 ? 0.0D : 1.0D
                );
            });
            IntentMetrics after = fixture.intents.metrics();
            System.out.printf(
                    "  %13d | %8d | %13d | %13d%n",
                    period,
                    after.switches() - before.switches(),
                    after.interruptions() - before.interruptions(),
                    after.cancellations() - before.cancellations()
            );
        }
    }

    /**
     * A hungry maid, a cabinet she can walk to, and an owner she could ask
     * instead — the two intents that were reported to fight each other, with
     * the same commitment and margin the shipped ones carry.
     */
    private static IntentVerificationFixture rivalScenario() {
        return rivalScenario(false);
    }

    private static IntentVerificationFixture rivalScenario(
            boolean opportunityIsEntryOnly
    ) {
        IntentVerificationFixture fixture = new IntentVerificationFixture();
        PlanDefinition fetch = IntentVerificationFixture.plan(
                IntentVerificationFixture.id("plan/fetch"),
                IntentVerificationFixture.WAIT,
                RUN_TICKS * 2
        );
        PlanDefinition ask = IntentVerificationFixture.plan(
                IntentVerificationFixture.id("plan/ask"),
                IntentVerificationFixture.WAIT,
                RUN_TICKS * 2
        );
        // Needs the opportunity as well as the hunger, and scores highest.
        IntentDefinition fetchFromCabinet = IntentVerificationFixture.definition(
                IntentVerificationFixture.id("intent/fetch"),
                IntentVerificationFixture.id("plan/fetch"),
                List.of(
                        condition(IntentVerificationFixture.FACT_A, 1.0D),
                        new FactCondition(
                                IntentVerificationFixture.FACT_B,
                                FactComparison.EQUAL,
                                1.0D,
                                opportunityIsEntryOnly
                        )
                ),
                1.0D,
                20,
                60,
                0.1D
        );
        // Needs only the hunger, and scores lower.
        IntentDefinition askTheOwner = IntentVerificationFixture.definition(
                IntentVerificationFixture.id("intent/ask"),
                IntentVerificationFixture.id("plan/ask"),
                List.of(condition(IntentVerificationFixture.FACT_A, 1.0D)),
                0.6D,
                40,
                40,
                0.15D
        );
        fixture.publish(
                1L,
                List.of(fetchFromCabinet, askTheOwner),
                List.of(fetch, ask)
        );
        return fixture;
    }

    private static FactCondition condition(
            OrchestrationId fact,
            double expected
    ) {
        return new FactCondition(fact, FactComparison.EQUAL, expected);
    }

    private static void run(
            IntentVerificationFixture fixture,
            IntentConsumer world
    ) {
        for (int tick = 0; tick < RUN_TICKS; tick++) {
            world.accept(tick);
            fixture.intents.tick("maid", tick);
        }
    }

    /** Named rather than {@link IntConsumer} so the parameter reads as a tick. */
    @FunctionalInterface
    private interface IntentConsumer {
        void accept(int tick);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
