package com.laixia.maidintelligence.feature.status.domain;

public final class DefaultHungerPolicy {
    public static final DefaultHungerPolicy INSTANCE = new DefaultHungerPolicy();

    public static final int MAX_HUNGER = 100;
    public static final int AUTO_EAT_THRESHOLD = 40;
    public static final int HUNGER_UNITS_PER_POINT = 400;
    public static final int WORK_UNITS_PER_TICK = 2;
    public static final int IDLE_UNITS_PER_TICK = 1;
    public static final int FOOD_NUTRITION_MULTIPLIER = 5;

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

    public MaidStatusState drain(MaidStatusState state, int points) {
        if (points <= 0 || state.hunger() == 0) {
            return state;
        }
        return new MaidStatusState(Math.max(0, state.hunger() - points));
    }

    public MaidStatusState restoreFromNutrition(MaidStatusState state, int nutrition) {
        if (nutrition <= 0 || state.hunger() == MAX_HUNGER) {
            return state;
        }
        int restored = nutrition * FOOD_NUTRITION_MULTIPLIER;
        return new MaidStatusState(Math.min(MAX_HUNGER, state.hunger() + restored));
    }
}
