package com.laixia.maidintelligence.feature.interaction.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

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
        if (!(entity instanceof EntityMaid maid)
                || !(getGeoEntity(entity).getGeoModel() instanceof AnimatedGeoModel model)
                || model.head() == null
                || model.headBones().isEmpty()) {
            return;
        }

        GeoMesh mesh = model.head().geoBone().cubes();
        int cubeIndex = findMainHeadCube(mesh);
        if (cubeIndex < 0) {
            return;
        }

        PoseStack headPose = copyPose(poseStack);
        model.headBones().forEach(bone -> RenderUtils.prepMatrixForBone(headPose, bone));
        updateTrackedRegion(
                maid,
                bufferSource,
                headPose.last().pose(),
                mesh,
                cubeIndex
        );
    }

    private static int findMainHeadCube(GeoMesh mesh) {
        int bestIndex = -1;
        float bestVolume = 0.0F;
        for (int i = 0; i < mesh.getCubeCount(); i++) {
            float volume = mesh.dx(i).length() * mesh.dy(i).length() * mesh.dz(i).length();
            if (volume > bestVolume) {
                bestVolume = volume;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static PoseStack copyPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    private static void updateTrackedRegion(
            EntityMaid maid,
            MultiBufferSource bufferSource,
            Matrix4f pose,
            GeoMesh mesh,
            int cubeIndex
    ) {
        Vector3f position = new Vector3f(mesh.position(cubeIndex));
        Vector3f dx = new Vector3f(mesh.dx(cubeIndex));
        Vector3f dy = new Vector3f(mesh.dy(cubeIndex));
        Vector3f dz = new Vector3f(mesh.dz(cubeIndex));
        if (dz.lengthSquared() <= 1.0E-8F) {
            return;
        }

        Vec3 renderP000 = toRender(pose, position);
        Vec3 renderRightSpan = toRender(
                pose,
                new Vector3f(position).add(dx)
        ).subtract(renderP000);
        Vec3 renderUpSpan = toRender(
                pose,
                new Vector3f(position).add(dy)
        ).subtract(renderP000);
        List<Vec3> faceVertices = List.of(
                renderP000,
                renderP000.add(renderRightSpan),
                renderP000.add(renderRightSpan).add(renderUpSpan),
                renderP000.add(renderUpSpan)
        );
        MaidFacePlane plane = MaidFacePlane.fromVertices(
                faceVertices,
                renderUpSpan
        ).orElse(null);
        if (plane == null) {
            return;
        }
        FaceVertexMarkerRenderer.render(bufferSource, plane);
        DynamicMaidFaceTracker.update(maid, plane);
    }

    private static Vec3 toRender(Matrix4f pose, Vector3f localPosition) {
        Vector3f transformed = new Vector3f(localPosition).mulPosition(pose);
        return new Vec3(transformed.x(), transformed.y(), transformed.z());
    }
}
