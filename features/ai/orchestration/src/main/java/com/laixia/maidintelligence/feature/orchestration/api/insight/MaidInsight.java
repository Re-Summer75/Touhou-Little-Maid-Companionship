package com.laixia.maidintelligence.feature.orchestration.api.insight;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.List;
import java.util.Objects;

/**
 * One maid's decision, reduced to what a player would want to be told.
 *
 * <p>Deliberately holds no text. {@code ai explain} already prints the raw
 * trace for someone debugging a data pack; this is the other audience, and the
 * words they should see depend on their language. Keeping the structure here
 * and the sentences at the display means the choice of <em>what is worth
 * saying</em> stays testable without a game or a locale.
 *
 * <p>Everything is already trimmed and ranked. A display should render the
 * lists in the order given and not re-sort them.
 */
public record MaidInsight(
        OrchestrationId doing,
        String step,
        int doingForTicks,
        List<Alternative> alternatives,
        List<Note> notes,
        Hunch hunch
) {
    public MaidInsight {
        step = step == null ? "" : step;
        alternatives = List.copyOf(alternatives);
        notes = List.copyOf(notes);
        if (doingForTicks < 0) {
            throw new IllegalArgumentException("doingForTicks must not be negative");
        }
    }

    public static MaidInsight idle() {
        return new MaidInsight(null, "", 0, List.of(), List.of(), null);
    }

    /** Whether anything is running; false is a normal, explainable state. */
    public boolean busy() {
        return doing != null;
    }

    public boolean hasHunch() {
        return hunch != null;
    }

    /**
     * Something she is not doing, and why.
     *
     * @param blockingFact the condition that failed, or {@code null} unless
     *                     {@code reason} is {@link InsightReason#CONDITION};
     *                     this is what turns "she won't fetch food" into
     *                     "there is no snack cabinet nearby"
     */
    public record Alternative(
            OrchestrationId intent,
            double score,
            InsightReason reason,
            OrchestrationId blockingFact
    ) {
        public Alternative {
            Objects.requireNonNull(intent, "intent");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * A fact currently worth mentioning, such as being hungry or unable to
     * move. Which facts qualify is supplied per game by {@link NoteRule},
     * because the orchestration layer does not know what any particular fact
     * means.
     */
    public record Note(String topic, double value) {
        public Note {
            Objects.requireNonNull(topic, "topic");
        }
    }

    /**
     * What she expects the owner to do next.
     *
     * @param lift     in {@code [0, 1)}, where {@code 0.5} is "as likely as
     *                 usual"
     * @param evidence how many transitions this context has been seen with,
     *                 carried so a display can say how sure she is instead of
     *                 presenting a guess as a fact
     */
    public record Hunch(String activity, double lift, int evidence) {
        public Hunch {
            Objects.requireNonNull(activity, "activity");
            if (!Double.isFinite(lift) || lift < 0.0D || lift >= 1.0D) {
                throw new IllegalArgumentException("Lift must be in [0, 1)");
            }
            if (evidence < 0) {
                throw new IllegalArgumentException("Evidence must not be negative");
            }
        }
    }
}
