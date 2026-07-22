package com.laixia.maidintelligence.feature.status.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.feature.status.domain.DefaultHungerPolicy;
import com.laixia.maidintelligence.feature.status.domain.MaidStatusState;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

public final class DefaultMaidStatusService implements MaidStatusApi {
    private final MaidStatusStore store;
    private final DefaultHungerPolicy hungerPolicy;
    private final ToolReplacementService toolReplacementService;
    private final MaidMealAccess mealAccess;
    private final MaidExpressionService expressionService;
    private final MaidActionService actionService;
    private final Map<EntityMaid, RuntimeState> runtimeStates = new WeakHashMap<>();

    public DefaultMaidStatusService(
            MaidStatusStore store,
            DefaultHungerPolicy hungerPolicy,
            ToolReplacementService toolReplacementService,
            MaidMealAccess mealAccess,
            MaidExpressionService expressionService,
            MaidActionService actionService
    ) {
        this.store = store;
        this.hungerPolicy = hungerPolicy;
        this.toolReplacementService = toolReplacementService;
        this.mealAccess = mealAccess;
        this.expressionService = expressionService;
        this.actionService = actionService;
    }

    @Override
    public MaidStatusState getState(EntityMaid maid) {
        return store.get(maid);
    }

    @Override
    public void setHunger(EntityMaid maid, int hunger) {
        int clamped = Math.max(0, Math.min(DefaultHungerPolicy.MAX_HUNGER, hunger));
        store.set(maid, new MaidStatusState(clamped));
        if (clamped > DefaultHungerPolicy.AUTO_EAT_THRESHOLD) {
            expressionService.clearHungerWarning(maid);
        }
    }

    public void tick(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel)) {
            return;
        }

        actionService.tick(maid);
        RuntimeState runtime = runtimeStates.computeIfAbsent(maid, ignored -> new RuntimeState());
        updateHunger(maid, runtime);
        updateHungerFeedback(maid);
        updateToolFeedback(maid);
    }

    @Override
    public void captureFoodNutrition(EntityMaid maid, ItemStack food) {
        if (!(maid.level() instanceof ServerLevel)) {
            return;
        }
        var properties = food.getFoodProperties(maid);
        if (properties != null) {
            runtimeStates.computeIfAbsent(maid, ignored -> new RuntimeState())
                    .pendingNutrition = properties.getNutrition();
        }
    }

    public void onFoodUseStopped(EntityMaid maid) {
        RuntimeState runtime = runtimeStates.get(maid);
        if (runtime != null) {
            runtime.pendingNutrition = 0;
        }
    }

    public void onFoodEaten(EntityMaid maid, ItemStack foodAfterEat) {
        if (!(maid.level() instanceof ServerLevel)) {
            return;
        }

        RuntimeState runtime = runtimeStates.computeIfAbsent(maid, ignored -> new RuntimeState());
        int nutrition = runtime.pendingNutrition;
        runtime.pendingNutrition = 0;
        if (nutrition <= 0 && !foodAfterEat.isEmpty()) {
            var properties = foodAfterEat.getFoodProperties(maid);
            if (properties != null) {
                nutrition = properties.getNutrition();
            }
        }
        if (nutrition <= 0) {
            return;
        }

        MaidStatusState current = store.get(maid);
        MaidStatusState restored = hungerPolicy.restoreFromNutrition(current, nutrition);
        if (!restored.equals(current)) {
            store.set(maid, restored);
        }
        expressionService.clearHungerWarning(maid);
    }

    private void updateHunger(EntityMaid maid, RuntimeState runtime) {
        int currentTick = maid.tickCount;
        if (runtime.lastMaidTick < 0 || currentTick < runtime.lastMaidTick) {
            runtime.lastMaidTick = currentTick;
            return;
        }

        int elapsedTicks = currentTick - runtime.lastMaidTick;
        runtime.lastMaidTick = currentTick;
        if (elapsedTicks <= 0) {
            return;
        }

        Activity activity = maid.getScheduleDetail();
        boolean working = activity == Activity.WORK;
        boolean restingOrSleeping = activity == Activity.REST || maid.isSleeping();
        runtime.hungerUnits += elapsedTicks
                * hungerPolicy.activityUnitsPerTick(working, restingOrSleeping);

        int hungerLoss = runtime.hungerUnits / DefaultHungerPolicy.HUNGER_UNITS_PER_POINT;
        if (hungerLoss <= 0) {
            return;
        }
        runtime.hungerUnits %= DefaultHungerPolicy.HUNGER_UNITS_PER_POINT;

        MaidStatusState current = store.get(maid);
        MaidStatusState drained = hungerPolicy.drain(current, hungerLoss);
        if (!drained.equals(current)) {
            store.set(maid, drained);
        }
    }

    private void updateHungerFeedback(EntityMaid maid) {
        MaidStatusState state = store.get(maid);
        if (!hungerPolicy.shouldAutoEat(state)) {
            expressionService.clearHungerWarning(maid);
            return;
        }
        if (maid.isSleeping()
                || maid.isUsingItem()
                || maid.isBegging()
                || !maid.getTask().enableEating(maid)
                || !maid.getHideInv().getStackInSlot(0).isEmpty()) {
            return;
        }

        if (mealAccess.tryStartHungerMeal(maid)) {
            expressionService.clearHungerWarning(maid);
            return;
        }
        if (expressionService.showHungerWarning(maid)) {
            actionService.requestAttention(maid, MaidActionService.HUNGER_PRIORITY);
        }
    }

    private void updateToolFeedback(EntityMaid maid) {
        if (maid.isUsingItem()) {
            return;
        }

        ToolReplacementResult result = toolReplacementService.inspectAndReplace(maid);
        if (!result.lowDurability()) {
            expressionService.clearToolWarning(maid);
            return;
        }
        if (result.replaced()) {
            expressionService.showToolReplaced(maid);
            maid.swing(result.hand());
            return;
        }
        if (expressionService.showToolWarning(maid, result)) {
            actionService.requestAttention(maid, MaidActionService.TOOL_PRIORITY);
        }
    }

    private static final class RuntimeState {
        private int lastMaidTick = -1;
        private int hungerUnits;
        private int pendingNutrition;
    }
}
