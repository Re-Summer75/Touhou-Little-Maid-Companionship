package com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponKind;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.SweepingEdgeEnchantment;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Reads everything she is carrying and reports what she could fight with.
 *
 * <p>Scanning the pack rather than the hand is the whole point: the vanilla
 * task asks only {@code isWeapon(getMainHandItem())}, which is why a maid with
 * a bow, arrows and a sword in her pack stands there empty-handed.
 *
 * <p>Ammunition is resolved here, not by the caller. "Has a bow" and "can
 * shoot" look identical in an inventory listing, and only one of them is a
 * reason to keep her distance.
 */
public final class TlmWeaponScanner {
    // Fixed ratings for vanilla ranged weapons, placed against melee by the
    // damage a competent shot actually lands rather than by the tooltip.
    private static final double BOW_POWER = 0.65D;
    private static final double CROSSBOW_POWER = 0.72D;
    private static final double TRIDENT_POWER = 0.70D;
    private static final double SNOWBALL_POWER = 0.05D;

    /**
     * Fractional gain per level of the damage enchantment on a launcher.
     *
     * <p>Matches vanilla's arrow scaling closely enough for a comparison
     * between two weapons she is carrying, which is all this rating is for.
     */
    private static final double POWER_PER_LEVEL = 0.25D;

    /** Impaling only applies in water, so it is discounted against Power. */
    private static final double IMPALING_PER_LEVEL = 0.10D;

    private final RangedWeaponRecognizer externalRanged;

    public TlmWeaponScanner(RangedWeaponRecognizer externalRanged) {
        this.externalRanged =
                Objects.requireNonNull(externalRanged, "externalRanged");
    }

    /** Everything usable she is carrying, main hand first. */
    public List<WeaponCandidate> scan(EntityMaid maid) {
        List<WeaponCandidate> candidates = new ArrayList<>();
        addCandidate(
                candidates,
                maid,
                maid.getMainHandItem(),
                WeaponCandidate.MAIN_HAND
        );
        IItemHandler backpack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < backpack.getSlots(); slot++) {
            addCandidate(candidates, maid, backpack.getStackInSlot(slot), slot);
        }
        return candidates;
    }

    /** The recogniser third-party ranged weapons are answered by. */
    public RangedWeaponRecognizer externalRecognizer() {
        return externalRanged;
    }

    /** Whether this stack is something she would fight with at all. */
    public boolean isWeapon(ItemStack stack) {
        return classify(stack) != null;
    }

    /**
     * How this stack would be used, or {@code null} if it is not a weapon.
     *
     * <p>Exposed so the swap can ask for the same kind the decision chose:
     * "a melee weapon" is the predicate, not "this exact item", which keeps
     * the swap working when the pack has been reshuffled since the scan.
     */
    public WeaponKind classifyFor(ItemStack stack) {
        return classify(stack);
    }

    private void addCandidate(
            List<WeaponCandidate> candidates,
            EntityMaid maid,
            ItemStack stack,
            int slot
    ) {
        WeaponKind kind = classify(stack);
        if (kind == null) {
            return;
        }
        candidates.add(new WeaponCandidate(
                kind,
                slot,
                power(stack, kind),
                hasAmmunition(maid, stack, kind),
                reach(maid, stack, kind),
                arcDamage(stack, kind),
                usesPerSecond(maid, stack, kind),
                durability(stack),
                shots(maid, stack, kind)
        ));
    }

    /**
     * What one swing of this lands on each <em>other</em> body beside the one
     * she aimed at.
     *
     * <p>The sword's arc, priced the same way the swing itself pays it out —
     * one point plus whatever Sweeping Edge adds. Zero for anything that does
     * not sweep, asked of the item rather than of its class so a modded blade
     * that declares the action is included and a modded club that does not is
     * not.
     *
     * <p>This is the number that decides a fight against a crowd, and it was
     * missing from the weapon pricing entirely. A sword was costed on what it
     * does to one target, so six zombies pressed together made steel look no
     * better than against one — while in fact they are the situation it is
     * best at, and a bow the situation it is worst at. Measured, she opened
     * every six-on-one by spending two hundred ticks emptying a quiver.
     */
    private double arcDamage(ItemStack stack, WeaponKind kind) {
        if (kind != WeaponKind.MELEE
                || !stack.canPerformAction(ToolActions.SWORD_SWEEP)) {
            return 0.0D;
        }
        // Asked of the candidate, not of the maid. The convenience overload
        // reads the enchantment off whatever is in her hand, which is the one
        // stack this must not be about: rating a Sweeping Edge blade in her
        // pack by the plain axe she happens to be holding gets the arc exactly
        // backwards, and the axe rated by the blade is worse still.
        int sweeping = EnchantmentHelper.getItemEnchantmentLevel(
                Enchantments.SWEEPING_EDGE, stack
        );
        return 1.0D + SweepingEdgeEnchantment.getSweepingDamageRatio(sweeping)
                * meleeDamage(stack);
    }

    /**
     * How far she could strike if she were holding <em>this</em> weapon.
     *
     * <p>Reach is an attribute — Forge's {@code ENTITY_REACH}, the same one a
     * player's is built from — so an item that grants it reaches further, and
     * a mod that ships a spear gets a spear's distance without naming it here.
     * A melee candidate reporting zero was the gap this closes: every blade in
     * her pack measured alike, so choosing between them could never account for
     * the one that keeps her out of a zombie's arms.
     *
     * <p>Computed as a difference from what she reaches right now rather than
     * assembled from parts. Her live figure already folds in her base, whatever
     * she happens to be holding, and every other modifier on her; swapping one
     * weapon for another changes exactly one term of that. Rebuilding the whole
     * sum here would mean re-deriving the pieces we do not own — and getting a
     * different answer from the game the moment anything else touches reach.
     */
    private double meleeReachWith(EntityMaid maid, ItemStack candidate) {
        double live = Math.sqrt(maid.getMeleeAttackRangeSqr(maid));
        return Math.max(
                0.0D,
                live - reachModifier(maid.getMainHandItem())
                        + reachModifier(candidate)
        );
    }

    /** What this stack alone adds to her reach, zero for most things. */
    private double reachModifier(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        double granted = 0.0D;
        for (AttributeModifier modifier
                : stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(ForgeMod.ENTITY_REACH.get())) {
            granted += modifier.getAmount();
        }
        return granted;
    }

    /**
     * How many more times this weapon can be used before it runs dry.
     *
     * <p>Counted with the weapon's own ammunition test, so a modded bow that
     * eats bolts or pellets is counted correctly without any of them being
     * named here — the same predicate that answers "can it fire at all".
     *
     * <p>Melee and anything that consumes nothing report
     * {@link WeaponCandidate#UNLIMITED}. That is the honest answer and it keeps
     * every comparison against a count well defined.
     */
    private int shots(EntityMaid maid, ItemStack stack, WeaponKind kind) {
        if (!kind.consumesAmmunition()) {
            return WeaponCandidate.UNLIMITED;
        }
        if (kind == WeaponKind.EXTERNAL_RANGED
                || !(stack.getItem() instanceof ProjectileWeaponItem projectile)
        ) {
            // Nothing measurable. Treated as plentiful rather than as empty,
            // because `hasAmmunition` has already decided whether it can fire
            // and this must not quietly re-decide that.
            return WeaponCandidate.UNLIMITED;
        }
        return count(maid, projectile.getAllSupportedProjectiles());
    }

    /** How many rounds she carries that satisfy this weapon's own test. */
    private int count(EntityMaid maid, Predicate<ItemStack> ammunition) {
        int rounds = 0;
        if (ammunition.test(maid.getOffhandItem())) {
            rounds += maid.getOffhandItem().getCount();
        }
        IItemHandler backpack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < backpack.getSlots(); slot++) {
            ItemStack stack = backpack.getStackInSlot(slot);
            if (ammunition.test(stack)) {
                rounds += stack.getCount();
            }
        }
        return rounds;
    }

    /**
     * How fast this particular weapon may be swung, per second.
     *
     * <p>Read off the item's own attack-speed modifier against her base, which
     * is how vanilla decides it: an iron sword lands 1.6 a second and an iron
     * axe 0.9, and the axe pays for that with damage. Both facts have to travel
     * together or the trade between them cannot be priced.
     *
     * <p>Before this, every melee candidate was costed at the cadence of
     * whatever she happened to be <em>holding</em> — so an axe in her pack was
     * valued as though it swung like the sword in her hand, and a sword in her
     * pack as though it swung like the axe. The heavier weapon always looked
     * better, because it kept its damage and borrowed the other one's speed.
     *
     * <p>Ranged weapons report nothing: their cadence is a draw, which is a
     * property of the drawing rather than of the bow, and the caller already
     * knows it.
     */
    private double usesPerSecond(
            EntityMaid maid,
            ItemStack stack,
            WeaponKind kind
    ) {
        if (kind != WeaponKind.MELEE) {
            return 0.0D;
        }
        // Her base, or the attribute's own default when she does not carry one.
        // Item modifiers are stated relative to that base and are negative, so
        // a missing base does not merely lose precision — it turns 4.0 - 2.4
        // into 0 - 2.4, and an iron sword prices as though it swung once every
        // ten seconds. The weapon then loses to anything at all.
        double base = maid.getAttributeBaseValue(Attributes.ATTACK_SPEED);
        if (base <= 0.0D) {
            base = Attributes.ATTACK_SPEED.getDefaultValue();
        }
        double modifier = 0.0D;
        for (AttributeModifier attribute
                : stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_SPEED)) {
            modifier += attribute.getAmount();
        }
        return Math.max(0.1D, base + modifier);
    }

    /**
     * What share of this weapon's life is left, one for anything unbreakable.
     *
     * <p>Carried so that "the best weapon" can stop meaning "the best weapon
     * for exactly one more hit". A blade about to break is about to leave her
     * empty-handed, and that is worth knowing before she commits to it rather
     * than at the moment it happens.
     */
    private double durability(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            return 1.0D;
        }
        double left = stack.getMaxDamage() - stack.getDamageValue();
        return Math.max(0.0D, left / stack.getMaxDamage());
    }

    /**
     * How far this weapon can actually hurt something.
     *
     * <p>Asked of the item, which is the only thing that knows: vanilla rates a
     * bow at fifteen blocks and a crossbow at eight, and a modded launcher
     * states its own. Holding one number for all of them cost a bow-armed maid
     * seven blocks of standoff — and a skeleton reaches fifteen, so the number
     * she was holding put her inside its range to reach her own.
     *
     * <p>Melee is answered by {@link #meleeReachWith}, not by zero. It used to
     * report zero on the grounds that a blade's stand-off is decided by her
     * attack reach rather than by the item — which is true and is exactly why
     * the answer is not zero: the item is one term of that reach, so two blades
     * in the same pack measured alike and choosing between them could never
     * account for the one that keeps her out of a zombie's arms.
     *
     * <p>Thrown weapons still report zero, since they are used at whatever
     * distance she happens to be.
     */
    private double reach(EntityMaid maid, ItemStack stack, WeaponKind kind) {
        if (kind == WeaponKind.MELEE) {
            return meleeReachWith(maid, stack);
        }
        if (!kind.isRanged()) {
            return 0.0D;
        }
        if (kind == WeaponKind.EXTERNAL_RANGED) {
            return externalRanged.range(stack);
        }
        if (stack.getItem() instanceof ProjectileWeaponItem projectile) {
            return projectile.getDefaultProjectileRange();
        }
        return 0.0D;
    }

    private WeaponKind classify(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof CrossbowItem) {
            return WeaponKind.CROSSBOW;
        }
        if (stack.getItem() instanceof BowItem) {
            return WeaponKind.BOW;
        }
        if (stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof SnowballItem) {
            return WeaponKind.THROWN;
        }
        if (externalRanged.isRangedWeapon(stack)) {
            return WeaponKind.EXTERNAL_RANGED;
        }
        // A modded bow need not extend BowItem, but anything that fires
        // something extends this and declares its own ammunition. Treating it
        // as a bow gives it a draw-and-release cycle, which is the behaviour
        // shared by every charged ranged weapon.
        if (stack.getItem() instanceof ProjectileWeaponItem) {
            return WeaponKind.BOW;
        }
        // Last, so a modded gun that also carries an attack modifier is still
        // treated as the ranged weapon it is.
        return meleeDamage(stack) > 0.0D ? WeaponKind.MELEE : null;
    }

    /**
     * How strong this stack is, on the shared {@code [0,1]} scale.
     *
     * <p>Zero for anything she would not fight with, so a caller comparing
     * candidates never has to ask whether it is a weapon first.
     */
    public double powerOf(ItemStack stack) {
        WeaponKind kind = classify(stack);
        return kind == null ? 0.0D : power(stack, kind);
    }

    private double power(ItemStack stack, WeaponKind kind) {
        double raw = switch (kind) {
            case MELEE -> meleeDamage(stack) / WeaponCandidate.POWER_SCALE;
            case BOW -> BOW_POWER * projectileBonus(stack);
            case CROSSBOW -> CROSSBOW_POWER * projectileBonus(stack);
            case THROWN -> stack.getItem() instanceof TridentItem
                    ? TRIDENT_POWER * loyaltyIsIrrelevantImpalingIsNot(stack)
                    : SNOWBALL_POWER;
            case EXTERNAL_RANGED -> externalRanged.power(stack);
        };
        return Math.max(0.0D, Math.min(1.0D, raw));
    }

    /**
     * What enchanting has done to a launcher's output.
     *
     * <p>An arrow's damage is a property of the flying arrow, not of the bow, so
     * the base rating cannot be read off the item the way melee damage can. The
     * enchantments can be, and they are the part that varies wildly: a Power V
     * bow does roughly double the damage of a plain one, and rating them
     * identically had her pick a fresh crossbow over her best bow.
     */
    private double projectileBonus(ItemStack stack) {
        int power = EnchantmentHelper.getItemEnchantmentLevel(
                Enchantments.POWER_ARROWS, stack
        );
        return 1.0D + power * POWER_PER_LEVEL;
    }

    /** Impaling is conditional, so it is worth less than its level suggests. */
    private double loyaltyIsIrrelevantImpalingIsNot(ItemStack stack) {
        int impaling = EnchantmentHelper.getItemEnchantmentLevel(
                Enchantments.IMPALING, stack
        );
        return 1.0D + impaling * IMPALING_PER_LEVEL;
    }

    /**
     * Whether this exact stack could be fought with right now.
     *
     * <p>Asked of a stack rather than of a {@link WeaponCandidate}, because the
     * two drift apart: the arsenal is scanned once a tick while her hand can
     * change under a swap, and a swap is refused mid-draw. So "the weapon the
     * decision chose" and "the weapon she is holding" are different questions,
     * and every path that actually swings or shoots has to ask the second one.
     *
     * <p>Ammunition is the whole of the difference for ranged weapons and no
     * part of it for melee, which is why one method answers both: a sword is
     * usable whenever it is a sword, and a bow only when something can leave it.
     */
    public boolean isUsable(EntityMaid maid, ItemStack stack) {
        WeaponKind kind = classify(stack);
        return kind != null && hasAmmunition(maid, stack, kind);
    }

    /** The same question, named for the ranged path that reads best that way. */
    public boolean canFire(EntityMaid maid, ItemStack weapon) {
        return isUsable(maid, weapon);
    }

    private boolean hasAmmunition(
            EntityMaid maid,
            ItemStack weapon,
            WeaponKind kind
    ) {
        if (!kind.consumesAmmunition()) {
            return true;
        }
        if (kind == WeaponKind.EXTERNAL_RANGED) {
            return externalRanged.hasAmmunition(maid, weapon);
        }
        if (weapon.getItem() instanceof ProjectileWeaponItem projectile) {
            // The weapon states what it eats. Asking it rather than looking
            // for arrows is what makes a modded bow that fires bolts, pellets
            // or nothing at all work without naming any of them here.
            return carries(maid, projectile.getAllSupportedProjectiles());
        }
        // Not a projectile weapon yet declares it consumes ammunition: no way
        // to know what, so assume it cannot fire rather than send her out with
        // an empty weapon.
        return false;
    }

    /** Whether anything she carries satisfies the weapon's own ammunition test. */
    private boolean carries(EntityMaid maid, Predicate<ItemStack> ammunition) {
        if (ammunition.test(maid.getOffhandItem())) {
            return true;
        }
        IItemHandler backpack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < backpack.getSlots(); slot++) {
            if (ammunition.test(backpack.getStackInSlot(slot))) {
                return true;
            }
        }
        return false;
    }

    /**
     * What one swing of this stack lands, before her own arm.
     *
     * <p>The attribute modifier is only half of it. Vanilla pays Sharpness in
     * {@code getDamageBonus} rather than as an attribute, so a Sharpness V blade
     * carries the same modifier as a plain one and rated identically here —
     * while {@code doHurtTarget} went on paying the bonus. She dealt the extra
     * damage and never counted it, so choosing between an enchanted sword and a
     * bare axe was decided on the half of the number that does not vary.
     *
     * <p>{@code UNDEFINED} on purpose. Smite and Bane of Arthropods pay out
     * against particular mobs, and the arsenal is scanned once for the whole
     * field rather than per target — pricing them here would credit a Smite
     * blade against zombies and spiders alike. Sharpness applies to everything
     * and is the part that can honestly be known without a target.
     */
    private double meleeDamage(ItemStack stack) {
        double damage = 0.0D;
        for (AttributeModifier modifier
                : stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE)) {
            damage += modifier.getAmount();
        }
        return damage
                + EnchantmentHelper.getDamageBonus(stack, MobType.UNDEFINED);
    }
}
