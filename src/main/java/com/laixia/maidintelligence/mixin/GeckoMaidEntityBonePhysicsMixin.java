package com.laixia.maidintelligence.mixin;

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
 * Applies the weighted secondary-motion layer right after Touhou Little Maid
 * finishes posing a Gecko maid for the frame. {@code setCustomAnimations}
 * already runs every animation controller, the head tracker, and the hardcoded
 * animations before returning, so injecting at its tail gives the physics the
 * final animated pose to add onto, and the bones are still read afterwards by
 * {@code renderRecursively}.
 */
@Mixin(value = GeckoMaidEntity.class, remap = false)
public abstract class GeckoMaidEntityBonePhysicsMixin {
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
        // Only run when the animation actually re-posed the bones this frame.
        // setCustomAnimations is frame-rate limited and returns false early on
        // skipped frames without resetting the bones; since @At("RETURN")
        // catches those early returns too, applying physics there would stack
        // onto the already-deflected pose and wind the chain up without bound.
        if (!Boolean.TRUE.equals(callback.getReturnValue())) {
            return;
        }
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
            MaidBonePhysics.apply(maid, model);
        }
    }
}
