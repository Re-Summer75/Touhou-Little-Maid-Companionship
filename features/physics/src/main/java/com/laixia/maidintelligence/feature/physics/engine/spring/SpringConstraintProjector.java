package com.laixia.maidintelligence.feature.physics.engine.spring;


import com.laixia.maidintelligence.feature.physics.layout.SecondaryMotionConstraint;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.layout.SwingRange;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProjection;
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
     *
     * <p>Not the place to catch an intermittent ejection, though one exists: on
     * winefox_magical, hatsidefront2 leaves the projection idle for a hundred and
     * four of a hundred and twenty frames, then moves 0.394, 0.414 and 0.156 rad
     * on three consecutive frames and repeats on a thirty-frame cycle. Tightening
     * this to catch that was measured and rejected. Six radians a second lets a
     * gust press cloth through the body it is blown against; twelve survives the
     * coverage but only halves the ejection, and it costs contacts everywhere —
     * winefox rising from 813 to 920, winefox_little from 416 to 656 — because a
     * projection kept under its budget is a projection that did not finish. The
     * budget is a smoothing device for a violation that is already legitimate,
     * and an ejection that should not have happened has to be stopped where it is
     * decided.
     */
    private static final float MAX_CORRECTION_RATE = 40.0F;

    private SpringConstraintProjector() {
    }

    static boolean project(
            SecondaryMotionConstraint constraint,
            Quaternionf boneOrientation,
            CollisionProjection collisions,
            Vector3f direction,
            float maximumSwing,
            Vector3f committedDirection,
            float maximumCorrection,
            SpringBoneScratch scratch,
            SpringBoneMetrics metrics
    ) {
        boolean corrected = false;
        boolean settled = false;
        boolean swingDeadlocked = false;
        scratch.collisionCorrected = false;
        scratch.swingCorrected = false;
        scratch.collisionNormal.zero();
        scratch.collision.setUnresolved(false);
        // A node with no collision pass at all lies against nothing by default.
        scratch.collision.setRestClearance(Float.MAX_VALUE);
        collisions.beginProjectionSeries();
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
                scratch.swingCorrected = true;
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
            /*
             * A swing limiter is idempotent on its own: it snaps onto its own
             * boundary, so a second look finds the direction already legal.
             * Still objecting on the final pass while a collider objects too
             * means each is pushing the direction back out of the other's
             * bound. The collision loop reports its own passes converging, so
             * it cannot see this: the two agree individually and disagree only
             * across the alternation between them.
             */
            swingDeadlocked =
                    (swingCorrected || safetyCorrected) && collisionCorrected;
            corrected |= swingCorrected
                    || safetyCorrected
                    || collisionCorrected;
            if (!swingCorrected
                    && !safetyCorrected
                    && !collisionCorrected) {
                settled = true;
                break;
            }
        }
        /*
         * Leaving the loop with the last iteration still correcting means the
         * bounds never agreed, not that four passes were too few: each is a
         * hard snap onto its own boundary, so a set with a common solution
         * reaches it and the next pass finds nothing to do. The collision loop
         * reports its own failure to converge, but two swing limiters
         * deadlocking against each other was invisible, and that is a period-2
         * cycle the damper never saw — measured on winefox_momo as bowL9
         * flipping 0.270 rad every frame indefinitely, its reversal streak past
         * 200 and its damping still nought because nothing ever reported the
         * set as unsatisfiable.
         *
         * <p>Reported only for a swing pair deadlock, never for a collider that
         * is merely still working. Cloth leaning on a body under wind corrects
         * every frame without being stuck, and damping that is what lets a gust
         * press it through the surface it is leaning on — the failure this was
         * caught by twice. Damping still needs the reversal streak the damper
         * counts for itself on top of this.
         */
        /*
         * <p>Not reported for a swing limiter working alone, though that is also
         * a segment its own cone keeps pushing back. Tried, and it changes
         * nothing: the damper needs a reversal streak of its own on top of this,
         * and a cone-wall stick-slip reverses once per release — every thirtieth
         * frame on winefox_magical's hatsidefront2 — so the streak is cleared by
         * the quiet frames in between long before it counts. That restraint is
         * correct. An occasional 0.373 rad lurch is not the sustained buzz this
         * damper exists for, and dulling a contactless segment on the strength of
         * its cone alone would reach far past the case at hand.
         */
        if (!settled && swingDeadlocked) {
            scratch.collision.setUnresolved(true);
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
