package com.laixia.maidintelligence.feature.orchestration.api.insight;

/**
 * Why an intent is or is not the one running, in the terms a player would ask
 * the question.
 *
 * <p>The selection engine records richer statuses than this, including which
 * exact condition failed. That detail is kept separately on
 * {@link MaidInsight.Alternative}; this enum is only the shape of the answer,
 * so a display can pick a sentence without parsing a status string.
 */
public enum InsightReason {
    /** Running right now. */
    ACTIVE,
    /** Allowed, but something else scored higher. */
    OUT_SCORED,
    /** A condition does not hold; the fact is named on the alternative. */
    CONDITION,
    /** Ran recently and is waiting out its cooldown. */
    COOLDOWN,
    /** Scored below its own minimum, so it was never a real candidate. */
    TOO_WEAK,
    /** Simply not due to be evaluated yet. */
    NOT_DUE;

    /**
     * Classifies a raw selection-trace status.
     *
     * <p>Unknown statuses map to {@link #CONDITION} rather than throwing: a new
     * block reason added later should read as "something is stopping her",
     * which is roughly right, instead of breaking the panel.
     */
    public static InsightReason fromStatus(String status) {
        if (status == null) {
            return CONDITION;
        }
        return switch (status) {
            case "eligible" -> OUT_SCORED;
            case "cooldown" -> COOLDOWN;
            case "below_minimum" -> TOO_WEAK;
            case "waiting" -> NOT_DUE;
            default -> CONDITION;
        };
    }

    /**
     * How much this reason explains, highest first.
     *
     * <p>A named failing condition answers "why not?" outright, so it outranks
     * a bare "something else won" no matter how the scores compare. Ordering by
     * score alone would fill a short list with near-misses and leave out the one
     * line that actually tells the player something.
     */
    public int explanatoryRank() {
        return switch (this) {
            case ACTIVE -> 0;
            case CONDITION -> 1;
            case COOLDOWN -> 2;
            case OUT_SCORED -> 3;
            case TOO_WEAK -> 4;
            case NOT_DUE -> 5;
        };
    }
}
