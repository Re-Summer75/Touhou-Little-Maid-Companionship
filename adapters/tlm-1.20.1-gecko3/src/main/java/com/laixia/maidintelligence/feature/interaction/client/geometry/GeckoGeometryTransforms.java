package com.laixia.maidintelligence.feature.interaction.client.geometry;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.laixia.maidintelligence.shared.geometry.Vec3d;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class GeckoGeometryTransforms {
    private static final ThreadLocal<PoseStack> SCRATCH_POSE =
            ThreadLocal.withInitial(PoseStack::new);

    private GeckoGeometryTransforms() {
    }

    static Optional<FaceGeometry.Frame> createFrame(
            PoseStack basePose,
            List<AnimatedGeoBone> anchorHierarchy
    ) {
        PoseStack pose = scratchPoseFor(basePose, anchorHierarchy);
        return FaceGeometry.Frame.tryCreate(
                transformedPosition(pose, 0.0F, 0.0F, 0.0F),
                transformedDirection(pose, -1.0F, 0.0F, 0.0F),
                transformedDirection(pose, 0.0F, 1.0F, 0.0F),
                transformedDirection(pose, 0.0F, 0.0F, -1.0F)
        );
    }

    /**
     * Reuses one never-pushed PoseStack per thread. Callers must extract what
     * they need before the next scratch call on the same thread.
     */
    static PoseStack scratchPoseFor(
            PoseStack basePose,
            List<AnimatedGeoBone> hierarchy
    ) {
        PoseStack pose = SCRATCH_POSE.get();
        pose.last().pose().set(basePose.last().pose());
        pose.last().normal().set(basePose.last().normal());
        for (AnimatedGeoBone bone : hierarchy) {
            RenderUtils.prepMatrixForBone(pose, bone);
        }
        return pose;
    }

    static List<AnimatedGeoBone> hierarchy(
            AnimatedGeoModel model,
            AnimatedGeoBone bone
    ) {
        List<AnimatedGeoBone> hierarchy = new ArrayList<>();
        AnimatedGeoBone current = bone;
        while (current != null) {
            hierarchy.add(0, current);
            if (current.geoBone().parent() == null) {
                break;
            }
            current = model.bones().get(current.geoBone().parent().name());
        }
        return List.copyOf(hierarchy);
    }

    static PoseStack poseFor(
            PoseStack basePose,
            List<AnimatedGeoBone> hierarchy
    ) {
        PoseStack pose = copyPose(basePose);
        hierarchy.forEach(bone -> RenderUtils.prepMatrixForBone(pose, bone));
        return pose;
    }

    static boolean isVisible(
            List<AnimatedGeoBone> hierarchy,
            AnimatedGeoBone owner
    ) {
        for (int index = 0; index < hierarchy.size(); index++) {
            AnimatedGeoBone bone = hierarchy.get(index);
            int nonZeroAxes = (bone.getScaleX() == 0.0F ? 0 : 1)
                    + (bone.getScaleY() == 0.0F ? 0 : 1)
                    + (bone.getScaleZ() == 0.0F ? 0 : 1);
            if (nonZeroAxes < 2) {
                return false;
            }
            if (index < hierarchy.size() - 1 && bone.childBonesAreHiddenToo()) {
                return false;
            }
        }
        return !owner.isHidden() && !owner.cubesAreHidden();
    }

    static Vec3d toRender(
            PoseStack pose,
            Vector3f localPosition
    ) {
        Vector3f transformed = new Vector3f(localPosition)
                .mulPosition(pose.last().pose());
        return new Vec3d(
                transformed.x(),
                transformed.y(),
                transformed.z()
        );
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
        return toRender(pose, new Vector3f(x, y, z));
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
