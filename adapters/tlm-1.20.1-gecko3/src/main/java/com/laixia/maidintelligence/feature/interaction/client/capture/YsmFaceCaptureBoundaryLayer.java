package com.laixia.maidintelligence.feature.interaction.client.capture;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.laixia.maidintelligence.feature.shading.client.ShadowPassDetector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class YsmFaceCaptureBoundaryLayer<
        T extends Mob,
        R extends IGeoEntityRenderer<T>
> extends GeoLayerRenderer<T, R> {
    public YsmFaceCaptureBoundaryLayer(R entityRenderer) {
        super(entityRenderer);
    }

    @Override
    public GeoLayerRenderer<T, R> copy(R entityRenderer) {
        return new YsmFaceCaptureBoundaryLayer<>(entityRenderer);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (!ShadowPassDetector.isActive()
                && entity instanceof EntityMaid maid
                && maid.isYsmModel()) {
            YsmFaceTrackingCapture.beginLayerSection(maid);
        }
    }
}
