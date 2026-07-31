package com.laixia.maidintelligence.feature.physics.layout;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.CollisionProxyComposer;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.CollisionProxyPlan;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.layout.mesh.MeshSheetExtent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.IdentityHashMap;

/**
 * Derives immutable constraints while a solver layout is being baked.
 */
final class SecondaryConstraintFactory {
    private static final float EPSILON = 1.0E-6F;

    private SecondaryConstraintFactory() {
    }

    static SecondaryMotionConstraint create(
            PhysicsSolverLayout.Node node,
            PhysicsBoneSelectionPlan.SimulationSpace space,
            int referenceIndex,
            PhysicsBoneGeometry.Analysis geometry,
            IdentityHashMap<BoneModelSnapshot.Bone, BoneRestPose> restPoses,
            CollisionProxyComposer collisionComposer,
            CollisionProxyPlan collisionPlan
    ) {
        PhysicsBoneSelectionPlan.ConstraintProfile profile =
                node.decision().constraints();
        PhysicsBoneSelectionPlan.SwingLimits authored =
                profile.swingLimits();
        float cap = SwingRange.maximum(node);
        PhysicsBoneSelectionPlan.SwingLimits limits =
                new PhysicsBoneSelectionPlan.SwingLimits(
                        Math.min(authored.left(), cap),
                        Math.min(authored.right(), cap),
                        Math.min(authored.outward(), cap),
                        Math.min(authored.inward(), cap)
                );

        BoneRestPose boneRestPose = restPoses.get(node.bone());
        Quaternionf boneRest = boneRestPose == null
                ? new Quaternionf()
                : boneRestPose.orientation();
        PhysicsBoneGeometry.Node geometryNode =
                geometry.node(node.bone());
        PhysicsBoneGeometry.Bounds referenceBounds = switch (space) {
            case HEAD_LOCAL -> geometry.headBounds();
            case BODY_LOCAL -> geometry.bodyBounds();
            case MODEL, AUTO -> geometry.modelBounds();
        };
        Vector3f referenceCenter = referenceBounds.center();
        Vector3f pivot = boneRestPose == null
                ? geometryNode == null
                        ? new Vector3f()
                        : new Vector3f(geometryNode.pivot())
                : boneRestPose.transformPosition(
                        node.kinematics().effectivePivot(),
                        new Vector3f()
                );
        Vector3f outwardModel =
                new Vector3f(pivot).sub(referenceCenter);
        if (outwardModel.lengthSquared() < EPSILON && geometryNode != null) {
            outwardModel.set(geometryNode.center()).sub(referenceCenter);
        }
        if (outwardModel.lengthSquared() < EPSILON) {
            outwardModel.set(0.0F, 0.0F, -1.0F);
        } else {
            outwardModel.normalize();
        }

        Vector3f outwardLocal = boneRest.transformInverse(
                outwardModel,
                new Vector3f()
        );
        orthogonalize(outwardLocal, node.axis());
        Vector3f rightLocal = outwardLocal.cross(
                node.axis(),
                new Vector3f()
        );
        if (rightLocal.lengthSquared() < EPSILON) {
            rightLocal.set(1.0F, 0.0F, 0.0F);
            orthogonalize(rightLocal, node.axis());
        } else {
            rightLocal.normalize();
        }
        node.axis().cross(rightLocal, outwardLocal).normalize();

        CollisionProxySet collisionProxies = collisionComposer.compose(
                collisionPlan,
                node,
                boneRestPose,
                pivot
        );

        /*
         * Only the thin mesh direction pads a surface contact. Width and
         * length never stand between the axis and a surface.
         */
        Vector3f meshHalf = MeshSheetExtent.halfExtents(
                geometryNode,
                node.axis(),
                rightLocal,
                outwardLocal
        );
        return new SecondaryMotionConstraint(
                profile.enabled(),
                space,
                referenceIndex,
                profile.rotationInertiaScale(),
                rightLocal,
                outwardLocal,
                limits,
                collisionProxies,
                meshHalf
        );
    }

    private static void orthogonalize(Vector3f vector, Vector3f axis) {
        float projection = vector.dot(axis);
        vector.add(
                -axis.x * projection,
                -axis.y * projection,
                -axis.z * projection
        );
        if (vector.lengthSquared() < EPSILON) {
            if (Math.abs(axis.y) < 0.90F) {
                vector.set(0.0F, 1.0F, 0.0F).cross(axis);
            } else {
                vector.set(1.0F, 0.0F, 0.0F).cross(axis);
            }
        }
        vector.normalize();
    }
}
