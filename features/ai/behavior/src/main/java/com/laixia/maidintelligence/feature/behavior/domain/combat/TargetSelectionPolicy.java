package com.laixia.maidintelligence.feature.behavior.domain.combat;

import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;

import java.util.Collection;
import java.util.Objects;

/**
 * Picks which hostile to hit first.
 *
 * <p>Separate from whether to fight and from what to fight with, because the
 * three answers do not move together: she may be losing the overall exchange
 * and still have exactly one thing she must interrupt.
 *
 * <p>Relation comes before distance. A skeleton shooting her owner across the
 * room is a better target than a zombie at her elbow — the zombie is her
 * problem, and she is replaceable in a way her owner is not.
 */
public final class TargetSelectionPolicy {
    public static final TargetSelectionPolicy INSTANCE =
            new TargetSelectionPolicy();

    private TargetSelectionPolicy() {
    }

    /**
     * The one to hit, or {@code null} when there is nothing to hit.
     *
     * <p>Ties within a relation break on distance, so among several things
     * mobbing her owner she starts with the one already in reach rather than
     * walking past it.
     */
    public ThreatSample select(Collection<ThreatSample> samples) {
        Objects.requireNonNull(samples, "samples");
        ThreatSample chosen = null;
        for (ThreatSample sample : samples) {
            if (sample == null) {
                continue;
            }
            if (chosen == null || preferred(sample, chosen)) {
                chosen = sample;
            }
        }
        return chosen;
    }

    private boolean preferred(ThreatSample candidate, ThreatSample incumbent) {
        if (candidate.relation() != incumbent.relation()) {
            return candidate.relation().outranks(incumbent.relation());
        }
        return candidate.distance() < incumbent.distance();
    }
}
