package com.laixia.maidintelligence.feature.physics.client.solver.collision;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneRestPose;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.build.HeadColliderShapeBuilder;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Bakes the legacy head Backstop and sphere into generic proxy records.
 */
public final class HeadCollisionProxyBuilder {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float EPSILON = 1.0E-6F;
    private static final float LEGAL_MARGIN = 0.02F;

    private HeadCollisionProxyBuilder() {
    }

    /**
     * @deprecated driven descendants now receive their own proxy set.
     */
    @Deprecated
    public static CollisionProxySet build(
            PhysicsSolverLayout.Node node,
            int referenceIndex,
            AnimatedGeoBone referenceBone,
            BoneRestPose referencePose,
            boolean drivenParent,
            PhysicsBoneGeometry.Analysis geometry,
            BoneRestPose boneRestPose,
            Vector3f pivot
    ) {
        return build(
                node,
                referenceIndex,
                referenceBone,
                referencePose,
                geometry,
                boneRestPose,
                pivot
        );
    }

    public static CollisionProxySet build(
            PhysicsSolverLayout.Node node,
            int referenceIndex,
            AnimatedGeoBone referenceBone,
            BoneRestPose referencePose,
            PhysicsBoneGeometry.Analysis geometry,
            BoneRestPose boneRestPose,
            Vector3f pivot
    ) {
        boolean hasHeadCollider = referenceBone != null
                && !geometry.headBounds().isEmpty();
        Quaternionf referenceRest = referencePose == null
                ? new Quaternionf()
                : referencePose.orientation();
        Quaternionf referenceInverse =
                new Quaternionf(referenceRest).conjugate();
        PhysicsBoneGeometry.Node geometryNode =
                geometry.node(node.bone());
        Vector3f headCenter = geometry.headBounds().center();
        Vector3f pivotOffsetModel = new Vector3f(pivot).sub(headCenter);
        Vector3f outward = new Vector3f(pivotOffsetModel);
        if (outward.lengthSquared() < EPSILON && geometryNode != null) {
            outward.set(geometryNode.center()).sub(headCenter);
        }
        if (outward.lengthSquared() < EPSILON) {
            outward.set(0.0F, 0.0F, -1.0F);
        } else {
            outward.normalize();
        }

        Vector3f pivotFromReference = referenceInverse.transform(
                pivotOffsetModel,
                new Vector3f()
        );
        Vector3f normalFromReference = referenceInverse.transform(
                outward,
                new Vector3f()
        ).normalize();
        Vector3f headSize = geometry.headBounds().size();
        float headRadius = Math.max(
                headSize.x,
                Math.max(headSize.y, headSize.z)
        ) * 0.5F;
        PhysicsBoneSelectionPlan.ConstraintProfile profile =
                node.decision().constraints();
        float hitRadius = deriveHitRadius(geometryNode, headRadius)
                * profile.hitRadiusScale();
        Vector3f planePointModel =
                new Vector3f(outward).mul(headRadius);
        Vector3f planePointFromReference = referenceInverse.transform(
                planePointModel,
                new Vector3f()
        );

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
                .normalize().mul(rawLeverArm).add(pivot)
                : boneRestPose.transformPosition(rawTip, new Vector3f());
        float leverArm = Math.max(
                pivot.distance(restTipModel),
                1.0F / PIXELS_PER_BLOCK
        );
        Vector3f restTip = restTipModel.sub(headCenter);
        float planeDistance =
                (restTip.x - planePointModel.x) * outward.x
                        + (restTip.y - planePointModel.y) * outward.y
                        + (restTip.z - planePointModel.z) * outward.z;
        boolean legalPlane =
                planeDistance + LEGAL_MARGIN >= hitRadius;
        boolean legalSphere = HeadColliderShapeBuilder.isRestTipLegal(
                headSize,
                restTip,
                headRadius,
                hitRadius,
                LEGAL_MARGIN
        );
        boolean eligible = profile.enabled()
                && hasHeadCollider
                && node.decision().type()
                != PhysicsBoneSelectionPlan.PartType.HEAD_SHELL;
        boolean usePlane = eligible && profile.backstop() && legalPlane;
        boolean useSphere =
                eligible && profile.headCollision() && legalSphere;
        if (!usePlane && !useSphere) {
            return CollisionProxySet.EMPTY;
        }

        CollisionProxy[] proxies =
                new CollisionProxy[(usePlane ? 1 : 0)
                        + (useSphere ? 1 : 0)];
        int index = 0;
        if (usePlane) {
            proxies[index++] = CollisionProxies.plane(
                    referenceIndex,
                    headCenter,
                    pivotFromReference,
                    planePointFromReference,
                    normalFromReference,
                    hitRadius,
                    leverArm
            );
        }
        if (useSphere) {
            proxies[index] = HeadColliderShapeBuilder.build(
                    referenceIndex,
                    headCenter,
                    pivotFromReference,
                    referenceInverse,
                    headSize,
                    headRadius,
                    hitRadius,
                    leverArm
            );
        }
        return new CollisionProxySet(proxies);
    }

    private static float deriveHitRadius(
            PhysicsBoneGeometry.Node node,
            float headRadius
    ) {
        if (node == null || !node.hasGeometry()) {
            return 0.0F;
        }
        Vector3f size = node.size();
        float smallest = smallestPositive(size.x, size.y, size.z);
        return smallest <= EPSILON
                ? 0.0F
                : Math.min(smallest * 0.5F, headRadius * 0.15F);
    }

    private static float smallestPositive(float x, float y, float z) {
        float result = Float.POSITIVE_INFINITY;
        if (x > EPSILON) {
            result = x;
        }
        if (y > EPSILON) {
            result = Math.min(result, y);
        }
        if (z > EPSILON) {
            result = Math.min(result, z);
        }
        return Float.isFinite(result) ? result : 0.0F;
    }
}
