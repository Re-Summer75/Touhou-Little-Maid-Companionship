package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneRestPose;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxies;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxy;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.Optional;

/**
 * Shared construction math for automatic and authored proxies.
 */
final class CollisionBakeContext {
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private final PhysicsSolverLayout.Node node;
    private final PhysicsBoneGeometry.Node geometryNode;
    private final BoneRestPose boneRestPose;
    private final Vector3f pivotModel;
    private final IdentityHashMap<AnimatedGeoBone, Integer> indices;
    private final IdentityHashMap<AnimatedGeoBone, BoneRestPose> restPoses;
    private final float leverArm;

    CollisionBakeContext(
            PhysicsSolverLayout.Node node,
            PhysicsBoneGeometry.Node geometryNode,
            BoneRestPose boneRestPose,
            Vector3f pivotModel,
            IdentityHashMap<AnimatedGeoBone, Integer> indices,
            IdentityHashMap<AnimatedGeoBone, BoneRestPose> restPoses
    ) {
        this.node = node;
        this.geometryNode = geometryNode;
        this.boneRestPose = boneRestPose;
        this.pivotModel = new Vector3f(pivotModel);
        this.indices = indices;
        this.restPoses = restPoses;
        this.leverArm = deriveLeverArm();
    }

    PhysicsSolverLayout.Node node() {
        return node;
    }

    BoneRestPose boneRestPose() {
        return boneRestPose;
    }

    Vector3f copyPivot(Vector3f output) {
        return output.set(pivotModel);
    }

    BoneRestPose restPose(CollisionReference reference) {
        return reference == null || reference.bone() == null
                ? null
                : restPoses.get(reference.bone());
    }

    int referenceIndex(CollisionReference reference) {
        return reference == null || reference.bone() == null
                ? -1
                : indices.getOrDefault(reference.bone(), -1);
    }

    float hitRadius(float colliderRadius) {
        return CollisionHitRadius.derive(geometryNode, colliderRadius)
                * node.decision().constraints().hitRadiusScale();
    }

    float hitRadius(
            Optional<Float> authoredPixels,
            float colliderRadius
    ) {
        float radius = authoredPixels.isPresent()
                ? authoredPixels.get() / PIXELS_PER_BLOCK
                : CollisionHitRadius.derive(
                        geometryNode,
                        colliderRadius
                );
        return radius * node.decision().constraints().hitRadiusScale();
    }

    CollisionProxy plane(
            CollisionReference reference,
            Vector3f pointModel,
            Vector3f normalModel,
            float hitRadius
    ) {
        Quaternionf inverse = inverse(reference);
        Vector3f origin = reference.copyOrigin(new Vector3f());
        return CollisionProxies.plane(
                referenceIndex(reference),
                origin,
                relative(pivotModel, origin, inverse),
                relative(pointModel, origin, inverse),
                inverse.transform(normalModel, new Vector3f()).normalize(),
                hitRadius,
                leverArm
        );
    }

    CollisionProxy sphere(
            CollisionReference reference,
            Vector3f centerModel,
            float radius,
            float hitRadius
    ) {
        Quaternionf inverse = inverse(reference);
        Vector3f origin = reference.copyOrigin(new Vector3f());
        return CollisionProxies.sphere(
                referenceIndex(reference),
                origin,
                relative(pivotModel, origin, inverse),
                relative(centerModel, origin, inverse),
                radius,
                hitRadius,
                leverArm
        );
    }

    CollisionProxy capsule(
            CollisionReference reference,
            Vector3f startModel,
            Vector3f endModel,
            float radius,
            float hitRadius
    ) {
        Quaternionf inverse = inverse(reference);
        Vector3f origin = reference.copyOrigin(new Vector3f());
        return CollisionProxies.capsule(
                referenceIndex(reference),
                origin,
                relative(pivotModel, origin, inverse),
                relative(startModel, origin, inverse),
                relative(endModel, origin, inverse),
                radius,
                hitRadius,
                leverArm
        );
    }

    private Quaternionf inverse(CollisionReference reference) {
        BoneRestPose pose = restPose(reference);
        return pose == null
                ? new Quaternionf()
                : new Quaternionf(pose.orientation()).conjugate();
    }

    private static Vector3f relative(
            Vector3f position,
            Vector3f origin,
            Quaternionf inverse
    ) {
        return inverse.transform(
                new Vector3f(position).sub(origin),
                new Vector3f()
        );
    }

    private float deriveLeverArm() {
        Vector3f axis = node.axisInto(new Vector3f());
        float rawLeverArm =
                node.kinematics().segmentLength() / PIXELS_PER_BLOCK;
        Vector3f rawTip = node.kinematics().effectivePivot()
                .fma(rawLeverArm, axis);
        Quaternionf boneRest = boneRestPose == null
                ? new Quaternionf()
                : boneRestPose.orientation();
        Vector3f restTipModel = boneRestPose == null
                ? boneRest.transform(axis, new Vector3f())
                .normalize().mul(rawLeverArm).add(pivotModel)
                : boneRestPose.transformPosition(rawTip, new Vector3f());
        return Math.max(
                pivotModel.distance(restTipModel),
                1.0F / PIXELS_PER_BLOCK
        );
    }

}
