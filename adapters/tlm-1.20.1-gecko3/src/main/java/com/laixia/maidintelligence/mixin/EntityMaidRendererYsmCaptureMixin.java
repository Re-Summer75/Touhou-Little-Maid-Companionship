package com.laixia.maidintelligence.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.laixia.maidintelligence.feature.interaction.client.YsmFaceTrackingCapture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EntityMaidRenderer.class, remap = false)
public abstract class EntityMaidRendererYsmCaptureMixin {
    @Redirect(
            // EntityMaidRenderer is external, so Mixin cannot generate this mapping.
            method = {"render", "m_7392_"},
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/geckolib3/geo/IGeoEntityRenderer;geoRender(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    remap = false
            ),
            remap = false
    )
    private void maidIntelligence$captureYsmVertices(
            IGeoEntityRenderer<Entity> renderer,
            Entity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight
    ) {
        YsmFaceTrackingCapture.render(
                renderer,
                entity,
                entityYaw,
                partialTick,
                poseStack,
                buffers,
                packedLight
        );
    }
}
