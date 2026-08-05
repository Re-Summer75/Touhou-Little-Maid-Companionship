package com.laixia.maidintelligence.feature.orchestration;

import com.laixia.maidintelligence.feature.orchestration.api.CompanionObservationSnapshot;
import com.laixia.maidintelligence.feature.orchestration.api.DecisionTrace;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.api.insight.InsightReason;
import com.laixia.maidintelligence.feature.orchestration.api.insight.MaidInsight;
import com.laixia.maidintelligence.feature.orchestration.api.insight.MaidInsightNarrator;
import com.laixia.maidintelligence.feature.orchestration.api.insight.NoteRule;
import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.FACT_A;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.FACT_B;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.id;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.require;

/**
 * What the soul lens is allowed to say, and — mostly — what it must leave out.
 *
 * <p>The trace this reads from carries eight candidates, thirty facts and a
 * catalog generation. Every check here is about the panel showing less than
 * that, and showing the right less: a named failing condition rather than a
 * near-miss score, silence rather than a forecast built on two sightings.
 */
public final class MaidInsightVerification {
    private static final OrchestrationId FETCH = id("intent/fetch");
    private static final OrchestrationId FOLLOW = id("intent/follow");
    private static final OrchestrationId REST = id("intent/rest");
    private static final OrchestrationId PLAY = id("intent/play");

    private MaidInsightVerification() {
    }

    public static void main(String[] args) {
        anIdleMaidStillExplainsItself();
        whatSheIsDoingIsNotListedAsAnAlternative();
        aFailedConditionNamesTheFactThatFailed();
        candidatesMerelyNotDueAreDropped();
        anExplanationOutranksANearMiss();
        theListIsCappedAndOrderingIsStable();
        notesFireOnlyOnTheRulesGiven();
        severityTiersProduceOneLine();
        aMissingFactNeverInventsANote();
        aWeakHunchIsNotShown();
        anEarnedHunchIsShown();
        elapsedTimeIsNeverNegative();
    }

    /**
     * The case a player is most likely to be holding the lens for: she is doing
     * nothing and they want to know why.
     */
    private static void anIdleMaidStillExplainsItself() {
        MaidInsight insight = summarize(
                trace(null, "", -1L, 100L, List.of(
                        candidate(FETCH, 0.8D, "blocked:" + FACT_A),
                        candidate(FOLLOW, 0.4D, "cooldown")
                ), Map.of()),
                null
        );
        require(!insight.busy(), "An idle maid was reported as busy");
        require(insight.doingForTicks() == 0,
                "Idle maid reported a running duration");
        require(insight.alternatives().size() == 2,
                "An idle maid must still explain what she is not doing");

        require(MaidInsightNarrator.summarize(null, List.of(), null)
                        .alternatives().isEmpty(),
                "A missing trace should summarize as idle, not throw");
    }

    private static void whatSheIsDoingIsNotListedAsAnAlternative() {
        MaidInsight insight = summarize(
                trace(FETCH, "approach", 40L, 100L, List.of(
                        candidate(FETCH, 0.9D, "eligible"),
                        candidate(FOLLOW, 0.5D, "eligible")
                ), Map.of()),
                null
        );
        require(FETCH.equals(insight.doing()), "The active intent was lost");
        require("approach".equals(insight.step()), "The plan step was lost");
        require(insight.alternatives().size() == 1
                        && insight.alternatives().get(0).intent().equals(FOLLOW),
                "The running intent was repeated as an alternative");
    }

    /**
     * The line that makes the panel useful rather than decorative: not "she
     * will not fetch food" but "there is no snack cabinet".
     */
    private static void aFailedConditionNamesTheFactThatFailed() {
        MaidInsight insight = summarize(
                trace(null, "", -1L, 10L, List.of(
                        candidate(FETCH, 0.0D, "blocked:" + FACT_B)
                ), Map.of()),
                null
        );
        MaidInsight.Alternative blocked = insight.alternatives().get(0);
        require(blocked.reason() == InsightReason.CONDITION,
                "A blocked candidate was not classified as a condition");
        require(FACT_B.equals(blocked.blockingFact()),
                "The failing condition was not named");

        // A malformed status still produces a usable line, minus the fact.
        MaidInsight malformed = summarize(
                trace(null, "", -1L, 10L, List.of(
                        candidate(FETCH, 0.0D, "blocked:not an id")
                ), Map.of()),
                null
        );
        require(malformed.alternatives().get(0).reason()
                        == InsightReason.CONDITION,
                "A malformed block status dropped the alternative entirely");
        require(malformed.alternatives().get(0).blockingFact() == null,
                "A malformed block status invented a fact");
    }

    /**
     * Almost every intent is "not due" at almost every tick, so keeping those
     * lines would push out the ones that say something.
     */
    private static void candidatesMerelyNotDueAreDropped() {
        MaidInsight insight = summarize(
                trace(null, "", -1L, 10L, List.of(
                        candidate(FETCH, 0.9D, "waiting"),
                        candidate(FOLLOW, 0.1D, "cooldown")
                ), Map.of()),
                null
        );
        require(insight.alternatives().size() == 1,
                "A not-due candidate was kept");
        require(insight.alternatives().get(0).intent().equals(FOLLOW),
                "The wrong candidate survived");
    }

    /**
     * Ranking is by how much a line explains, not by score. Sorting by score
     * would put the highest near-miss first and bury the one candidate whose
     * failure the player could actually do something about.
     */
    private static void anExplanationOutranksANearMiss() {
        MaidInsight insight = summarize(
                trace(FOLLOW, "walk", 0L, 10L, List.of(
                        candidate(PLAY, 0.95D, "eligible"),
                        candidate(FETCH, 0.05D, "blocked:" + FACT_A)
                ), Map.of()),
                null
        );
        require(insight.alternatives().get(0).intent().equals(FETCH),
                "A near-miss outranked a named failing condition");
        require(insight.alternatives().get(1).intent().equals(PLAY),
                "The out-scored candidate was dropped instead of demoted");
    }

    private static void theListIsCappedAndOrderingIsStable() {
        List<IntentTrace.Candidate> many = List.of(
                candidate(PLAY, 0.5D, "eligible"),
                candidate(FOLLOW, 0.5D, "eligible"),
                candidate(REST, 0.5D, "eligible"),
                candidate(FETCH, 0.5D, "eligible")
        );
        MaidInsight first = MaidInsightNarrator.summarize(
                trace(null, "", -1L, 10L, many, Map.of()),
                List.of(),
                null,
                2,
                3
        );
        require(first.alternatives().size() == 2, "The cap was not applied");

        // Identical scores must not leave the order to chance: two clients
        // looking at one maid have to agree.
        MaidInsight second = MaidInsightNarrator.summarize(
                trace(null, "", -1L, 10L, many, Map.of()),
                List.of(),
                null,
                2,
                3
        );
        require(first.alternatives().equals(second.alternatives()),
                "Equal scores produced an unstable order");
        require(first.alternatives().get(0).intent().equals(FETCH),
                "Ties were not broken by intent id");
    }

    private static void notesFireOnlyOnTheRulesGiven() {
        Map<OrchestrationId, Double> facts = new LinkedHashMap<>();
        facts.put(FACT_A, 12.0D);
        facts.put(FACT_B, 1.0D);
        List<NoteRule> rules = List.of(
                new NoteRule(FACT_A, FactComparison.LESS_OR_EQUAL, 20.0D, "hungry"),
                new NoteRule(FACT_A, FactComparison.GREATER_THAN, 90.0D, "full"),
                new NoteRule(FACT_B, FactComparison.EQUAL, 1.0D, "stuck")
        );
        MaidInsight insight = MaidInsightNarrator.summarize(
                trace(null, "", -1L, 10L, List.of(), facts),
                rules,
                null
        );
        require(insight.notes().size() == 2,
                "Expected exactly the two firing rules, got "
                        + insight.notes().size());
        require("hungry".equals(insight.notes().get(0).topic())
                        && "stuck".equals(insight.notes().get(1).topic()),
                "Notes did not follow the order the rules were given in");
        require(insight.notes().get(0).value() == 12.0D,
                "The note lost the value that triggered it");
    }

    /**
     * Severity tiers are several rules over one fact. Both would fire on a low
     * enough value, and a panel saying "starving" and "a bit hungry" in
     * consecutive lines reads as broken rather than detailed.
     */
    private static void severityTiersProduceOneLine() {
        Map<OrchestrationId, Double> facts = new LinkedHashMap<>();
        facts.put(FACT_A, 3.0D);
        List<NoteRule> tiers = List.of(
                new NoteRule(FACT_A, FactComparison.LESS_OR_EQUAL, 6.0D,
                        "very_hungry"),
                new NoteRule(FACT_A, FactComparison.LESS_OR_EQUAL, 20.0D,
                        "hungry")
        );
        MaidInsight insight = MaidInsightNarrator.summarize(
                trace(null, "", -1L, 10L, List.of(), facts),
                tiers,
                null
        );
        require(insight.notes().size() == 1,
                "Both severity tiers fired for one fact");
        require("very_hungry".equals(insight.notes().get(0).topic()),
                "The more severe tier did not win");

        // The milder tier must still work on its own.
        facts.put(FACT_A, 15.0D);
        require("hungry".equals(MaidInsightNarrator.summarize(
                        trace(null, "", -1L, 10L, List.of(), facts),
                        tiers,
                        null
                ).notes().get(0).topic()),
                "The milder tier stopped firing once ordered behind another");
    }

    private static void aMissingFactNeverInventsANote() {
        List<NoteRule> rules = List.of(
                new NoteRule(FACT_A, FactComparison.LESS_OR_EQUAL, 20.0D, "hungry")
        );
        require(MaidInsightNarrator.summarize(
                        trace(null, "", -1L, 10L, List.of(), Map.of()),
                        rules,
                        null
                ).notes().isEmpty(),
                "A note fired on a fact the reader never supplied");

        Map<OrchestrationId, Double> broken = new LinkedHashMap<>();
        broken.put(FACT_A, Double.NaN);
        require(MaidInsightNarrator.summarize(
                        trace(null, "", -1L, 10L, List.of(), broken),
                        rules,
                        null
                ).notes().isEmpty(),
                "A note fired on an unreadable fact");
    }

    /**
     * A panel that always shows a hunch teaches the player to ignore it.
     */
    private static void aWeakHunchIsNotShown() {
        require(!summarize(
                        emptyTrace(),
                        new MaidInsight.Hunch("travelling", 0.52D, 100)
                ).hasHunch(),
                "A forecast barely off baseline was shown");
        require(!summarize(
                        emptyTrace(),
                        new MaidInsight.Hunch("travelling", 0.95D, 2)
                ).hasHunch(),
                "A confident-looking forecast built on two sightings was shown");
    }

    private static void anEarnedHunchIsShown() {
        MaidInsight insight = summarize(
                emptyTrace(),
                new MaidInsight.Hunch("travelling", 0.66D, 40)
        );
        require(insight.hasHunch(), "An earned forecast was suppressed");
        require(insight.hunch().evidence() == 40,
                "The evidence count was lost, so the panel cannot say how sure "
                        + "she is");
    }

    private static void elapsedTimeIsNeverNegative() {
        // A trace evaluated before the intent started is not expected, but a
        // negative duration on screen would be worse than a zero.
        MaidInsight insight = summarize(
                trace(FETCH, "approach", 500L, 100L, List.of(), Map.of()),
                null
        );
        require(insight.doingForTicks() == 0,
                "A trace older than its active intent produced a negative "
                        + "duration");
    }

    private static MaidInsight summarize(
            DecisionTrace trace,
            MaidInsight.Hunch hunch
    ) {
        return MaidInsightNarrator.summarize(trace, List.of(), hunch);
    }

    private static DecisionTrace emptyTrace() {
        return trace(null, "", -1L, 10L, List.of(), Map.of());
    }

    private static DecisionTrace trace(
            OrchestrationId active,
            String step,
            long activeSince,
            long evaluatedAt,
            List<IntentTrace.Candidate> candidates,
            Map<OrchestrationId, Double> facts
    ) {
        return new DecisionTrace(
                UUID.nameUUIDFromBytes("decision".getBytes()),
                evaluatedAt,
                1L,
                new IntentTrace(active, step, activeSince, candidates, "none"),
                facts,
                CompanionObservationSnapshot.empty(),
                new UUID(0L, 0L),
                new UUID(0L, 0L)
        );
    }

    private static IntentTrace.Candidate candidate(
            OrchestrationId intent,
            double score,
            String status
    ) {
        return new IntentTrace.Candidate(intent, score, status);
    }
}
