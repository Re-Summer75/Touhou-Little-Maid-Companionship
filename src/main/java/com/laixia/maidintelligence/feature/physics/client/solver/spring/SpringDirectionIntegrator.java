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
            SpringBoneState state,
            SpringBoneScratch scratch
    ) {
        int slot = node.drivenSlot();
        node.axisInto(scratch.boneAxis);
        boneBaseOrientation.transform(
                scratch.boneAxis,
                scratch.restDirection
        ).normalize();
        if (!state.initialized[slot]) {
            state.currentDirections[slot].set(scratch.restDirection);
            state.previousDirections[slot].set(scratch.restDirection);
            state.initialized[slot] = true;
        }
    }

    static boolean integrate(
            PhysicsSolverLayout.Node node,
            Vector3f modelAcceleration,
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
        float stiffness = (float) (
                SpringBoneMath.STIFFNESS * profile.stiffnessScale() * dt
        );
        scratch.nextDirection.add(
                scratch.restDirection.x() * stiffness,
                scratch.restDirection.y() * stiffness,
                scratch.restDirection.z() * stiffness
        );
        float inertia =
                SpringBoneMath.INERTIA_GAIN * profile.inertiaScale();
        float turn = SpringBoneMath.TURN_GAIN * profile.turnScale();
        scratch.nextDirection.add(
                (modelAcceleration.x() * -inertia + yawRate * turn) * dt,
                (modelAcceleration.y() * -inertia
                        - SpringBoneMath.GRAVITY_POWER
                        * profile.gravityScale()) * dt,
                modelAcceleration.z() * -inertia * dt
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
