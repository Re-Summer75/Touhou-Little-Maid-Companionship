package com.laixia.maidintelligence.feature.physics.client.audit;

import com.laixia.maidintelligence.feature.physics.client.MeshPenetrationAuditAccess;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProxyDebugData;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class AuditSupport {
    static final float DT = 1.0F / 60.0F;
    static final int SETTLE = 240;
    static final int SAMPLE = 120;
    static final float PIXELS_PER_BLOCK = 16.0F;
    /** Below this a step is numerical noise rather than motion. */
    static final float MOVED = 1.0E-4F;
    /** X rotation in degrees, from the shipped sit animation. */
    private static final Map<String, Float> SIT_POSE = Map.ofEntries(
            Map.entry("LeftLeg", -62.5F),
            Map.entry("RightLeg", -62.5F),
            Map.entry("LeftLowerLeg", 165.0F),
            Map.entry("RightLowerLeg", 165.0F),
            Map.entry("LeftFoot", 35.0F),
            Map.entry("RightFoot", 40.0F),
            Map.entry("UpBody", 20.0F),
            Map.entry("UpperBody", -20.0F),
            Map.entry("DownBody", -10.0F),
            Map.entry("FrontClothe", -65.0F),
            Map.entry("BackClothe", -12.5F),
            Map.entry("FL2", 62.5F),
            Map.entry("FR2", 62.5F),
            Map.entry("FM2", 62.5F),
            Map.entry("BR2", -90.0F),
            Map.entry("BL2", -90.0F),
            Map.entry("BM2", -90.0F),
            Map.entry("LF2", 7.5F),
            Map.entry("LF3", 15.0F),
            Map.entry("LB2", -7.5F),
            Map.entry("RB", -7.5F),
            Map.entry("RF", 7.5F),
            Map.entry("RF3", 15.0F),
            Map.entry("wb", 65.0F),
            Map.entry("Leg", -35.0F),
            Map.entry("LongHair", 12.5F)
    );

    private AuditSupport() {
    }

    static Path modelPath(String model) {
        return MeshPenetrationAuditAccess.modelDirectory().resolve(model);
    }

    static BoneModelSnapshot loadModel(Path path) throws Exception {
        return MeshPenetrationAuditAccess.loadModel(path);
    }

    static PhysicsSolverLayout buildLayout(
            String modelId,
            BoneModelSnapshot model
    ) {
        return MeshPenetrationAuditAccess.buildLayout(modelId, model);
    }

    static PhysicsBoneGeometry.Analysis analyze(BoneModelSnapshot model) {
        return PhysicsBoneGeometry.analyze(model);
    }

    /**
     * The shipped {@code sit} pose, taken from winefox.animation.json rather
     * than invented. The values matter: this animation keyframes the driven
     * skirt bones themselves — FrontClothe to -65 degrees, the FM/FL/FR panels
     * forward 62.5, the back panels to -90 — so the authored pose already folds
     * cloth around legs that are themselves rotated into it. An invented pose
     * puts the legs somewhere the panels were never drawn to accommodate and
     * measures a configuration the model never renders.
     */
    static void sit(List<BoneModelSnapshot.Bone> bones) {
        for (BoneModelSnapshot.Bone bone : bones) {
            Float degrees = SIT_POSE.get(bone.getName());
            if (degrees != null) {
                bone.setRotationX((float) Math.toRadians(degrees));
            }
        }
    }

    /** Every bone the sit pose keyframes, whichever of them a model has. */
    static List<BoneModelSnapshot.Bone> legs(BoneModelSnapshot model) {
        List<BoneModelSnapshot.Bone> found = new ArrayList<>();
        for (BoneModelSnapshot.Bone bone : model.boneList()) {
            if (SIT_POSE.containsKey(bone.getName())) {
                found.add(bone);
            }
        }
        return found;
    }

    static int[] drivenSegments(PhysicsSolverLayout layout) {
        List<Integer> found = new ArrayList<>();
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (node.driven()
                    && node.constraint().collisionProxies().proxyCount() > 0) {
                found.add(index);
            }
        }
        int[] result = new int[found.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = found.get(index);
        }
        return result;
    }

    /**
     * How far the deepest corner of the segment's mesh lies inside the box, in
     * model pixels, or nought when every corner is outside it. This is the
     * penetration a viewer sees: cloth is drawn as its cubes, so a cube corner
     * inside a leg is visible however well the axis is placed.
     *
     * <p>Measured as a point-in-box depth, not as a clearance minus a reach.
     * Subtracting an extent taken along the contact normal charges the segment
     * for its own length whenever the normal is not square to the bone axis, and
     * a hanging panel is far longer than it is thick — that read a settled hem as
     * 15.8 px penetrating when nothing had gone inside anything, and it moved for
     * every solver change because it was pinned to geometry rather than to the
     * pose. Walking the corners cannot make that mistake.
     */
    static float meshDepth(
            PhysicsBoneGeometry.Node node,
            Vector3f pivot,
            Vector3f tip,
            CollisionProxyDebugData data
    ) {
        if (node == null || node.cubeBoxes().isEmpty()) {
            return 0.0F;
        }
        /*
         * The cubes are authored in the rest pose, so they are carried onto the
         * solved axis before being tested. Skipping this would test the hem where
         * the artist left it rather than where the solver put it, which is the
         * whole question.
         */
        Vector3f restAxis = new Vector3f(node.center()).sub(node.pivot());
        Vector3f liveAxis = new Vector3f(tip).sub(pivot);
        if (restAxis.lengthSquared() <= 1.0E-12F
                || liveAxis.lengthSquared() <= 1.0E-12F) {
            return 0.0F;
        }
        Quaternionf pose = new Quaternionf().rotateTo(
                restAxis.normalize(), liveAxis.normalize()
        );
        Vector3f corner = new Vector3f();
        float deepest = 0.0F;
        for (PhysicsBoneGeometry.CubeBox box : node.cubeBoxes()) {
            for (int index = 0; index < 8; index++) {
                box.corner(index, corner);
                corner.sub(node.pivot());
                pose.transform(corner);
                corner.add(pivot);
                deepest = Math.max(deepest, boxDepth(corner, data));
            }
        }
        return deepest;
    }

    /**
     * Depth of a point inside an oriented box, nought if it is outside. The
     * smallest distance to a face, since that is the shortest way back out and so
     * what a viewer reads as how far in the part has sunk.
     */
    private static float boxDepth(
            Vector3f point,
            CollisionProxyDebugData data
    ) {
        Vector3f local = new Vector3f(point).sub(data.boxCenter);
        float[] along = {
                local.dot(data.boxAxisX),
                local.dot(data.boxAxisY),
                local.dot(data.boxAxisZ)
        };
        float[] half = {
                data.boxHalfExtents.x,
                data.boxHalfExtents.y,
                data.boxHalfExtents.z
        };
        float shallowest = Float.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++) {
            /*
             * A half-open box is missing the face on its open axis, so that
             * direction cannot contain the point and is not a way out either.
             */
            if (axis == data.boxOpenAxis) {
                continue;
            }
            float inside = half[axis] - Math.abs(along[axis]);
            if (inside <= 0.0F) {
                return 0.0F;
            }
            shallowest = Math.min(shallowest, inside);
        }
        return shallowest == Float.MAX_VALUE ? 0.0F : shallowest;
    }
}
