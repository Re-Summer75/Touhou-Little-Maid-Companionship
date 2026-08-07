package com.laixia.maidintelligence.feature.orchestration.tlm.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;

/**
 * Where a gun from another mod gets recognised.
 *
 * <p>Vanilla ranged weapons are identified by item class, which does not
 * generalise: a modded firearm may keep its rounds in NBT, in a separate
 * magazine item, or in a loaded state that only its own API can read. Rather
 * than guess, this asks the question and lets whoever added the weapon answer.
 *
 * <p>Implementations must be cheap — this is consulted while she is deciding
 * what to fight with, not once at load.
 */
public interface RangedWeaponRecognizer {
    /** Recognises nothing; the built-in vanilla handling still applies. */
    RangedWeaponRecognizer NONE = new RangedWeaponRecognizer() {
        @Override
        public boolean isRangedWeapon(ItemStack stack) {
            return false;
        }

        @Override
        public boolean hasAmmunition(EntityMaid maid, ItemStack weapon) {
            return false;
        }

        @Override
        public double power(ItemStack weapon) {
            return 0.0D;
        }

        @Override
        public int chargeTicks(ItemStack weapon) {
            return 0;
        }
    };

    /** Whether this stack is a ranged weapon this recogniser owns. */
    boolean isRangedWeapon(ItemStack stack);

    /**
     * Whether the weapon can fire right now.
     *
     * <p>Answering optimistically is the expensive mistake: she will walk into
     * the open, aim, and never shoot.
     */
    boolean hasAmmunition(EntityMaid maid, ItemStack weapon);

    /** Normalised effectiveness in {@code [0,1]}, comparable to a diamond sword. */
    double power(ItemStack weapon);

    /**
     * Ticks the weapon must be held before it will fire.
     *
     * <p>Zero for anything that fires the moment the trigger is pulled, which
     * is most guns. A weapon that winds up, spins up or charges reports that
     * here instead of having this mod guess from a bow's timing.
     */
    int chargeTicks(ItemStack weapon);
}
