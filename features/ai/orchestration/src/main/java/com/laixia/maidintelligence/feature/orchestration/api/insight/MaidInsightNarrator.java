package com.laixia.maidintelligence.feature.orchestration.api.insight;

import com.laixia.maidintelligence.feature.orchestration.api.DecisionTrace;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reduces a decision trace to the few things worth telling a player.
 *
 * <p>The trace already contains the answer; the problem is that it contains
 * eight candidates, thirty facts and a catalog generation, and a panel that
 * showed all of it would be the same wall of numbers {@code ai explain} already
 * prints. Everything here is a decision about what to leave out.
 */
public final class MaidInsightNarrator {
    public static final int DEFAULT_MAX_ALTERNATIVES = 3;
    public static final int DEFAULT_MAX_NOTES = 3;

    /**
     * How far a forecast must sit from "as likely as usual" before it is worth
     * mentioning. Below this the honest report is silence — a panel that always
     * shows a hunch trains the player to ignore it, and the one time it matters
     * it will look the same as the hundred times it did not.
     */
    private static final double HUNCH_MARGIN = 0.10D;

    /**
     * Context observations required before a hunch is shown at all. A forecast
     * built on two sightings can clear the margin above by coincidence.
     */
    private static final int HUNCH_MINIMUM_EVIDENCE = 5;

    private static final String BLOCKED_PREFIX = "blocked:";

    private MaidInsightNarrator() {
    }

    public static MaidInsight summarize(
            DecisionTrace trace,
            List<NoteRule> noteRules,
            MaidInsight.Hunch hunch
    ) {
        return summarize(
                trace,
                noteRules,
                hunch,
                DEFAULT_MAX_ALTERNATIVES,
                DEFAULT_MAX_NOTES
        );
    }

    public static MaidInsight summarize(
            DecisionTrace trace,
            List<NoteRule> noteRules,
            MaidInsight.Hunch hunch,
            int maxAlternatives,
            int maxNotes
    ) {
        if (trace == null) {
            return MaidInsight.idle();
        }
        IntentTrace intent = trace.intent();
        OrchestrationId doing = intent.activeIntent();
        return new MaidInsight(
                doing,
                intent.activeState(),
                elapsed(trace.evaluatedAtTick(), intent.activeSinceTick()),
                alternatives(intent, doing, maxAlternatives),
                notes(trace.facts(), noteRules, maxNotes),
                worthMentioning(hunch) ? hunch : null
        );
    }

    /**
     * Builds the "and why not something else" list.
     *
     * <p>Ranked by how much each line explains rather than by score, because a
     * named failing condition tells the player something they can act on and a
     * near-miss score does not. Candidates that were merely not due for
     * evaluation are dropped outright: "she has not checked yet" is true of most
     * intents most of the time and would crowd out every useful line.
     */
    private static List<MaidInsight.Alternative> alternatives(
            IntentTrace intent,
            OrchestrationId doing,
            int limit
    ) {
        if (limit <= 0) {
            return List.of();
        }
        List<MaidInsight.Alternative> ranked = new ArrayList<>();
        for (IntentTrace.Candidate candidate : intent.candidates()) {
            if (candidate.intent().equals(doing)) {
                continue;
            }
            InsightReason reason =
                    InsightReason.fromStatus(candidate.status());
            if (reason == InsightReason.NOT_DUE) {
                continue;
            }
            ranked.add(new MaidInsight.Alternative(
                    candidate.intent(),
                    candidate.score(),
                    reason,
                    blockingFact(candidate.status())
            ));
        }
        ranked.sort(
                Comparator
                        .comparingInt((MaidInsight.Alternative alternative) ->
                                alternative.reason().explanatoryRank())
                        .thenComparing(
                                MaidInsight.Alternative::score,
                                Comparator.reverseOrder()
                        )
                        // Ties settled by id so two clients showing the same
                        // maid never disagree about the order.
                        .thenComparing(MaidInsight.Alternative::intent)
        );
        return ranked.size() <= limit
                ? List.copyOf(ranked)
                : List.copyOf(ranked.subList(0, limit));
    }

    private static List<MaidInsight.Note> notes(
            Map<OrchestrationId, Double> facts,
            List<NoteRule> rules,
            int limit
    ) {
        if (rules == null || rules.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<MaidInsight.Note> fired = new ArrayList<>();
        java.util.Set<OrchestrationId> spoken = new java.util.HashSet<>();
        for (NoteRule rule : rules) {
            if (fired.size() >= limit) {
                break;
            }
            /*
             * One note per fact, first rule wins. Severity tiers are written as
             * several rules over the same fact — "starving" before "a bit
             * hungry" — and without this both would fire and the panel would
             * contradict itself in two consecutive lines.
             */
            if (spoken.contains(rule.fact())) {
                continue;
            }
            Double actual = facts.get(rule.fact());
            if (actual != null && rule.firesOn(actual)) {
                fired.add(new MaidInsight.Note(rule.topic(), actual));
                spoken.add(rule.fact());
            }
        }
        return List.copyOf(fired);
    }

    private static boolean worthMentioning(MaidInsight.Hunch hunch) {
        return hunch != null
                && hunch.evidence() >= HUNCH_MINIMUM_EVIDENCE
                && Math.abs(hunch.lift() - 0.5D) >= HUNCH_MARGIN;
    }

    private static OrchestrationId blockingFact(String status) {
        if (status == null || !status.startsWith(BLOCKED_PREFIX)) {
            return null;
        }
        try {
            return OrchestrationId.parse(
                    status.substring(BLOCKED_PREFIX.length())
            );
        } catch (IllegalArgumentException malformed) {
            // The reason is still shown, just without naming the condition.
            return null;
        }
    }

    private static int elapsed(long now, long since) {
        if (since < 0L || now < since) {
            return 0;
        }
        long ticks = now - since;
        return ticks > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ticks;
    }
}
