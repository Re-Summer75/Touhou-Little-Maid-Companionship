package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.laixia.maidintelligence.feature.physics.client.solver.SecondaryMotionConstraint;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.SwingRange;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.PreparedCollisionProxySet;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Alternates swing and collision projection until stable.
 */
final class SpringConstraintProjector {
    private static final int MAX_ITERATIONS = 4;
    private static final int COLLISION_PASSES_PER_ITERATION = 24;
    /**
     * How far a projected segment may travel between two committed frames. A
     * hard constraint solves for the legal pose in one step, which reads as a
     * teleport whenever the violation is large — an animation cutting a
     * collider across a strand, a form swap, or a model coming into view
     * already overlapped. Spreading that over a few frames reaches the same
     * pose without the jump.
     *
     * <p>The budget deliberately measures the frame-over-frame result rather
     * than the work done inside one solve. Swing and collision take turns and
     * routinely undo each other by large amounts while settling on a pose that
     * barely moved; charging them for that would cap how hard either bound can
     * pull and leave the segment permanently short of the surface.
     *
     * <p>It is set well above anything a spring produces on its own. A segment
     * in contact is corrected every single frame, so a tight budget would also
     * throttle it following an animation that is moving legitimately fast —
     * lag, then catch-up, which is its own kind of stutter. Buzzing contact is
     * dealt with where it originates instead.
     */
    private static final float MAX_CORRECTION_RATE = 40.0F;

    private SpringConstraintProjector() {
    }

    static boolean project(
            SecondaryMotionConstraint constraint,
            Quaternionf boneOrientation,
            PreparedCollisionProxySet collisions,
            Vector3f direction,
            float maximumSwing,
            Vector3f committedDirection,
            float maximumCorrection,
            SpringBoneScratch scratch,
            SpringBoneMetrics metrics
    ) {
        boolean corrected = false;
        scratch.collisionCorrected = false;
        scratch.collisionNormal.zero();
        float maximumCosine = (float) Math.cos(maximumSwing);
        float maximumSine = (float) Math.sin(maximumSwing);
        for (int iteration = 0;
             iteration < MAX_ITERATIONS;
             iteration++) {
            boolean swingCorrected = constraint.projectSwing(
                    direction,
                    scratch.authoredRestDirection,
                    boneOrientation,
                    scratch.constraintRight
            );
            boolean safetyCorrected = constraint.enabled()
                    && projectMaximumSwing(
                    direction,
                    scratch.authoredRestDirection,
                    maximumCosine,
                    maximumSine,
                    scratch.localDirection
            );
            if (swingCorrected || safetyCorrected) {
                metrics.recordConstraintProjection();
            }
            scratch.collisionStart.set(direction);
            boolean collisionCorrected =
                    collisions.project(
                            direction,
                            scratch.collision,
                            COLLISION_PASSES_PER_ITERATION
                    );
            if (collisionCorrected) {
                metrics.recordCollisionProjection();
                scratch.collisionCorrected = true;
                scratch.collisionNormal.add(
                        direction.x() - scratch.collisionStart.x(),
                        direction.y() - scratch.collisionStart.y(),
                        direction.z() - scratch.collisionStart.z()
                );
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
        if (corrected) {
            limitCorrection(committedDirection, direction, maximumCorrection);
        }
        return corrected;
    }

    /**
     * Distance a projected segment may travel this step. A repeated render of
     * one animation frame carries no budget and therefore cannot move.
     */
    static float maximumCorrection(float dt) {
        return Float.isFinite(dt) && dt > 0.0F
                ? MAX_CORRECTION_RATE * Math.min(dt, 0.1F)
                : 0.0F;
    }

    /**
     * Walks {@code direction} back toward {@code start} until the step fits
     * {@code maximumAngle}. The chord interpolation undershoots the arc, which
     * only makes the limit safer, and whatever violation is left over is
     * projected again next frame.
     */
    private static void limitCorrection(
            Vector3f start,
            Vector3f direction,
            float maximumAngle
    ) {
        float dot = Math.max(
                -1.0F,
                Math.min(1.0F, start.dot(direction))
        );
        float angle = (float) Math.acos(dot);
        if (angle <= maximumAngle) {
            return;
        }
        float ratio = maximumAngle / angle;
        direction.set(
                start.x + (direction.x - start.x) * ratio,
                start.y + (direction.y - start.y) * ratio,
                start.z + (direction.z - start.z) * ratio
        );
        if (direction.lengthSquared() <= SpringBoneMath.EPSILON) {
            direction.set(start);
        } else {
            direction.normalize();
        }
    }

    static float maximumSwing(
            PhysicsSolverLayout.Node node,
            float runtimeSafetyScale
    ) {
        return SwingRange.maximum(node, runtimeSafetyScale);
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
