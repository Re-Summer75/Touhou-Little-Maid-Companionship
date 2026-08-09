package com.laixia.maidintelligence.feature.orchestration.tlm.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponKind;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.List;
import java.util.Objects;

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
                hasAmmunition(maid, stack, kind)
        ));
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

    private double meleeDamage(ItemStack stack) {
        double damage = 0.0D;
        for (AttributeModifier modifier
                : stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE)) {
            damage += modifier.getAmount();
        }
        return damage;
    }
}
