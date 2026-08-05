package com.laixia.maidintelligence.feature.behavior.domain.forecast;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

/**
 * The owner activities the forecast predicts over.
 *
 * <p>Deliberately coarse. Every value has to be decidable from what an observer
 * can actually see of another entity — held item, posture, movement, recent
 * damage — because a category that cannot be recognised reliably contributes
 * noise to every transition it appears in, not just its own.
 *
 * <p>Each activity carries the fact it is published as, so adding one cannot
 * leave a fact id and an enum constant out of step.
 */
public enum CompanionActivity {
    IDLE("idle"),
    TRAVELLING("travelling"),
    MINING("mining"),
    BUILDING("building"),
    FARMING("farming"),
    COMBAT("combat"),
    RESTING("resting");

    private static final CompanionActivity[] VALUES = values();

    private final OrchestrationId forecastFact;
    private final OrchestrationId forecastLiftFact;

    CompanionActivity(String key) {
        this.forecastFact = new OrchestrationId(
                "tlm_companionship",
                "fact/forecast/" + key
        );
        this.forecastLiftFact = new OrchestrationId(
                "tlm_companionship",
                "fact/forecast_lift/" + key
        );
    }

    /**
     * The fact a data pack reads to ask "how likely is the owner to do this
     * next", already a probability in {@code [0, 1]} so a consideration can
     * normalize over that range with no scaling.
     */
    public OrchestrationId forecastFact() {
        return forecastFact;
    }

    /**
     * The fact to read for "is the owner <em>unusually</em> likely to do this
     * next", in {@code [0, 1)} with {@code 0.5} meaning exactly as likely as
     * usual.
     *
     * <p>Prefer this over {@link #forecastFact()} for anticipation. A raw
     * probability is not comparable between activities — travelling is nearly
     * half of all transitions and resting is rare, so the same number means
     * opposite things about the two, and a threshold copied from one activity
     * to another silently changes meaning. This one carries the same meaning
     * everywhere.
     */
    public OrchestrationId forecastLiftFact() {
        return forecastLiftFact;
    }

    public static int count() {
        return VALUES.length;
    }

    public static CompanionActivity byIndex(int index) {
        return VALUES[index];
    }
}
