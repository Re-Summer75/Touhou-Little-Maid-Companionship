package com.laixia.maidintelligence.feature.orchestration.tlm.context;

import com.laixia.maidintelligence.feature.behavior.domain.forecast.CompanionActivity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * Names what the owner appears to be doing, from what a bystander could
 * actually tell.
 *
 * <p>Held item first, because it is the only signal that distinguishes the
 * working activities from each other and it is stable across the pauses inside
 * a task — someone mining is still mining while walking to the next vein, and
 * classifying that gap as travelling would fill the table with transitions that
 * describe nothing.
 *
 * <p>Combat overrides the held item, since being attacked is what the owner is
 * doing regardless of what they were holding when it started.
 *
 * <p>Guesses, all of them. That is acceptable here because the forecast is
 * built on counted transitions: a category that is wrong occasionally shows up
 * as a weaker prediction, not a wrong one, and the confidence blend already
 * refuses to trust thin evidence.
 */
final class OwnerActivityClassifier {
    /**
     * Ticks after being hurt during which the owner still counts as fighting.
     * Five seconds bridges the gaps between blows without leaving the owner in
     * combat long after it ended.
     */
    private static final int COMBAT_MEMORY_TICKS = 100;

    private static final double MOVING_SPEED_SQUARED = 0.0025D;

    private OwnerActivityClassifier() {
    }

    static CompanionActivity classify(LivingEntity owner, long gameTime) {
        if (owner == null) {
            return CompanionActivity.IDLE;
        }
        if (owner.isSleeping()) {
            return CompanionActivity.RESTING;
        }
        if (inCombat(owner, gameTime)) {
            return CompanionActivity.COMBAT;
        }
        CompanionActivity held = byHeldItem(
                owner.getMainHandItem().getItem()
        );
        if (held != null) {
            return held;
        }
        return moving(owner)
                ? CompanionActivity.TRAVELLING
                : CompanionActivity.IDLE;
    }

    private static boolean inCombat(LivingEntity owner, long gameTime) {
        if (owner.hurtTime > 0) {
            return true;
        }
        return owner.getLastHurtByMob() != null
                && gameTime - owner.getLastHurtByMobTimestamp()
                <= COMBAT_MEMORY_TICKS;
    }

    /**
     * @return the activity the item implies, or {@code null} when it implies
     *         nothing and movement should decide instead
     */
    private static CompanionActivity byHeldItem(Item item) {
        if (item instanceof SwordItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof TridentItem) {
            return CompanionActivity.COMBAT;
        }
        if (item instanceof PickaxeItem || item instanceof ShovelItem) {
            return CompanionActivity.MINING;
        }
        // Seeds are `ItemNameBlockItem`, so they have to be tested before the
        // `BlockItem` branch below or planting would read as building.
        if (item instanceof HoeItem || item instanceof ItemNameBlockItem) {
            return CompanionActivity.FARMING;
        }
        if (item instanceof BlockItem) {
            return CompanionActivity.BUILDING;
        }
        // An axe is a weapon and a tool; without a swing to look at, treating it
        // as building matches what it is usually carried for.
        if (item instanceof AxeItem) {
            return CompanionActivity.BUILDING;
        }
        return null;
    }

    private static boolean moving(LivingEntity owner) {
        return owner.getDeltaMovement().horizontalDistanceSqr()
                > MOVING_SPEED_SQUARED;
    }
}
