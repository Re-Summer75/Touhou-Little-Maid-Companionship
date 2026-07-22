package com.laixia.maidintelligence.feature.interaction.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class GeckoMaidFaceTrackingLayer<T extends Mob, R extends IGeoEntityRenderer<T>>
        extends GeoLayerRenderer<T, R> {
    public GeckoMaidFaceTrackingLayer(R entityRenderer) {
        super(entityRenderer);
    }

    @Override
    public GeoLayerRenderer<T, R> copy(R entityRenderer) {
        return new GeckoMaidFaceTrackingLayer<>(entityRenderer);
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
        if (!(entity instanceof EntityMaid maid) || !maid.isAddedToWorld()) {
            return;
        }

        ILocationModel modelObject = getGeoEntity(entity).getGeoModel();
        if (!(modelObject instanceof AnimatedGeoModel model)) {
            if (maid.isYsmModel()) {
                try {
                    YsmFaceTrackingCapture.captureHeadFrame(
                            maid,
                            poseStack,
                            modelObject.headBones()
                    );
                } finally {
                    YsmFaceTrackingCapture.endLayerSection(maid);
                }
                return;
            }
            DynamicMaidFaceTracker.invalidate(maid);
            FaceTrackingGeometryCache.reportFailureOnce(
                    modelObject,
                    maid.isYsmModel() ? maid.getYsmModelId() : maid.getModelId(),
                    maid.isYsmModel()
                            ? FaceGeometry.Source.YSM
                            : FaceGeometry.Source.GECKO,
                    FaceGeometry.FailureReason.UNSUPPORTED_GEOMETRY_SOURCE
            );
            return;
        }

        FaceGeometry.Source source = maid.isYsmModel()
                ? FaceGeometry.Source.YSM
                : FaceGeometry.Source.GECKO;
        GeckoFaceGeometryAdapter.Result result =
                GeckoFaceGeometryAdapter.resolve(model, poseStack);
        result.plane().ifPresentOrElse(
                plane -> {
                    DynamicMaidFaceTracker.update(maid, plane);
                    FaceVertexMarkerRenderer.render(
                            bufferSource,
                            plane,
                            source,
                            result.confidence()
                    );
                    FaceTrackingGeometryCache.reportSuccessOnce(
                            model,
                            maid.isYsmModel()
                                    ? maid.getYsmModelId()
                                    : maid.getModelId(),
                            source,
                            result.key(),
                            result.confidence()
                    );
                },
                () -> {
                    DynamicMaidFaceTracker.invalidate(maid);
                    FaceTrackingGeometryCache.reportFailureOnce(
                            model,
                            maid.isYsmModel()
                                    ? maid.getYsmModelId()
                                    : maid.getModelId(),
                            source,
                            result.failureReason()
                    );
                }
        );
    }

}
