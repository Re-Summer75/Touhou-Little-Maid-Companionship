package com.laixia.maidintelligence.feature.orchestration.tlm.combat.sustenance;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatCapability;
import com.laixia.maidintelligence.feature.behavior.domain.combat.sustenance.EatingPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.sustenance.FoodValue;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.feature.status.domain.DefaultHungerPolicy;

/**
 * Standing still and having a meal, out of anybody's way.
 *
 * <p>Free mode has a hunger bar and, until this existed, no way of ever
 * emptying a plate. The host registers automatic eating and free mode skips the
 * host's registration wholesale — deliberately, since every judgement here is
 * meant to be ours — so the bar simply drained and nothing in the world
 * responded to it. She would walk to a snack cabinet and pick food off the
 * floor, and then carry it forever.
 *
 * <p>The decision is the same one the fight makes, asked with an empty field.
 * That is not a shortcut: eating out of combat <em>is</em> the fight's quiet
 * moment with the fight taken away, and giving it a second rule of its own is
 * how "she is hungry" comes to mean two different things depending on whether
 * anything is chasing her.
 *
 * <p>She does not move. Walking to food is two other intents' business, and
 * both of them end by putting it in the pack this one reaches into.
 */
public final class EatFromPackAction {
    private static final int MAX_HUNGER = DefaultHungerPolicy.MAX_HUNGER;

    /**
     * What she brings to a fight that is not happening.
     *
     * <p>Any armed, healthy figure does: with an empty field the risk rules
     * never run, and the only rule that can fire is the one about a quiet
     * moment. Stating it here rather than measuring her keeps this action from
     * needing a weapon scanner it would never otherwise consult.
     */
    private static final CombatCapability AT_EASE =
            new CombatCapability(20.0D, 1.0D, 0.0D, 2.0D);

    private final TlmFoodScanner larder = new TlmFoodScanner();
    private final MaidStatusApi<EntityMaid> status;

    public EatFromPackAction(MaidStatusApi<EntityMaid> status) {
        this.status = status;
    }

    /**
     * Take a mouthful, and keep running until it is swallowed.
     *
     * <p>Reported as running while she chews so the intent holds her still for
     * the second and a half it takes; finishing the moment the food is in her
     * hand would let the next intent swap the weapon back and cancel the meal.
     */
    public ActionResult execute(EntityMaid maid) {
        if (MaidEating.chewing(maid)) {
            return ActionResult.RUNNING;
        }
        if (status == null) {
            return ActionResult.FAILED;
        }
        FoodValue mouthful = EatingPolicy.instance().choose(
                larder.scan(maid),
                ThreatField.EMPTY,
                AT_EASE,
                1.0D,
                hungerFraction(maid),
                true
        );
        if (!MaidEating.begin(maid, mouthful)) {
            // Nothing worth eating, or the pack moved under the scan. Failing
            // rather than succeeding matters: the plan is then re-selected next
            // tick instead of being recorded as a meal that happened.
            return ActionResult.FAILED;
        }
        return ActionResult.RUNNING;
    }

    /** Drop the mouthful if something more urgent takes over. */
    public void cancel(EntityMaid maid) {
        if (MaidEating.chewing(maid)) {
            maid.stopUsingItem();
        }
    }

    private double hungerFraction(EntityMaid maid) {
        return Math.max(0.0D, Math.min(
                1.0D,
                status.getState(maid).hunger() / (double) MAX_HUNGER
        ));
    }
}
