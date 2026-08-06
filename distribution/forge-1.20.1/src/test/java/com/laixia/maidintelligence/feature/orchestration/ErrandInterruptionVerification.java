package com.laixia.maidintelligence.feature.orchestration;

import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;

import java.util.List;

/**
 * What may and may not stop a maid who is already on her way.
 *
 * <p>Conditions serve two purposes that look alike and are not: deciding
 * whether she may begin, and deciding whether she may carry on. The engine
 * re-tests them every tick while an intent is active, so a condition written
 * for the first was silently enforcing the second — and a maid walking ten
 * blocks to a cabinet was cancelled halfway by anything that brushed her aside
 * for a single tick.
 */
public final class ErrandInterruptionVerification {
    private ErrandInterruptionVerification() {
    }

    public static void main(String[] args) {
        aRunningErrandSurvivesSoftOccupancy();
    }

    /**
     * An errand under way must survive a passing distraction.
     *
     * <p>Conditions are re-tested every tick while an intent is active, and a
     * failure cancels at once — the commitment window does not protect this
     * path. So a condition written to mean "she must be free to start" also
     * means "she must stay free throughout", and a maid walking ten blocks to a
     * cabinet was cancelled the moment any soft native behaviour touched her
     * movement, halfway there, every time.
     *
     * <p>This is why the two intents that already walked any distance had been
     * loosened by hand. The condition belongs at entry; while running, only a
     * hard occupancy should be able to stop her.
     */
    private static void aRunningErrandSurvivesSoftOccupancy() {
        IntentVerificationFixture fixture = new IntentVerificationFixture();
        PlanDefinition plan = IntentVerificationFixture.plan(
                IntentVerificationFixture.id("plan/walk"),
                IntentVerificationFixture.WAIT,
                200
        );
        IntentDefinition errand = IntentVerificationFixture.definition(
                IntentVerificationFixture.id("intent/errand"),
                IntentVerificationFixture.id("plan/walk"),
                List.of(new FactCondition(
                        IntentVerificationFixture.FACT_A,
                        FactComparison.EQUAL,
                        0.0D,
                        true
                )),
                1.0D,
                20,
                60,
                0.1D
        );
        fixture.publish(1L, List.of(errand), List.of(plan));

        // Idle: she sets off.
        fixture.facts.put(IntentVerificationFixture.FACT_A, 0.0D);
        fixture.intents.tick("maid", 0L);
        require(errand.id().equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "The errand never started");

        // A native behaviour touches her movement mid-walk: soft, not hard.
        fixture.facts.put(IntentVerificationFixture.FACT_A, 1.0D);
        fixture.intents.tick("maid", 10L);
        require(errand.id().equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "A soft distraction cancelled an errand already under way");

        /*
         * And an entry-only condition stays out of the way for good: what
         * stops her mid-errand has to be a condition that says so, not the one
         * that decided she could set off.
         */
        fixture.facts.put(IntentVerificationFixture.FACT_A, 2.0D);
        fixture.intents.tick("maid", 20L);
        require(errand.id().equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "An entry-only condition cancelled an errand under way");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
