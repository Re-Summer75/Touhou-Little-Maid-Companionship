package com.laixia.maidintelligence.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.context.AnimationContext;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.MaidBonePhysics;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wraps Touhou Little Maid's Gecko pose with a reversible secondary-motion
 * overlay. The previous overlay is removed before animations run and a fresh
 * one is applied after both controller and hardcoded animation updates.
 */
@Mixin(value = GeckoMaidEntity.class, remap = false)
public abstract class GeckoMaidEntityBonePhysicsMixin {
    @Inject(
            method = "setCustomAnimations",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void maidIntelligence$restoreAnimationPose(
            AnimationContext<?> context,
            AnimationEvent<?> event,
            CallbackInfoReturnable<Boolean> callback
    ) {
        AnimatableEntity<?> self = (AnimatableEntity<?>) (Object) this;
        AnimatedGeoModel model = self.getCurrentModel();
        if (model != null && self.getEntity() instanceof LivingEntity maid) {
            MaidBonePhysics.restoreAnimationPose(maid, model);
        }
    }

    // RETURN, not TAIL: setCustomAnimations returns from two branches (the
    // normal render path carries EntityModelData and returns early), and TAIL
    // would only catch the last one, so physics never ran on rendered maids.
    @Inject(
            method = "setCustomAnimations",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void maidIntelligence$applyBonePhysics(
            AnimationContext<?> context,
            AnimationEvent<?> event,
            CallbackInfoReturnable<Boolean> callback
    ) {
        /*
         * Do not gate this on the return value. Gecko returns false when its
         * controller rate limiter skips a tick, but GeckoMaidEntity still runs
         * hardcoded animations afterwards. In particular, tail/default writes
         * the tail root on every render call. The HEAD restore makes applying
         * physics on these skipped controller frames safe and non-cumulative.
         */
        // getCurrentModel/getEntity are public on the AnimatableEntity
        // supertype; cast through Object so no @Shadow binding is needed for
        // inherited members. A live GeckoMaidEntity is always an
        // AnimatableEntity at runtime.
        AnimatableEntity<?> self = (AnimatableEntity<?>) (Object) this;
        AnimatedGeoModel model = self.getCurrentModel();
        if (model == null) {
            return;
        }
        // A real Gecko model means this is not the YSM path; only living maids
        // carry the body-rotation history the springs read for motion.
        if (self.getEntity() instanceof LivingEntity maid) {
            // Keep the integer age exact so long-lived entities do not lose
            // render-frame partial ticks to float mantissa quantization.
            double animationTick =
                    (double) maid.tickCount + event.getPartialTick();
            MaidBonePhysics.apply(maid, model, animationTick);
        }
    }
}
