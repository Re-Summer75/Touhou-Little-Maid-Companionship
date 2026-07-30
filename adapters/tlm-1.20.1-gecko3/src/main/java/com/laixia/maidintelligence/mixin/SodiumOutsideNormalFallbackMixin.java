package com.laixia.maidintelligence.mixin;

import com.github.tartaricacid.touhoulittlemaid.compat.sodium.SodiumCompat;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.shading.client.OutsideNormalWriter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 仅让存在内向绕序的骨骼退出 Sodium 批量路径。
 */
@Mixin(value = SodiumCompat.class, remap = false)
public abstract class SodiumOutsideNormalFallbackMixin {
    @Inject(
            method = "sodiumRenderCubesOfBone",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void maidIntelligence$useOutsideNormalWriter(
            AnimatedGeoBone bone,
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (SodiumCompat.isSodiumInstalled()
                && OutsideNormalWriter.requiresCustomWriter(bone)) {
            callback.setReturnValue(false);
        }
    }
}
