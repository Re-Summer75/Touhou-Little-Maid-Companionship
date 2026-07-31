package com.laixia.maidintelligence.feature.interaction.client.geometry;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.laixia.maidintelligence.shared.geometry.Vec3d;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class BedrockGeometryTransforms {
    private static final ThreadLocal<PoseStack> SCRATCH_POSE =
            ThreadLocal.withInitial(PoseStack::new);

    private BedrockGeometryTransforms() {
    }

    static Optional<FaceGeometry.Frame> createFrame(
            PoseStack basePose,
            List<BedrockPart> anchorHierarchy
    ) {
        PoseStack pose = scratchPoseFor(basePose, anchorHierarchy);
        return FaceGeometry.Frame.tryCreate(
                transformedPosition(pose, 0.0F, 0.0F, 0.0F),
                transformedDirection(pose, 1.0F, 0.0F, 0.0F),
                transformedDirection(pose, 0.0F, -1.0F, 0.0F),
                transformedDirection(pose, 0.0F, 0.0F, -1.0F)
        );
    }

    static PoseStack poseFor(
            PoseStack basePose,
            List<BedrockPart> hierarchy
    ) {
        PoseStack pose = copyPose(basePose);
        hierarchy.forEach(part -> part.translateAndRotateAndScale(pose));
        return pose;
    }

    /**
     * Reuses one never-pushed PoseStack per thread. Callers must extract what
     * they need before the next scratch call on the same thread.
     */
    static PoseStack scratchPoseFor(
            PoseStack basePose,
            List<BedrockPart> hierarchy
    ) {
        PoseStack pose = SCRATCH_POSE.get();
        pose.last().pose().set(basePose.last().pose());
        pose.last().normal().set(basePose.last().normal());
        for (BedrockPart part : hierarchy) {
            part.translateAndRotateAndScale(pose);
        }
        return pose;
    }

    static List<BedrockPart> hierarchy(BedrockPart part) {
        List<BedrockPart> hierarchy = new ArrayList<>();
        BedrockPart current = part;
        while (current != null) {
            hierarchy.add(0, current);
            current = current.getParent();
        }
        return List.copyOf(hierarchy);
    }

    static boolean isUsable(
            List<BedrockPart> hierarchy,
            boolean allowHiddenSurface
    ) {
        for (int index = 0; index < hierarchy.size(); index++) {
            BedrockPart part = hierarchy.get(index);
            boolean hiddenSemanticLeaf = allowHiddenSurface
                    && index == hierarchy.size() - 1;
            // Blink/face overlays are commonly hidden outside their animation,
            // but their unchanged geometry remains the best facial locator.
            if (!part.visible && !hiddenSemanticLeaf) {
                return false;
            }
            boolean xZero = Math.abs(part.xScale) <= 1.0E-5F;
            boolean yZero = Math.abs(part.yScale) <= 1.0E-5F;
            boolean zZero = Math.abs(part.zScale) <= 1.0E-5F;
            if ((xZero && yZero) || (xZero && zZero) || (yZero && zZero)) {
                return false;
            }
        }
        return true;
    }

    static Vector3f[] createNormals(Matrix3f normal) {
        return new Vector3f[]{
                new Vector3f(-normal.m10, -normal.m11, -normal.m12).normalize(),
                new Vector3f(normal.m10, normal.m11, normal.m12).normalize(),
                new Vector3f(-normal.m20, -normal.m21, -normal.m22).normalize(),
                new Vector3f(normal.m20, normal.m21, normal.m22).normalize(),
                new Vector3f(-normal.m00, -normal.m01, -normal.m02).normalize(),
                new Vector3f(normal.m00, normal.m01, normal.m02).normalize()
        };
    }

    static double projectionRange(
            List<Vec3d> vertices,
            Vec3d axis
    ) {
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (Vec3d vertex : vertices) {
            double projection = vertex.dot(axis);
            minimum = Math.min(minimum, projection);
            maximum = Math.max(maximum, projection);
        }
        return maximum - minimum;
    }

    private static PoseStack copyPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    private static Vec3d transformedPosition(
            PoseStack pose,
            float x,
            float y,
            float z
    ) {
        Vector3f transformed = new Vector3f(x, y, z)
                .mulPosition(pose.last().pose());
        return new Vec3d(
                transformed.x(),
                transformed.y(),
                transformed.z()
        );
    }

    private static Vec3d transformedDirection(
            PoseStack pose,
            float x,
            float y,
            float z
    ) {
        Vector3f transformed = new Vector3f(x, y, z)
                .mulDirection(pose.last().pose())
                .normalize();
        return new Vec3d(
                transformed.x(),
                transformed.y(),
                transformed.z()
        );
    }
}
