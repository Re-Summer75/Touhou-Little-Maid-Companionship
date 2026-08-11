package com.laixia.maidintelligence.feature.orchestration.tlm.combat.sustenance;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatCapability;
import com.laixia.maidintelligence.feature.behavior.domain.combat.sustenance.EatingPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.feature.status.domain.DefaultHungerPolicy;

/**
 * The fight's appetite: reading her pack, her hunger, and deciding to eat.
 *
 * <p>Gathered out of the fight itself because it is a different question with
 * different collaborators. The fight asks who to hit, whether to be there and
 * what to hold; this asks whether her hands would be better spent on a mouthful
 * — and needs a food scanner and a hunger reading that nothing else in the
 * fight has any use for.
 *
 * <p>It is asked once a tick and answers about that tick. Nothing is scheduled
 * and nothing is committed to: a quiet moment that stops being quiet takes the
 * decision with it, which is the behaviour a maid who eats mid-fight has to
 * have.
 */
public final class CombatAppetite {
    /** Her hunger ceiling, from the policy the rest of the mod drives it with. */
    private static final int MAX_HUNGER = DefaultHungerPolicy.MAX_HUNGER;

    private final TlmFoodScanner larder = new TlmFoodScanner();
    private final MaidStatusApi<EntityMaid> status;

    /**
     * @param status where her hunger is kept, or {@code null} when nobody is
     *               tracking it. Hunger then reads as full, which costs exactly
     *               one of the four eating rules — the one that spends a quiet
     *               moment on a mouthful. The three that are about the fight go
     *               on working, so a scenario built without a status still
     *               exercises them.
     */
    public CombatAppetite(MaidStatusApi<EntityMaid> status) {
        this.status = status;
    }

    /**
     * Take a mouthful if the fight is better for it.
     *
     * <p>Once she is chewing she is left alone: interrupting a mouthful spends
     * the seconds without buying the food, which is strictly the worst of the
     * three things that could happen.
     *
     * @return whether her hands are busy with food this tick
     */
    public boolean consider(
            EntityMaid maid,
            ThreatField field,
            CombatCapability capability,
            double healthFraction,
            boolean canOpenGround
    ) {
        if (MaidEating.chewing(maid)) {
            return true;
        }
        // A draw under way is deliberately *not* protected the way a mouthful
        // is, and that asymmetry was measured rather than assumed. Deferring
        // the meal until the arrow goes off reads as obviously right — a bow
        // abandoned at seventeen ticks of twenty spent the seconds and bought
        // nothing — and over twenty-four trials it bought nothing either: her
        // arrow damage went *down*, 28.5 to 25.0, because the draws it saved
        // were not the reason she was short of arrows.
        //
        // What it did instead is stop her eating almost entirely, peak
        // absorption 4.0 to 1.0. This class holds no state on purpose, so a
        // decision that cannot act on the tick it is made is a decision
        // discarded — and inside a twenty-one tick draw cycle there is exactly
        // one tick where her hands are free. Twenty times in twenty-one the
        // appetite would speak and nothing would hear it.
        //
        // So the draw is interrupted, and the cost of that is real and small.
        // Anything that wants to protect it has to carry the intent across the
        // draw, not merely decline to act during it.
        MaidEating.begin(maid, EatingPolicy.instance().choose(
                larder.scan(maid),
                field,
                capability,
                healthFraction,
                hungerFraction(maid),
                canOpenGround
        ));
        return MaidEating.chewing(maid);
    }

    /** Her hunger over its maximum, or full when nobody is tracking it. */
    private double hungerFraction(EntityMaid maid) {
        if (status == null) {
            return 1.0D;
        }
        return Math.max(0.0D, Math.min(
                1.0D,
                status.getState(maid).hunger() / (double) MAX_HUNGER
        ));
    }
}
