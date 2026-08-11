package com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponKind;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/**
 * When to let go, asked of the weapon rather than counted out in ticks.
 *
 * <p>Every ranged weapon answers "am I ready" differently, and the numbers are
 * not ours to know: a modded bow may reach full draw in five ticks, a modded
 * crossbow may load in forty, and enchantments move both. Hard-coding any of
 * them produces the same failure — she holds the weapon at full draw forever,
 * waiting for a moment defined by a different weapon than the one she is
 * holding.
 *
 * <p>So the order here is: state first ({@code isCharged} on anything that
 * tracks it), then the item's own declared use duration, and only then a curve.
 * The last resort is a timeout, which exists solely so an item that never
 * reports ready still fires instead of freezing her.
 */
public final class RangedDrawCycle {
    /**
     * The point past which she fires regardless.
     *
     * <p>Not a charge time — a deadlock breaker. Any weapon whose readiness
     * this class cannot read would otherwise take her out of the fight
     * permanently, which is strictly worse than one weak shot.
     */
    private static final int MAX_DRAW_TICKS = 60;

    /**
     * Above this, a declared use duration means "hold as long as you like".
     *
     * <p>Vanilla bows and tridents say 72000 ticks — an hour — which is the
     * idiom for unbounded rather than a real duration. Anything genuinely
     * bounded (a crossbow's load time) sits far below this, so the split
     * separates "use it up" from "charge it until it is worth releasing"
     * without naming a single item.
     */
    private static final int HELD_INDEFINITELY_TICKS = 200;

    private RangedDrawCycle() {
    }

    /**
     * Draw, hold, release — the same three steps a player performs.
     *
     * <p>Firing straight from {@code performRangedAttack} does launch an arrow,
     * but it is not shooting: the bow never enters its using state, so it never
     * bends on screen, and the shot carries a made-up power instead of the one
     * her draw earned. Both are visible — arrows leaving a slack bow at uniform
     * speed.
     *
     * <p>The draw itself is entity state, so it survives between ticks without
     * the caller having to remember anything: the bow is either being held or it
     * is not, and how long for is {@code getTicksUsingItem}.
     */
    public static void shoot(
            EntityMaid maid,
            LivingEntity victim,
            TlmWeaponScanner weapons
    ) {
        maid.getLookControl().setLookAt(
                victim.getX(), victim.getEyeY(), victim.getZ()
        );
        if (!maid.hasLineOfSight(victim)) {
            // Nothing to aim at any more; relax rather than hold a full draw.
            if (maid.isUsingItem()) {
                maid.stopUsingItem();
            }
            return;
        }
        if (!maid.isUsingItem()) {
            maid.setSwingingArms(true);
            maid.startUsingItem(InteractionHand.MAIN_HAND);
            return;
        }
        release(maid, victim, weapons);
    }

    /**
     * Let a completed draw go, and leave an unfinished one alone.
     *
     * <p>Called from every path that would otherwise abandon a draw. The rule it
     * enforces is that a draw only ever moves forward: it advances, or it becomes
     * an arrow. Nothing discards one except the fight itself ending.
     *
     * <p>That rule is not a nicety. A bow reaches full at twenty ticks, so a draw
     * reset even once every nineteen ticks produces no arrows at all, forever,
     * while looking exactly like a maid aiming carefully.
     */
    public static void releaseOrKeepDraw(
            EntityMaid maid,
            LivingEntity victim,
            TlmWeaponScanner weapons
    ) {
        if (!maid.isUsingItem()) {
            return;
        }
        if (victim == null || !maid.hasLineOfSight(victim)) {
            maid.stopUsingItem();
            return;
        }
        release(maid, victim, weapons);
    }

    /** Fire if the weapon says it is ready; otherwise keep drawing. */
    private static void release(
            EntityMaid maid,
            LivingEntity victim,
            TlmWeaponScanner weapons
    ) {
        ItemStack weapon = maid.getMainHandItem();
        if (!readyToRelease(
                maid,
                weapon,
                weapons.classifyFor(weapon),
                weapons.externalRecognizer()
        )) {
            return;
        }
        // Power is read before letting go: releasing clears the draw.
        float power = releasePower(maid, weapon);
        // releaseUsingItem, not stopUsingItem. Only the former runs the item's
        // own releaseUsing hook, and for a crossbow that hook *is* the loading
        // step — stopping instead threw away the draw and left the weapon empty,
        // so the shot that followed had nothing to fire. A bow does not notice
        // the difference because the host builds its arrow itself.
        maid.releaseUsingItem();
        // The host's crossbow route ignores the victim it is handed and reads
        // the maid's own target back off the entity instead, so firing with no
        // target set dereferences null and takes the server down. It crashed
        // in play the moment she broke off mid-draw with a crossbow, which is
        // precisely when the target has been let go.
        //
        // And the target has to be planted where the host looks for it:
        // {@code EntityMaid#getTarget} does not read the vanilla field at all,
        // it reads {@code ATTACK_TARGET} out of the brain. A {@code setTarget}
        // here is a no-op as far as the crossbow is concerned — that was the
        // first attempt at this fix, and it fixed nothing.
        //
        // Guarded here rather than at the call sites because callers cannot be
        // expected to know that firing has a precondition on state they were
        // right to clear. Withdrawing sets no target at all — only the fighting
        // branch does — so every tick she spends breaking off with a drawn
        // crossbow is a tick this would otherwise crash on.
        if (maid.getTarget() == null) {
            maid.getBrain().setMemory(
                    MemoryModuleType.ATTACK_TARGET, victim
            );
        }
        maid.performRangedAttack(victim, power);
    }

    /** Whether the weapon in her hands has finished whatever it was doing. */
    public static boolean readyToRelease(
            EntityMaid maid,
            ItemStack weapon,
            WeaponKind kind,
            RangedWeaponRecognizer external
    ) {
        int drawn = maid.getTicksUsingItem();
        if (drawn >= MAX_DRAW_TICKS) {
            return true;
        }
        if (kind == WeaponKind.EXTERNAL_RANGED) {
            return drawn >= Math.max(0, external.chargeTicks(weapon));
        }
        // Loading and firing are two separate actions on a crossbow. Treating
        // them as one long charge leaves her cranking a weapon that is already
        // loaded — which is exactly the bolt that never comes out.
        if (weapon.getItem() instanceof CrossbowItem
                && CrossbowItem.isCharged(weapon)) {
            return true;
        }
        int duration = weapon.getUseDuration();
        if (duration <= 0) {
            return true;
        }
        if (duration < HELD_INDEFINITELY_TICKS) {
            // One tick early, and that tick is the whole difference.
            //
            // Waiting for the counter to reach zero looks right and never
            // fires: on the tick it reaches zero the host has already called
            // completeUsingItem, and a crossbow loads in releaseUsing — the
            // "let go" path — not in finishUsingItem. Measured, the counter ran
            // 28 → 0 and back to 28 forever, isCharged false the whole time,
            // every shot spent on an unloaded weapon.
            //
            // Vanilla's own bolt is ready three ticks before the duration ends
            // (charge duration plus the release window), so releasing at one
            // remaining is inside the window for anything shaped like a
            // crossbow and still spends the full stated time for anything else.
            return maid.getUseItemRemainingTicks() <= 1;
        }
        return BowItem.getPowerForTime(drawn) >= 1.0F;
    }

    /**
     * How hard the shot goes off.
     *
     * <p>Only weapons that can be held indefinitely earn their power from the
     * draw. Anything with a stated duration was either ready or it was not, so
     * a fraction of it would be inventing a half-loaded crossbow.
     */
    public static float releasePower(EntityMaid maid, ItemStack weapon) {
        return weapon.getUseDuration() >= HELD_INDEFINITELY_TICKS
                ? BowItem.getPowerForTime(maid.getTicksUsingItem())
                : 1.0F;
    }
}
