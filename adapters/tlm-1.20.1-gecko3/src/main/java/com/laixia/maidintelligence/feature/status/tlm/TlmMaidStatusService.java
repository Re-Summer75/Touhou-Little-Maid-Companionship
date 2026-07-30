package com.laixia.maidintelligence.feature.status.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.feature.status.application.MaidStatusApplication;
import com.laixia.maidintelligence.feature.status.domain.DefaultHungerPolicy;
import com.laixia.maidintelligence.feature.status.domain.MaidStatusState;
import com.laixia.maidintelligence.feature.status.port.MaidStatusStore;
import com.laixia.maidintelligence.feature.status.service.MaidActionService;
import com.laixia.maidintelligence.feature.status.service.MaidExpressionService;
import com.laixia.maidintelligence.feature.status.service.ToolReplacementResult;
import com.laixia.maidintelligence.feature.status.service.ToolReplacementService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * TLM orchestration delegates reusable state transitions to the pure application slice.
 */
public final class TlmMaidStatusService implements MaidStatusApi<EntityMaid> {
    private final MaidStatusStore<EntityMaid> store;
    private final MaidStatusApplication<EntityMaid> application;
    private final DefaultHungerPolicy hungerPolicy;
    private final ToolReplacementService toolReplacementService;
    private final MaidMealAccess mealAccess;
    private final MaidExpressionService expressionService;
    private final MaidActionService actionService;
    private final Map<EntityMaid, RuntimeState> runtimeStates = new WeakHashMap<>();

    public TlmMaidStatusService(
            MaidStatusStore<EntityMaid> store,
            DefaultHungerPolicy hungerPolicy,
            ToolReplacementService toolReplacementService,
            MaidMealAccess mealAccess,
            MaidExpressionService expressionService,
            MaidActionService actionService
    ) {
        this.store = store;
        this.application = new MaidStatusApplication<>(store, hungerPolicy);
        this.hungerPolicy = hungerPolicy;
        this.toolReplacementService = toolReplacementService;
        this.mealAccess = mealAccess;
        this.expressionService = expressionService;
        this.actionService = actionService;
    }

    @Override
    public MaidStatusState getState(EntityMaid maid) {
        return application.getState(maid);
    }

    @Override
    public boolean isSaturationFull(EntityMaid maid) {
        return application.isSaturationFull(maid);
    }

    @Override
    public void setHunger(EntityMaid maid, int hunger) {
        int clamped = Math.max(0, Math.min(DefaultHungerPolicy.MAX_HUNGER, hunger));
        application.setHunger(maid, clamped);
        if (clamped > DefaultHungerPolicy.AUTO_EAT_THRESHOLD) {
            expressionService.clearHungerWarning(maid);
        }
    }

    public void tick(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel)) {
            return;
        }

        actionService.tick(maid);
        RuntimeState runtime = runtimeStates.computeIfAbsent(
                maid,
                ignored -> new RuntimeState()
        );
        int elapsedTicks = advanceRuntimeClock(maid, runtime);
        if (elapsedTicks > 0) {
            updateHunger(maid, runtime, elapsedTicks);
        }
        updateHungerFeedback(maid);
        updateToolFeedback(maid);
    }

    public void tickHungerRegeneration(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel)
                || maid.isDeadOrDying()) {
            return;
        }

        RuntimeState runtime = runtimeStates.computeIfAbsent(
                maid,
                ignored -> new RuntimeState()
        );
        MaidStatusState current = store.get(maid);
        MaidStatusState updated = hungerPolicy.settleExhaustion(current);

        if (maid.getHealth() >= maid.getMaxHealth()) {
            runtime.hungerRegenerationTicks = 0;
            runtime.saturatedRegenerationTicks = 0;
            if (!updated.equals(current)) {
                store.set(maid, updated);
            }
            return;
        }

        updated = updateHungerRegeneration(maid, runtime, updated);
        if (maid.getHealth() < maid.getMaxHealth()) {
            updated = updateSaturatedRegeneration(maid, runtime, updated);
        } else {
            runtime.saturatedRegenerationTicks = 0;
        }
        if (!updated.equals(current)) {
            store.set(maid, updated);
        }
    }

    private MaidStatusState updateHungerRegeneration(
            EntityMaid maid,
            RuntimeState runtime,
            MaidStatusState state
    ) {
        int interval = hungerPolicy.regenerationIntervalTicks(state);
        if (interval <= 0) {
            runtime.hungerRegenerationTicks = 0;
            return state;
        }

        runtime.hungerRegenerationTicks++;
        if (runtime.hungerRegenerationTicks < interval) {
            return state;
        }
        runtime.hungerRegenerationTicks = 0;

        float previousHealth = maid.getHealth();
        maid.heal(DefaultHungerPolicy.REGENERATION_HEALTH);
        if (maid.getHealth() > previousHealth) {
            return hungerPolicy.consumeForRegeneration(state);
        }
        return state;
    }

    private MaidStatusState updateSaturatedRegeneration(
            EntityMaid maid,
            RuntimeState runtime,
            MaidStatusState state
    ) {
        int interval = hungerPolicy.saturatedRegenerationIntervalTicks(state);
        if (interval <= 0) {
            runtime.saturatedRegenerationTicks = 0;
            return state;
        }

        runtime.saturatedRegenerationTicks++;
        if (runtime.saturatedRegenerationTicks < interval) {
            return state;
        }
        runtime.saturatedRegenerationTicks = 0;

        float previousHealth = maid.getHealth();
        maid.heal(hungerPolicy.saturatedRegenerationHealth(state));
        if (maid.getHealth() > previousHealth) {
            return hungerPolicy.consumeForSaturatedRegeneration(state);
        }
        return state;
    }

    public void captureFoodNutrition(EntityMaid maid, ItemStack food) {
        if (!(maid.level() instanceof ServerLevel)) {
            return;
        }
        var properties = food.getFoodProperties(maid);
        if (properties != null) {
            RuntimeState runtime = runtimeStates.computeIfAbsent(
                    maid,
                    ignored -> new RuntimeState()
            );
            runtime.pendingNutrition = properties.getNutrition();
            runtime.pendingSaturationModifier = properties.getSaturationModifier();
        }
    }

    @Override
    public void restoreFromFood(
            EntityMaid maid,
            int nutrition,
            float saturationModifier
    ) {
        if (!(maid.level() instanceof ServerLevel) || nutrition <= 0) {
            return;
        }

        application.restoreFromFood(
                maid,
                nutrition,
                saturationModifier
        );
        expressionService.clearHungerWarning(maid);
    }

    public void onFoodUseStopped(EntityMaid maid) {
        RuntimeState runtime = runtimeStates.get(maid);
        if (runtime != null) {
            runtime.pendingNutrition = 0;
            runtime.pendingSaturationModifier = 0.0F;
        }
    }

    public void onFoodEaten(EntityMaid maid, ItemStack foodAfterEat) {
        if (!(maid.level() instanceof ServerLevel)) {
            return;
        }

        RuntimeState runtime = runtimeStates.computeIfAbsent(
                maid,
                ignored -> new RuntimeState()
        );
        int nutrition = runtime.pendingNutrition;
        float saturationModifier = runtime.pendingSaturationModifier;
        runtime.pendingNutrition = 0;
        runtime.pendingSaturationModifier = 0.0F;
        if (nutrition <= 0 && !foodAfterEat.isEmpty()) {
            var properties = foodAfterEat.getFoodProperties(maid);
            if (properties != null) {
                nutrition = properties.getNutrition();
                saturationModifier = properties.getSaturationModifier();
            }
        }
        if (nutrition <= 0) {
            return;
        }

        restoreFromFood(maid, nutrition, saturationModifier);
    }

    private int advanceRuntimeClock(EntityMaid maid, RuntimeState runtime) {
        int currentTick = maid.tickCount;
        if (runtime.lastMaidTick < 0 || currentTick < runtime.lastMaidTick) {
            runtime.lastMaidTick = currentTick;
            return 0;
        }

        int elapsedTicks = currentTick - runtime.lastMaidTick;
        runtime.lastMaidTick = currentTick;
        return elapsedTicks;
    }

    private void updateHunger(
            EntityMaid maid,
            RuntimeState runtime,
            int elapsedTicks
    ) {
        if (elapsedTicks <= 0) {
            return;
        }

        Activity activity = maid.getScheduleDetail();
        boolean working = activity == Activity.WORK;
        boolean restingOrSleeping = activity == Activity.REST || maid.isSleeping();
        runtime.hungerUnits += elapsedTicks
                * hungerPolicy.activityUnitsPerTick(working, restingOrSleeping);

        int hungerLoss =
                runtime.hungerUnits / DefaultHungerPolicy.HUNGER_UNITS_PER_POINT;
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
        private int hungerRegenerationTicks;
        private int saturatedRegenerationTicks;
        private int pendingNutrition;
        private float pendingSaturationModifier;
    }
}
