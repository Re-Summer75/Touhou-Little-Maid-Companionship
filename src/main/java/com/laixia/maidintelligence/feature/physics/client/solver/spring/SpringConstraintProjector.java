package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.laixia.maidintelligence.feature.physics.client.solver.SecondaryMotionConstraint;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.PreparedCollisionProxySet;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Alternates swing and collision projection until stable.
 */
final class SpringConstraintProjector {
    private static final int MAX_ITERATIONS = 4;
    private static final int COLLISION_PASSES_PER_ITERATION = 24;

    private SpringConstraintProjector() {
    }

    static boolean project(
            SecondaryMotionConstraint constraint,
            Quaternionf boneOrientation,
            PreparedCollisionProxySet collisions,
            Vector3f direction,
            float maximumSwing,
            SpringBoneScratch scratch,
            SpringBoneMetrics metrics
    ) {
        boolean corrected = false;
        float maximumCosine = (float) Math.cos(maximumSwing);
        float maximumSine = (float) Math.sin(maximumSwing);
        for (int iteration = 0;
             iteration < MAX_ITERATIONS;
             iteration++) {
            boolean swingCorrected = constraint.projectSwing(
                    direction,
                    scratch.restDirection,
                    boneOrientation,
                    scratch.constraintRight
            );
            boolean safetyCorrected = constraint.enabled()
                    && projectMaximumSwing(
                    direction,
                    scratch.restDirection,
                    maximumCosine,
                    maximumSine,
                    scratch.localDirection
            );
            if (swingCorrected || safetyCorrected) {
                metrics.recordConstraintProjection();
            }
            boolean collisionCorrected =
                    collisions.project(
                            direction,
                            scratch.collision,
                            COLLISION_PASSES_PER_ITERATION
                    );
            if (collisionCorrected) {
                metrics.recordCollisionProjection();
            }
            corrected |= swingCorrected
                    || safetyCorrected
                    || collisionCorrected;
            if (!swingCorrected
                    && !safetyCorrected
                    && !collisionCorrected) {
                break;
            }
        }
        return corrected;
    }

    static float maximumSwing(
            PhysicsSolverLayout.Node node,
            float runtimeSafetyScale
    ) {
        float scale = Float.isFinite(runtimeSafetyScale)
                ? Math.max(1.0E-6F, runtimeSafetyScale)
                : 1.0F;
        float geometric = Math.min(
                SpringBoneMath.MAX_ANGLE,
                node.kinematics().safeAngle()
        ) * node.decision().profile().angleScale();
        float displacement = SpringBoneMath.MAX_TIP_DISPLACEMENT
                * node.decision().profile().tipDisplacementScale()
                / (node.kinematics().leverArm() * scale);
        return Math.max(0.0F, Math.min(geometric, displacement));
    }

    private static boolean projectMaximumSwing(
            Vector3f direction,
            Vector3f restDirection,
            float maximumCosine,
            float maximumSine,
            Vector3f tangent
    ) {
        float dot = Math.max(
                -1.0F,
                Math.min(1.0F, direction.dot(restDirection))
        );
        if (dot >= maximumCosine) {
            return false;
        }
        tangent.set(direction).fma(-dot, restDirection);
        if (tangent.lengthSquared() <= SpringBoneMath.EPSILON) {
            if (Math.abs(restDirection.y) < 0.90F) {
                tangent.set(0.0F, 1.0F, 0.0F)
                        .cross(restDirection);
            } else {
                tangent.set(1.0F, 0.0F, 0.0F)
                        .cross(restDirection);
            }
        }
        tangent.normalize();
        direction.set(restDirection)
                .mul(maximumCosine)
                .fma(maximumSine, tangent)
                .normalize();
        return true;
    }
}
