package com.laixia.maidintelligence.feature.interaction.client;

import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.shading.client.ShadowPassDetector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MaidFaceTrackingLayer extends RenderLayer<Mob, BedrockModel<Mob>> {
    public MaidFaceTrackingLayer(EntityMaidRenderer renderer) {
        super(renderer);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            Mob entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        // 屏幕空间交互只允许主相机 pass 更新，阴影 pass 不得覆盖追踪平面。
        if (ShadowPassDetector.isActive()
                || !(entity instanceof EntityMaid maid)
                || !maid.isAddedToWorld()
                || !FaceTrackingDemand.shouldTrack(maid)) {
            return;
        }

        BedrockModel<Mob> model = getParentModel();
        String modelId = maid.getModelId();
        BedrockFaceGeometryAdapter.Result result =
                BedrockFaceGeometryAdapter.resolve(
                        model,
                        poseStack,
                        modelId
                );
        result.plane().ifPresentOrElse(
                plane -> {
                    DynamicMaidFaceTracker.update(maid, plane);
                    FaceVertexMarkerRenderer.render(
                            buffer,
                            plane,
                            FaceGeometry.Source.BEDROCK,
                            result.confidence()
                    );
                    FaceTrackingGeometryCache.reportSuccessOnce(
                            model,
                            modelId,
                            FaceGeometry.Source.BEDROCK,
                            result.key(),
                            result.confidence()
                    );
                },
                () -> {
                    DynamicMaidFaceTracker.invalidate(maid);
                    FaceTrackingGeometryCache.reportFailureOnce(
                            model,
                            modelId,
                            FaceGeometry.Source.BEDROCK,
                            result.failureReason()
                    );
                }
        );
    }

}
