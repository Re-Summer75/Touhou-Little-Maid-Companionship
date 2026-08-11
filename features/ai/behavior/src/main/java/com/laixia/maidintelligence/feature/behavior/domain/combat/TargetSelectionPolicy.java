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
     *
     * <p>Health is deliberately not a tiebreak, and this is the second time
     * that has been established. Finishing a wounded one first is sound
     * arithmetic in the abstract — incoming damage is proportional to how many
     * are still standing, so a kill cuts it and a wound cuts nothing — but the
     * choice made here decides where her feet go, not only where she swings.
     * Preferring the hurt one therefore walks her past whatever is standing
     * between, and everything she passes gets a free hit.
     *
     * <p>It was tried with a guard limiting the preference to targets already
     * inside her weapon's reach. That is not enough: measured in play she still
     * fixated on wounded ones behind the front and took damage from the one at
     * her elbow. Concentrating damage has to come from somewhere that does not
     * also steer her — it is not this decision's to make.
     *
     * <p>And the third attempt found that "somewhere" does not exist in the
     * shot either, which is where the sentence above was pointing. Aiming the
     * bow at the most wounded while her feet went on answering to the nearest
     * looks free — an arrow does its own travelling, and from ten blocks they
     * are all equally in front of her. It is not free, because aiming is not
     * only aiming: the draw turns her head every tick, over the top of the
     * facing her movement just set, so the shots went to something far enough
     * away that mob accuracy threw most of them, while whatever was closest
     * walked in unwatched. Measured over twenty-four trials against an
     * otherwise identical maid, her arrow damage halved, 28.5 to 13.6, and the
     * share of the fight spent inside someone's reach went from a third to
     * two fifths.
     *
     * <p>Three attempts, three different mechanisms, one shape: every place
     * that can choose a different victim also points some part of her at it.
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
