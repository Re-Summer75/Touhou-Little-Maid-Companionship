package com.laixia.maidintelligence.feature.behavior.domain.owner;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

/**
 * One reading of everything a maid can notice about her owner.
 *
 * <p>Plain numbers with no world types, so what an owner fact <em>means</em>
 * can be settled and tested here rather than inside a renderer's tick. The
 * adapter's only job is filling this in.
 *
 * <p>{@link #absent()} answers {@code NaN} to everything rather than zero.
 * Zero is a reading — "no armour", "standing still" — and a maid with no owner
 * has taken no reading at all. A condition on a missing fact should fail, not
 * quietly succeed because the default happened to satisfy it.
 */
public record OwnerFacts(
        double holdingFood,
        double heldFoodQuality,
        double holdingWeapon,
        double holdingTool,
        double holdingBlock,
        double handsEmpty,
        double armorFraction,
        double usingItem,
        double healthFraction,
        double foodFraction,
        double airFraction,
        double onFire,
        double inWater,
        double hurtRecently,
        double sneaking,
        double sprinting,
        double riding,
        double sleeping,
        double flying,
        double speed,
        double onTheMove,
        double harmfulEffectCount,
        double beneficialEffectCount,
        double inventoryFood,
        double inventoryFullness,
        double experienceLevel
) {
    private static final OwnerFacts ABSENT = new OwnerFacts(
            Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
            Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
            Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
            Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
            Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
            Double.NaN
    );

    /** No owner in reach, so nothing has been observed about one. */
    public static OwnerFacts absent() {
        return ABSENT;
    }

    /**
     * @return the value for an owner fact, or {@code NaN} if the id is not one
     */
    public double value(OrchestrationId fact) {
        if (fact == null) {
            return Double.NaN;
        }
        if (fact.equals(OwnerFactIds.HOLDING_FOOD)) {
            return holdingFood;
        }
        if (fact.equals(OwnerFactIds.HELD_FOOD_QUALITY)) {
            return heldFoodQuality;
        }
        if (fact.equals(OwnerFactIds.HOLDING_WEAPON)) {
            return holdingWeapon;
        }
        if (fact.equals(OwnerFactIds.HOLDING_TOOL)) {
            return holdingTool;
        }
        if (fact.equals(OwnerFactIds.HOLDING_BLOCK)) {
            return holdingBlock;
        }
        if (fact.equals(OwnerFactIds.HANDS_EMPTY)) {
            return handsEmpty;
        }
        if (fact.equals(OwnerFactIds.ARMOR_FRACTION)) {
            return armorFraction;
        }
        if (fact.equals(OwnerFactIds.USING_ITEM)) {
            return usingItem;
        }
        if (fact.equals(OwnerFactIds.HEALTH_FRACTION)) {
            return healthFraction;
        }
        if (fact.equals(OwnerFactIds.FOOD_FRACTION)) {
            return foodFraction;
        }
        if (fact.equals(OwnerFactIds.AIR_FRACTION)) {
            return airFraction;
        }
        if (fact.equals(OwnerFactIds.ON_FIRE)) {
            return onFire;
        }
        if (fact.equals(OwnerFactIds.IN_WATER)) {
            return inWater;
        }
        if (fact.equals(OwnerFactIds.HURT_RECENTLY)) {
            return hurtRecently;
        }
        return postureValue(fact);
    }

    private double postureValue(OrchestrationId fact) {
        if (fact.equals(OwnerFactIds.SNEAKING)) {
            return sneaking;
        }
        if (fact.equals(OwnerFactIds.SPRINTING)) {
            return sprinting;
        }
        if (fact.equals(OwnerFactIds.RIDING)) {
            return riding;
        }
        if (fact.equals(OwnerFactIds.SLEEPING)) {
            return sleeping;
        }
        if (fact.equals(OwnerFactIds.FLYING)) {
            return flying;
        }
        if (fact.equals(OwnerFactIds.SPEED)) {
            return speed;
        }
        if (fact.equals(OwnerFactIds.ON_THE_MOVE)) {
            return onTheMove;
        }
        if (fact.equals(OwnerFactIds.HARMFUL_EFFECT_COUNT)) {
            return harmfulEffectCount;
        }
        if (fact.equals(OwnerFactIds.BENEFICIAL_EFFECT_COUNT)) {
            return beneficialEffectCount;
        }
        if (fact.equals(OwnerFactIds.INVENTORY_FOOD)) {
            return inventoryFood;
        }
        if (fact.equals(OwnerFactIds.INVENTORY_FULLNESS)) {
            return inventoryFullness;
        }
        if (fact.equals(OwnerFactIds.EXPERIENCE_LEVEL)) {
            return experienceLevel;
        }
        return Double.NaN;
    }
}
