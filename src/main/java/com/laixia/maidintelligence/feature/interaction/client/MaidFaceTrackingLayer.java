package com.laixia.maidintelligence.feature.interaction.client;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockCube;
import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class MaidFaceTrackingLayer extends RenderLayer<Mob, BedrockModel<Mob>> {
    private static final Vector3f[] UNUSED_NORMALS = createNormals();

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
        if (!(entity instanceof EntityMaid maid)) {
            return;
        }

        BedrockModel<Mob> model = getParentModel();
        if (!model.hasHead() || model.getHead().cubes.isEmpty()) {
            return;
        }

        PoseStack headPose = copyPose(poseStack);
        applyPartHierarchy(headPose, model.getHead());
        Vec3 expectedForward = transformedDirection(
                headPose,
                0.0F,
                0.0F,
                -1.0F
        );
        Vec3 expectedUp = transformedDirection(
                headPose,
                0.0F,
                -1.0F,
                0.0F
        );

        FaceCandidate best = null;
        for (BedrockCube cube : model.getHead().cubes) {
            FaceCandidate candidate = captureFace(
                    cube,
                    headPose,
                    expectedForward,
                    expectedUp
            );
            if (candidate != null && (best == null || candidate.score() > best.score())) {
                best = candidate;
            }
        }
        if (best != null) {
            best.update(maid);
            best.renderMarkers(buffer);
        }
    }

    private static FaceCandidate captureFace(
            BedrockCube cube,
            PoseStack headPose,
            Vec3 expectedForward,
            Vec3 expectedUp
    ) {
        CapturingVertexConsumer consumer = new CapturingVertexConsumer();
        cube.compile(
                headPose.last(),
                UNUSED_NORMALS,
                consumer,
                0,
                0,
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );
        List<Vec3> vertices = consumer.renderVertices();
        if (vertices.size() < BedrockCube.NUM_CUBE_FACES * 4) {
            return null;
        }

        Vec3 cubeCenter = average(vertices);
        List<Vec3> frontFace = null;
        double bestFacing = Double.NEGATIVE_INFINITY;
        for (int face = 0; face < BedrockCube.NUM_CUBE_FACES; face++) {
            List<Vec3> faceVertices = vertices.subList(face * 4, face * 4 + 4);
            Vec3 faceCenter = average(faceVertices);
            Vec3 outward = faceCenter.subtract(cubeCenter);
            if (outward.lengthSqr() <= 1.0E-8D) {
                continue;
            }
            double facing = outward.normalize().dot(expectedForward);
            if (facing > bestFacing) {
                bestFacing = facing;
                frontFace = faceVertices;
            }
        }
        if (frontFace == null || bestFacing < 0.35D) {
            return null;
        }

        Vec3 faceCenter = average(frontFace);
        Vec3 outward = faceCenter.subtract(cubeCenter).normalize();
        MaidFacePlane plane = MaidFacePlane.fromVertices(frontFace, expectedUp)
                .orElse(null);
        if (plane == null) {
            return null;
        }

        double depth = maxProjectionDistance(vertices, cubeCenter, outward) * 2.0D;
        double score = plane.width() * plane.height() * Math.max(depth, 1.0E-4D);
        return new FaceCandidate(
                plane,
                score
        );
    }

    private static double maxProjectionDistance(
            List<Vec3> vertices,
            Vec3 center,
            Vec3 axis
    ) {
        double maximum = 0.0D;
        for (Vec3 vertex : vertices) {
            maximum = Math.max(maximum, Math.abs(vertex.subtract(center).dot(axis)));
        }
        return maximum;
    }

    private static Vec3 transformedDirection(
            PoseStack poseStack,
            float x,
            float y,
            float z
    ) {
        Vector3f transformed = new Vector3f(x, y, z)
                .mulDirection(poseStack.last().pose())
                .normalize();
        return new Vec3(transformed.x(), transformed.y(), transformed.z());
    }

    private static Vec3 average(List<Vec3> vertices) {
        Vec3 result = Vec3.ZERO;
        for (Vec3 vertex : vertices) {
            result = result.add(vertex);
        }
        return result.scale(1.0D / vertices.size());
    }

    private static PoseStack copyPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    private static void applyPartHierarchy(PoseStack poseStack, BedrockPart part) {
        List<BedrockPart> hierarchy = new ArrayList<>();
        BedrockPart current = part;
        while (current != null) {
            hierarchy.add(current);
            current = current.getParent();
        }
        Collections.reverse(hierarchy);
        hierarchy.forEach(bone -> bone.translateAndRotateAndScale(poseStack));
    }

    private static Vector3f[] createNormals() {
        Vector3f[] normals = new Vector3f[BedrockCube.NUM_CUBE_FACES];
        for (int i = 0; i < normals.length; i++) {
            normals[i] = new Vector3f();
        }
        return normals;
    }

    private record FaceCandidate(
            MaidFacePlane plane,
            double score
    ) {
        private void update(EntityMaid maid) {
            DynamicMaidFaceTracker.update(maid, plane);
        }

        private void renderMarkers(MultiBufferSource buffers) {
            FaceVertexMarkerRenderer.render(buffers, plane);
        }
    }

    private static final class CapturingVertexConsumer implements VertexConsumer {
        private final List<Vec3> vertices = new ArrayList<>();

        private List<Vec3> renderVertices() {
            return vertices;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            vertices.add(new Vec3(x, y, z));
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
}
