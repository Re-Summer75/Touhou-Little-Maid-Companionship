package com.laixia.maidintelligence.feature.status.domain;

public final class DefaultHungerPolicy {
    public static final DefaultHungerPolicy INSTANCE = new DefaultHungerPolicy();

    public static final int MAX_HUNGER = 100;
    public static final int AUTO_EAT_THRESHOLD = 40;
    public static final int HUNGER_UNITS_PER_POINT = 400;
    public static final int WORK_UNITS_PER_TICK = 2;
    public static final int IDLE_UNITS_PER_TICK = 1;
    public static final int FOOD_NUTRITION_MULTIPLIER = 5;
    public static final float INITIAL_SATURATION = 25.0F;
    public static final float MAX_EXHAUSTION = 40.0F;
    public static final float EXHAUSTION_THRESHOLD = 4.0F;
    public static final float SATURATION_SCALE = 5.0F;
    public static final float MAX_SATURATED_REGENERATION_EXHAUSTION = 6.0F;
    public static final int REGENERATION_HIGH_HUNGER = 80;
    public static final int REGENERATION_MEDIUM_HUNGER = 60;
    public static final int REGENERATION_MIN_HUNGER = AUTO_EAT_THRESHOLD + 1;
    public static final int SATURATED_REGENERATION_INTERVAL_TICKS = 10;
    public static final int REGENERATION_HIGH_INTERVAL_TICKS = 10;
    public static final int REGENERATION_MEDIUM_INTERVAL_TICKS = 80;
    public static final int REGENERATION_LOW_INTERVAL_TICKS = 160;
    public static final int REGENERATION_HUNGER_COST = 1;
    public static final float REGENERATION_HEALTH = 1.0F;

    private DefaultHungerPolicy() {
    }

    public int activityUnitsPerTick(boolean working, boolean restingOrSleeping) {
        if (restingOrSleeping) {
            return 0;
        }
        return working ? WORK_UNITS_PER_TICK : IDLE_UNITS_PER_TICK;
    }

    public boolean shouldAutoEat(MaidStatusState state) {
        return state.hunger() <= AUTO_EAT_THRESHOLD;
    }

    public boolean isSaturationFull(MaidStatusState state) {
        return state.saturation() >= MAX_HUNGER - 1.0E-4F;
    }

    public MaidStatusState drain(MaidStatusState state, int points) {
        if (points <= 0 || state.hunger() == 0) {
            return state;
        }

        int saturationProtectedPoints = Math.min(
                points,
                (int) Math.ceil(state.saturation())
        );
        float saturation = Math.max(
                0.0F,
                state.saturation() - saturationProtectedPoints
        );
        int hunger = Math.max(
                0,
                state.hunger() - (points - saturationProtectedPoints)
        );
        return new MaidStatusState(
                hunger,
                Math.min(saturation, hunger),
                state.exhaustion()
        );
    }

    public MaidStatusState restoreFromNutrition(MaidStatusState state, int nutrition) {
        return restoreFromFood(state, nutrition, 0.0F);
    }

    public MaidStatusState restoreFromFood(
            MaidStatusState state,
            int nutrition,
            float saturationModifier
    ) {
        if (nutrition <= 0) {
            return state;
        }

        int hunger = Math.min(
                MAX_HUNGER,
                state.hunger() + nutrition * FOOD_NUTRITION_MULTIPLIER
        );
        float restoredSaturation = nutrition
                * Math.max(0.0F, saturationModifier)
                * 2.0F
                * SATURATION_SCALE;
        float saturation = Math.min(
                hunger,
                state.saturation() + restoredSaturation
        );
        return new MaidStatusState(hunger, saturation, state.exhaustion());
    }

    public int regenerationIntervalTicks(MaidStatusState state) {
        if (state.hunger() >= REGENERATION_HIGH_HUNGER) {
            return REGENERATION_HIGH_INTERVAL_TICKS;
        }
        if (state.hunger() >= REGENERATION_MEDIUM_HUNGER) {
            return REGENERATION_MEDIUM_INTERVAL_TICKS;
        }
        if (state.hunger() >= REGENERATION_MIN_HUNGER) {
            return REGENERATION_LOW_INTERVAL_TICKS;
        }
        return 0;
    }

    public int saturatedRegenerationIntervalTicks(MaidStatusState state) {
        return hasSaturatedRegeneration(state)
                ? SATURATED_REGENERATION_INTERVAL_TICKS
                : 0;
    }

    public boolean hasSaturatedRegeneration(MaidStatusState state) {
        return state.hunger() >= REGENERATION_MIN_HUNGER
                && state.saturation() > 0.0F;
    }

    public float saturatedRegenerationHealth(MaidStatusState state) {
        return saturatedRegenerationExhaustion(state)
                / MAX_SATURATED_REGENERATION_EXHAUSTION;
    }

    public MaidStatusState consumeForSaturatedRegeneration(MaidStatusState state) {
        float exhaustion = Math.min(
                MAX_EXHAUSTION,
                state.exhaustion() + saturatedRegenerationExhaustion(state)
        );
        return new MaidStatusState(state.hunger(), state.saturation(), exhaustion);
    }

    public MaidStatusState settleExhaustion(MaidStatusState state) {
        if (state.exhaustion() <= EXHAUSTION_THRESHOLD) {
            return state;
        }

        float exhaustion = state.exhaustion() - EXHAUSTION_THRESHOLD;
        float saturation = state.saturation();
        int hunger = state.hunger();
        if (saturation > 0.0F) {
            saturation = Math.max(0.0F, saturation - SATURATION_SCALE);
        } else {
            hunger = Math.max(0, hunger - (int) SATURATION_SCALE);
        }
        return new MaidStatusState(
                hunger,
                Math.min(saturation, hunger),
                exhaustion
        );
    }

    public MaidStatusState consumeForRegeneration(MaidStatusState state) {
        int hunger = Math.max(0, state.hunger() - REGENERATION_HUNGER_COST);
        return new MaidStatusState(
                hunger,
                Math.min(state.saturation(), hunger),
                state.exhaustion()
        );
    }

    private float saturatedRegenerationExhaustion(MaidStatusState state) {
        return Math.min(
                state.saturation() / SATURATION_SCALE,
                MAX_SATURATED_REGENERATION_EXHAUSTION
        );
    }
}
