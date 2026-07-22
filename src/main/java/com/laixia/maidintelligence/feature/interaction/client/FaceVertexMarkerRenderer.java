package com.laixia.maidintelligence.feature.interaction.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.laixia.maidintelligence.feature.interaction.domain.MouthTargetRegion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
final class FaceVertexMarkerRenderer {
    private static final double MARKER_SIZE = 0.06D;
    private static final double PATCH_SURFACE_OFFSET = 0.002D;
    private static final float[][] COLORS = {
            {1.0F, 0.1F, 0.1F},
            {0.1F, 1.0F, 0.1F},
            {0.1F, 0.4F, 1.0F},
            {1.0F, 0.9F, 0.1F}
    };

    private FaceVertexMarkerRenderer() {
    }

    static void render(MultiBufferSource buffers, MaidFacePlane plane) {
        if (!isDebugEnabled()) {
            return;
        }

        PoseStack identityPose = new PoseStack();
        for (int index = 0; index < plane.vertices().size(); index++) {
            Vec3 vertex = plane.vertices().get(index);
            float[] color = COLORS[index];
            DebugRenderer.renderFilledBox(
                    identityPose,
                    buffers,
                    AABB.ofSize(vertex, MARKER_SIZE, MARKER_SIZE, MARKER_SIZE),
                    color[0],
                    color[1],
                    color[2],
                    1.0F
            );
        }
        renderTargetPatch(buffers, identityPose.last().pose(), plane);
    }

    private static void renderTargetPatch(
            MultiBufferSource buffers,
            Matrix4f pose,
            MaidFacePlane plane
    ) {
        Vec3 leftBottom = plane.point(
                MouthTargetRegion.MIN_U,
                MouthTargetRegion.MIN_V
        );
        Vec3 rightBottom = plane.point(
                MouthTargetRegion.MAX_U,
                MouthTargetRegion.MIN_V
        );
        Vec3 rightTop = plane.point(
                MouthTargetRegion.MAX_U,
                MouthTargetRegion.MAX_V
        );
        Vec3 leftTop = plane.point(
                MouthTargetRegion.MIN_U,
                MouthTargetRegion.MAX_V
        );
        Vec3 patchCenter = leftBottom.add(rightTop).scale(0.5D);
        Vec3 towardCamera = patchCenter.scale(-1.0D);
        Vec3 normal = plane.normal();
        if (normal.dot(towardCamera) < 0.0D) {
            normal = normal.scale(-1.0D);
        }
        Vec3 surfaceOffset = normal.scale(PATCH_SURFACE_OFFSET);
        leftBottom = leftBottom.add(surfaceOffset);
        rightBottom = rightBottom.add(surfaceOffset);
        rightTop = rightTop.add(surfaceOffset);
        leftTop = leftTop.add(surfaceOffset);

        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        quad(quads, pose, leftBottom, rightBottom, rightTop, leftTop);
        quad(quads, pose, leftTop, rightTop, rightBottom, leftBottom);
    }

    private static void quad(
            VertexConsumer consumer,
            Matrix4f pose,
            Vec3 first,
            Vec3 second,
            Vec3 third,
            Vec3 fourth
    ) {
        vertex(consumer, pose, first);
        vertex(consumer, pose, second);
        vertex(consumer, pose, third);
        vertex(consumer, pose, fourth);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Vec3 vertex) {
        consumer.vertex(pose, (float) vertex.x, (float) vertex.y, (float) vertex.z)
                .color(255, 96, 24, 255)
                .endVertex();
    }

    private static boolean isDebugEnabled() {
        return Minecraft.getInstance()
                .getEntityRenderDispatcher()
                .shouldRenderHitBoxes();
    }
}
