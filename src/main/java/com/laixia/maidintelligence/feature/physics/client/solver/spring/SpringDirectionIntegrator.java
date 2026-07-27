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
        // Atmosphere leans the spring without displacing what it pulls toward.
        scratch.nextDirection.fma(stiffness, scratch.poseDriveBias);
        float inertia =
                SpringBoneMath.INERTIA_GAIN * profile.inertiaScale();
        float turn = SpringBoneMath.TURN_GAIN * profile.turnScale();
        /*
         * Gravity is resolved against the authored pose, not against the
         * world. The author already drew the part where gravity had settled
         * it — a skirt hanging, a sash lying along a hip — so pulling world-
         * down on top of that pose applies gravity a second time, and the
         * spring settles where the two balance instead of where the model was
         * drawn. A raised strand sags, a sideways ribbon droops, and no amount
         * of retuning the strength fixes it, because the displacement is what
         * a constant force does to a spring.
         *
         * Only the component along the rest direction acts unconditionally:
         * direction is normalized afterwards, so that component changes how
         * firmly the part is drawn home without moving where home is. Parts
         * hanging downwards are pulled home harder, parts raised against
         * gravity more softly, which is the weight gravity should lend them.
         * The perpendicular component is the one that displaces the pose, so
         * it fades in with how far the segment already is from rest: a strand
         * thrown out by wind or a kick still falls the way gravity really
         * points, while one sitting at rest is left exactly where it was
         * drawn. It cannot run away either — it saturates at full gravity
         * while the restoring pull keeps growing with the angle.
         */
        float gravity = SpringBoneMath.GRAVITY_POWER * profile.gravityScale();
        Vector3f rest = scratch.restDirection;
        float alongRest = -rest.y() * gravity;
        float displaced = Mth.clamp(1.0F - current.dot(rest), 0.0F, 1.0F);
        float seated = 1.0F - displaced;
        scratch.nextDirection.add(
                ((modelAcceleration.x() + animationAcceleration.x())
                        * -inertia + yawRate * turn
                        + rest.x() * alongRest * seated) * dt,
                ((modelAcceleration.y() + animationAcceleration.y())
                        * -inertia
                        + rest.y() * alongRest * seated
                        - gravity * displaced) * dt,
                ((modelAcceleration.z() + animationAcceleration.z())
                        * -inertia
                        + rest.z() * alongRest * seated) * dt
        );
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
