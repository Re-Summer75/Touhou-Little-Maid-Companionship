package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import net.minecraft.util.Mth;
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
        // Wind leans the spring; the authored pose stays its equilibrium.
        scratch.restDirection.set(scratch.authoredRestDirection);
        SpringProceduralPoseDriver.apply(
                node,
                modelPoseDrive,
                turbulenceStep,
                state,
                scratch
        );
        if (!state.initialized[slot]) {
            // A bone entering the solver mid-gust starts at the authored pose
            // and leans in over the following frames instead of snapping.
            state.currentDirections[slot].set(scratch.authoredRestDirection);
            state.previousDirections[slot].set(scratch.authoredRestDirection);
            state.initialized[slot] = true;
        }
    }

    static boolean integrate(
            PhysicsSolverLayout.Node node,
            Vector3f modelAcceleration,
            Vector3f animationAcceleration,
            float yawRate,
            float dt,
            boolean paused,
            SpringBoneState state,
            SpringBoneScratch scratch
    ) {
        int slot = node.drivenSlot();
        PhysicsBoneSelectionPlan.SpringProfile profile =
                node.decision().profile();
        Vector3f current = state.currentDirections[slot];
        Vector3f previous = state.previousDirections[slot];
        boolean stepped = !paused && dt > SpringBoneMath.EPSILON;
        if (!stepped) {
            scratch.nextDirection.set(current);
            return false;
        }

        scratch.nextDirection.set(current);
        float drag = Mth.clamp(
                SpringBoneMath.DRAG * profile.dragScale(),
                0.0F,
                0.95F
        );
        float retention = SpringBoneMath.dragRetention(drag, dt);
        float previousDt = state.previousDeltaSeconds[slot];
        float stepRatio = previousDt > SpringBoneMath.EPSILON
                ? Mth.clamp(dt / previousDt, 0.25F, 4.0F)
                : 1.0F;
        scratch.nextDirection.add(
                (current.x() - previous.x()) * retention * stepRatio,
                (current.y() - previous.y()) * retention * stepRatio,
                (current.z() - previous.z()) * retention * stepRatio
        );
        /*
         * Mass enters through k/m; gravity and frame acceleration stay
         * accelerations and must not be multiplied by mass. Only added mass
         * slows the restoring pull: letting a light part pull harder than the
         * authored baseline would overshoot the collision projection between
         * frames instead of looking livelier.
         */
        float stiffness = (float) (
                SpringBoneMath.STIFFNESS
                        * profile.stiffnessScale()
                        / Math.max(1.0F, profile.massScale())
                        * dt
        );
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
         * <p>Gravity pulls world-down on every part at all times, so it also
         * displaces the authored pose: the spring settles where the pull and the
         * restoring force balance rather than where the model was drawn, and a
         * raised strand sags. Resolving it against the rest direction removes
         * that displacement, but it removes the sag that reads as weight along
         * with it, and the trade was judged the wrong way round.
         *
         * <p>The restoring pull towards the authored pose is deliberately left
         * out. That pose is the equilibrium, overlap and all — an author may seat
         * a hem inside a hip — and cancelling it would stop the part returning to
         * where the model was drawn.
         */
        scratch.appliedForce.set(scratch.poseDriveBias).mul(stiffness);
        scratch.appliedForce.add(
                ((modelAcceleration.x() + animationAcceleration.x())
                        * -inertia + yawRate * turn) * dt,
                ((modelAcceleration.y() + animationAcceleration.y())
                        * -inertia
                        - SpringBoneMath.GRAVITY_POWER
                        * profile.gravityScale()) * dt,
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
        Vector3f current = state.currentDirections[slot];
        Vector3f previous = state.previousDirections[slot];
        if (stepped && scratch.nextDirection.lengthSquared()
                > SpringBoneMath.EPSILON) {
            if (corrected) {
                previous.set(scratch.nextDirection);
            } else {
                previous.set(current);
            }
            current.set(scratch.nextDirection);
            state.previousDeltaSeconds[slot] = dt;
        } else if (corrected) {
            current.set(scratch.nextDirection);
            previous.set(scratch.nextDirection);
        }
    }
}
