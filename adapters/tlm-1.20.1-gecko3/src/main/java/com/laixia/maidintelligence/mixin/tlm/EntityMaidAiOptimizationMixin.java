package com.laixia.maidintelligence.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.api.MaidAiOptimizationApi;
import com.laixia.maidintelligence.feature.ai.domain.PathReachabilityCache;
import com.laixia.maidintelligence.feature.ai.tlm.ActivityRadiusAccess;
import com.laixia.maidintelligence.feature.ai.tlm.ActivityRadiusBridge;
import com.laixia.maidintelligence.feature.ai.tlm.ActivityRadiusState;
import com.laixia.maidintelligence.feature.ai.tlm.CombatReactionBridge;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidAiOptimizationMixin
        implements ActivityRadiusAccess {
    @Unique
    private static final long maidIntelligence$blockTargetIdentity = 0L;

    @Unique
    private final PathReachabilityCache maidIntelligence$pathCache =
            new PathReachabilityCache();

    @Unique
    private final ActivityRadiusState maidIntelligence$activityRadius =
            new ActivityRadiusState();

    @Unique
    private MaidAiOptimizationApi maidIntelligence$ai;

    @Unique
    private long maidIntelligence$aiStepStartedNanos;

    @Unique
    private boolean maidIntelligence$timingAiStep;

    @Unique
    private WalkTarget maidIntelligence$previousCombatWalkTarget;

    @Unique
    private boolean maidIntelligence$coordinatingCombat;

    @Inject(
            // 混淆后这个覆写叫 m_21535_，两个名字都列上：开发环境命中前者，
            // 玩家的游戏命中后者。
            method = {"getRestrictRadius()F", "m_21535_()F"},
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void maidIntelligence$applyEffectiveActivityRadius(
            CallbackInfoReturnable<Float> callback
    ) {
        EntityMaid maid = maidIntelligence$self();
        MaidAiOptimizationApi ai = maidIntelligence$ai();
        float baseRadius = callback.getReturnValueF();
        if (!ai.enabled()) {
            maidIntelligence$activityRadius.reset();
            return;
        }

        float effectiveRadius = ActivityRadiusBridge.effectiveRadius(
                maid,
                baseRadius
        );
        if (ActivityRadiusBridge.applyRadius(
                maid,
                baseRadius,
                effectiveRadius
        )) {
            // Radius changes invalidate assumptions made by cached paths.
            maidIntelligence$pathCache.clear();
        }
        if (effectiveRadius != baseRadius) {
            callback.setReturnValue(effectiveRadius);
        }
    }

    @Inject(
            method = "canPathReach(Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void maidIntelligence$reuseReachableBlock(
            BlockPos target,
            CallbackInfoReturnable<Boolean> callback
    ) {
        EntityMaid maid = maidIntelligence$self();
        MaidAiOptimizationApi ai = maidIntelligence$ai();
        if (!ai.enabled()) {
            maidIntelligence$pathCache.clear();
            return;
        }
        if (maidIntelligence$pathCache.containsReachable(
                maid.level().getGameTime(),
                maid.blockPosition().asLong(),
                target.asLong(),
                maidIntelligence$blockTargetIdentity,
                System.identityHashCode(maid.getNavigation())
        )) {
            ai.recordPathCacheHit();
            callback.setReturnValue(true);
        }
    }

    @Inject(
            method = "canPathReach(Lnet/minecraft/core/BlockPos;)Z",
            at = @At("RETURN"),
            remap = false
    )
    private void maidIntelligence$rememberReachableBlock(
            BlockPos target,
            CallbackInfoReturnable<Boolean> callback
    ) {
        MaidAiOptimizationApi ai = maidIntelligence$ai();
        if (!ai.enabled()) {
            return;
        }
        ai.recordPathComputation();
        if (!callback.getReturnValueZ()) {
            return;
        }
        EntityMaid maid = maidIntelligence$self();
        maidIntelligence$pathCache.rememberReachable(
                maid.level().getGameTime(),
                ai.reachablePathCacheTicks(),
                maid.blockPosition().asLong(),
                target.asLong(),
                maidIntelligence$blockTargetIdentity,
                System.identityHashCode(maid.getNavigation())
        );
    }

    @Inject(
            method = "canPathReach(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void maidIntelligence$reuseReachableEntity(
            Entity target,
            CallbackInfoReturnable<Boolean> callback
    ) {
        EntityMaid maid = maidIntelligence$self();
        MaidAiOptimizationApi ai = maidIntelligence$ai();
        if (!ai.enabled()) {
            maidIntelligence$pathCache.clear();
            return;
        }
        if (target.isAlive() && maidIntelligence$pathCache.containsReachable(
                maid.level().getGameTime(),
                maid.blockPosition().asLong(),
                target.blockPosition().asLong(),
                maidIntelligence$entityIdentity(target),
                System.identityHashCode(maid.getNavigation())
        )) {
            ai.recordPathCacheHit();
            callback.setReturnValue(true);
        }
    }

    @Inject(
            method = "canPathReach(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN"),
            remap = false
    )
    private void maidIntelligence$rememberReachableEntity(
            Entity target,
            CallbackInfoReturnable<Boolean> callback
    ) {
        MaidAiOptimizationApi ai = maidIntelligence$ai();
        if (!ai.enabled()) {
            return;
        }
        ai.recordPathComputation();
        if (!callback.getReturnValueZ() || !target.isAlive()) {
            return;
        }
        EntityMaid maid = maidIntelligence$self();
        maidIntelligence$pathCache.rememberReachable(
                maid.level().getGameTime(),
                ai.reachablePathCacheTicks(),
                maid.blockPosition().asLong(),
                target.blockPosition().asLong(),
                maidIntelligence$entityIdentity(target),
                System.identityHashCode(maid.getNavigation())
        );
    }

    @Inject(
            method = {"customServerAiStep()V", "m_8024_()V"},
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void maidIntelligence$startAiStepTiming(CallbackInfo callback) {
        EntityMaid maid = maidIntelligence$self();
        maidIntelligence$coordinatingCombat =
                CombatReactionBridge.beginAiStep(maid);
        maidIntelligence$previousCombatWalkTarget =
                maidIntelligence$coordinatingCombat
                        ? CombatReactionBridge.captureMovement(maid)
                        : null;

        MaidAiOptimizationApi ai = maidIntelligence$ai();
        maidIntelligence$timingAiStep =
                ai.enabled() && ai.profilingEnabled();
        if (maidIntelligence$timingAiStep) {
            maidIntelligence$aiStepStartedNanos = System.nanoTime();
        }
    }

    @Inject(
            method = {"customServerAiStep()V", "m_8024_()V"},
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void maidIntelligence$finishAiStepTiming(CallbackInfo callback) {
        if (maidIntelligence$coordinatingCombat) {
            CombatReactionBridge.finishAiStep(
                    maidIntelligence$self(),
                    maidIntelligence$previousCombatWalkTarget
            );
        }
        maidIntelligence$coordinatingCombat = false;
        maidIntelligence$previousCombatWalkTarget = null;

        if (!maidIntelligence$timingAiStep) {
            return;
        }
        maidIntelligence$timingAiStep = false;
        maidIntelligence$ai().recordBrainTick(
                System.nanoTime() - maidIntelligence$aiStepStartedNanos
        );
    }

    @Unique
    private EntityMaid maidIntelligence$self() {
        return (EntityMaid) (Object) this;
    }

    @Unique
    private MaidAiOptimizationApi maidIntelligence$ai() {
        MaidAiOptimizationApi current = maidIntelligence$ai;
        if (current == null) {
            current = AdapterRuntime.require(MaidAiOptimizationApi.class);
            maidIntelligence$ai = current;
        }
        return current;
    }

    @Unique
    private static long maidIntelligence$entityIdentity(Entity entity) {
        return ((long) entity.getId() << 32)
                ^ (System.identityHashCode(entity) & 0xffffffffL);
    }

    @Override
    public ActivityRadiusState maidIntelligence$activityRadiusState() {
        return maidIntelligence$activityRadius;
    }
}
