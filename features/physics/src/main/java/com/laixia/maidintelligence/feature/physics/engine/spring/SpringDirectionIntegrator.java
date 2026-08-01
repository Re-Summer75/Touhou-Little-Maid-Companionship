package com.laixia.maidintelligence.feature.physics.engine.spring;


import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.engine.PhysicsMath;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Updates normalized Verlet directions without applying constraints.
 */
final class SpringDirectionIntegrator {
    private SpringDirectionIntegrator() {
    }

    static void prepareRestDirection(
            PhysicsSolverLayout.Node node,
            Quaternionf boneBaseOrientation,
            Vector3f modelPoseDrive,
            float turbulenceStep,
            SpringBoneState state,
            SpringBoneScratch scratch
    ) {
        int slot = node.drivenSlot();
        node.axisInto(scratch.boneAxis);
        boneBaseOrientation.transform(
                scratch.boneAxis,
                scratch.authoredRestDirection
        ).normalize();
        state.integration.restDirections[slot]
                .set(scratch.authoredRestDirection);
        // Wind leans the spring; the authored pose stays its equilibrium.
        scratch.restDirection.set(scratch.authoredRestDirection);
        SpringProceduralPoseDriver.apply(
                node,
                modelPoseDrive,
                turbulenceStep,
                state,
                scratch
        );
        if (!state.integration.initialized[slot]) {
            // A bone entering the solver mid-gust starts at the authored pose
            // and leans in over the following frames instead of snapping.
            state.integration.currentDirections[slot]
                    .set(scratch.authoredRestDirection);
            state.integration.previousDirections[slot]
                    .set(scratch.authoredRestDirection);
            state.integration.initialized[slot] = true;
        }
    }

    static boolean integrate(
            PhysicsSolverLayout.Node node,
            Vector3f modelAcceleration,
            Vector3f animationAcceleration,
            float yawRate,
            float dt,
            boolean paused,
            boolean preserveAuthoredPose,
            SpringBoneState state,
            SpringBoneScratch scratch
    ) {
        int slot = node.drivenSlot();
        PhysicsBoneSelectionPlan.SpringProfile profile =
                node.decision().profile();
        Vector3f current = state.integration.currentDirections[slot];
        Vector3f previous = state.integration.previousDirections[slot];
        boolean stepped = !paused && dt > SpringBoneMath.EPSILON;
        if (!stepped) {
            scratch.nextDirection.set(current);
            return false;
        }

        scratch.nextDirection.set(current);
        float drag = PhysicsMath.clamp(
                SpringBoneMath.DRAG * profile.dragScale(),
                0.0F,
                0.95F
        );
        float retention = SpringBoneMath.dragRetention(drag, dt);
        float previousDt = state.integration.previousDeltaSeconds[slot];
        float stepRatio = previousDt > SpringBoneMath.EPSILON
                ? PhysicsMath.clamp(dt / previousDt, 0.25F, 4.0F)
                : 1.0F;
        scratch.carriedVelocity.set(
                (current.x() - previous.x()) * retention * stepRatio,
                (current.y() - previous.y()) * retention * stepRatio,
                (current.z() - previous.z()) * retention * stepRatio
        );
        /*
         * A segment resting on a surface may not use its carried velocity to climb
         * off it. The force cancellation below keeps such a segment from pressing
         * in, and leaving the outward half untouched let it be launched instead.
         */
        SpringContactSupport.cancelOutward(
                slot, state, scratch.carriedVelocity
        );
        scratch.nextDirection.add(scratch.carriedVelocity);
        /*
         * Mass enters through k/m; frame acceleration stays an acceleration and
         * must not be multiplied by mass. Only added mass
         * slows the restoring pull: letting a light part pull harder than the
         * authored baseline would overshoot the collision projection between
         * frames instead of looking livelier.
         */
        float restoringRate = (float) (
                SpringBoneMath.STIFFNESS
                        * profile.stiffnessScale()
                        / Math.max(1.0F, profile.massScale())
        );
        float stiffness = restoringRate * dt;
        scratch.nextDirection.add(
                scratch.restDirection.x() * stiffness,
                scratch.restDirection.y() * stiffness,
                scratch.restDirection.z() * stiffness
        );
        float inertia =
                SpringBoneMath.INERTIA_GAIN * profile.inertiaScale();
        float turn = SpringBoneMath.TURN_GAIN * profile.turnScale();
        /*
         * Everything the surroundings do to the part, gathered before any of it
         * reaches the pose, because a surface the part rests on answers all of
         * it at once. Atmosphere belongs here even though it arrives as a lean
         * rather than a force: it leans the spring by displacing what the spring
         * pulls towards, and a gust blowing inwards puts that target inside the
         * body. Projection alone cannot settle a target it can never reach — it
         * keeps answering the same violation for as long as the weather lasts,
         * and the cloth rides at whatever depth pull and push balance at. Being
         * held against a collider is the one thing a gust must not be able to
         * turn into being pressed through it.
         *
         * <p>The constrained production path rejects constant gravity torque.
         * Otherwise gravity and the spring define a second equilibrium below
         * the authored silhouette, making every idle raised strand sag forever.
         * Motion acceleration, turning, and gust bias still provide weight and
         * lag. The unconstrained oracle retains the legacy gravity response.
         *
         * <p>The restoring pull towards the authored pose is deliberately left
         * out. That pose is the equilibrium, overlap and all — an author may seat
         * a hem inside a hip — and cancelling it would stop the part returning to
         * where the model was drawn.
         */
        float gravityResponse = preserveAuthoredPose ? 0.0F : 1.0F;
        scratch.appliedForce.set(scratch.poseDriveBias).mul(stiffness);
        scratch.appliedForce.add(
                ((modelAcceleration.x() + animationAcceleration.x())
                        * -inertia + yawRate * turn) * dt,
                ((modelAcceleration.y() + animationAcceleration.y())
                        * -inertia
                        - SpringBoneMath.GRAVITY_POWER
                        * profile.gravityScale()
                        * gravityResponse) * dt,
                (modelAcceleration.z() + animationAcceleration.z())
                        * -inertia * dt
        );
        SpringContactSupport.cancelInward(slot, state, scratch.appliedForce);
        scratch.nextDirection.add(scratch.appliedForce);
        if (scratch.nextDirection.lengthSquared()
                > SpringBoneMath.EPSILON) {
            scratch.nextDirection.normalize();
        }
        return true;
    }

    static void commit(
            int slot,
            boolean stepped,
            boolean corrected,
            float dt,
            SpringBoneState state,
            SpringBoneScratch scratch
    ) {
        Vector3f current = state.integration.currentDirections[slot];
        Vector3f previous = state.integration.previousDirections[slot];
        if (stepped && scratch.nextDirection.lengthSquared()
                > SpringBoneMath.EPSILON) {
            if (corrected) {
                /*
                 * The whole step is erased rather than only the component the
                 * constraint objected to, and that is load-bearing rather than
                 * merely conservative. SpringProjectionDamper identifies an
                 * unsatisfiable squeeze by a correction that reverses on
                 * consecutive frames, which presumes contact leaves no velocity
                 * of its own. Keeping the tangential sweep makes an ordinary
                 * sustained contact reverse just as often; the damper then reads
                 * that as a squeeze and drops response to its floor, and cloth
                 * stops reacting to a limb sweeping through it. Measured on the
                 * swept-leg case: 0.17 rad falling to 0.097 as more of the step
                 * was preserved.
                 */
                previous.set(scratch.nextDirection);
            } else {
                previous.set(current);
            }
            current.set(scratch.nextDirection);
            state.integration.previousDeltaSeconds[slot] = dt;
        } else if (corrected) {
            current.set(scratch.nextDirection);
            previous.set(scratch.nextDirection);
        }
    }
}
