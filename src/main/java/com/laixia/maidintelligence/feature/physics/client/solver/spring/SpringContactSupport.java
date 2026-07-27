package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import org.joml.Vector3f;

/**
 * Lets a collider hold a part up instead of merely refusing to let it through.
 *
 * <p>Position projection alone cannot do that. A steady force — gravity, most
 * of the time — adds one frame's travel towards the collider every frame, and
 * the projection takes exactly that back out, so a part resting on a surface
 * trembles by the amount that force covers in a frame for as long as the pose
 * is held. Nothing about the situation changes between frames, which is why it
 * never settles: the projection answers where the part is, and the problem is
 * what is still pushing it.
 *
 * <p>Real contact answers the push. The surface supplies whatever normal force
 * cancels the component pressing into it, and the part sits still under a load
 * it is fully supporting. That is what this does, and the normal it needs is
 * already implied by the correction: a projection can only move a segment away
 * from the surface it hit, so the direction it moved it is the direction the
 * surface pushes.
 *
 * <p>Only the component pressing inwards is cancelled, so a part on a slope
 * still slides and one lifted away is released immediately. Support fades
 * instead of vanishing the moment a frame passes without a correction, because
 * a fully supported part is precisely one there is nothing left to correct —
 * dropping it there would restart the tremble it exists to prevent — but it
 * fades fast enough that a collider moving out from under a part drops it
 * rather than leaving it hanging.
 *
 * <p>It also builds over several frames of contact rather than arriving whole
 * with the first correction. A part still on its way onto a surface is corrected
 * on the frame it arrives, and supporting it there would freeze it at first
 * touch — the cloth would stop short of the body it is meant to drape over.
 * Resting contact is the case that needs answering, and what distinguishes it is
 * that it persists.
 */
final class SpringContactSupport {
    /** How long support outlives the last correction that established it. */
    private static final float FADE_SECONDS = 0.30F;
    /** Share of support one frame of contact earns. */
    private static final float RISE = 0.2F;
    private static final float EPSILON = 1.0E-6F;
    private static final float FLOOR = 1.0E-3F;

    private SpringContactSupport() {
    }

    static void record(
            int drivenSlot,
            float dt,
            SpringBoneState state,
            SpringBoneScratch scratch
    ) {
        Vector3f normal = state.contactNormals[drivenSlot];
        if (scratch.collisionCorrected
                && scratch.collisionNormal.lengthSquared() > EPSILON) {
            float support = state.contactSupport[drivenSlot];
            if (support <= 0.0F) {
                normal.set(scratch.collisionNormal).normalize();
            } else {
                // Averaging keeps a wandering contact point from swinging the
                // surface around between frames.
                normal.lerp(
                        scratch.collisionNormal.normalize(),
                        0.5F
                );
                if (normal.lengthSquared() <= EPSILON) {
                    normal.set(scratch.collisionNormal);
                }
                normal.normalize();
            }
            state.contactSupport[drivenSlot] = Math.min(
                    1.0F,
                    support + RISE
            );
            return;
        }
        float support = state.contactSupport[drivenSlot];
        if (support <= 0.0F) {
            return;
        }
        if (!Float.isFinite(dt) || dt <= 0.0F) {
            return;
        }
        float faded = support * (float) Math.exp(
                -Math.min(dt, 0.1F) / FADE_SECONDS
        );
        if (faded <= FLOOR) {
            state.contactSupport[drivenSlot] = 0.0F;
            normal.zero();
        } else {
            state.contactSupport[drivenSlot] = faded;
        }
    }

    /**
     * Removes the part of {@code force} that presses into the surface the
     * segment was last pushed off, scaled by how sure of that surface we are.
     */
    static void cancelInward(
            int drivenSlot,
            SpringBoneState state,
            Vector3f force
    ) {
        float support = state.contactSupport[drivenSlot];
        if (support <= 0.0F) {
            return;
        }
        Vector3f normal = state.contactNormals[drivenSlot];
        if (normal.lengthSquared() <= EPSILON) {
            return;
        }
        float inward = force.dot(normal);
        if (inward >= 0.0F) {
            return;
        }
        force.fma(-inward * support, normal);
    }
}
