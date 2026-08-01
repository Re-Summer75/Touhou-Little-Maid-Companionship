package com.laixia.maidintelligence.feature.physics.engine.spring;


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
    /**
     * How long support outlives contact while it is unclear the surface has gone.
     *
     * <p>Only the first few frames after a collider stops touching are spent at
     * this rate, since a graze reads clear for a frame or two while still resting.
     */
    private static final float FADE_SECONDS = 0.30F;
    /** Share of support one frame of contact earns. */
    private static final float RISE = 0.2F;
    /**
     * How long support survives once no collider lies against the segment at all.
     *
     * <p>Short enough that the spring is free again within a couple of frames,
     * since there is nothing left for the cancellation to represent, and not
     * instant: a contact grazing in and out across a frame boundary would then
     * flicker the spring's restoring force on and off.
     */
    private static final float RELEASE_SECONDS = 0.05F;
    /** Consecutive frames clear of every collider before support is released. */
    private static final int FREE_FRAMES = 4;
    /**
     * Clearance within which support is kept alive, in blocks.
     *
     * <p>Half a pixel, wide enough that a segment lying on a surface still reads
     * as resting on the frames its contact grazes: such a segment hovers either
     * side of zero clearance by a fraction of a pixel, and a narrower band called
     * it free every few frames, which is the decay-rebuild cycle again.
     */
    private static final float HOLD_BAND = 1.0F / 32.0F;
    private static final float EPSILON = 1.0E-6F;
    private static final float FLOOR = 1.0E-3F;

    private SpringContactSupport() {
    }

    /** Drops a contact whose geometry is being crossed during lock recovery. */
    static void clear(int drivenSlot, SpringBoneState state) {
        state.contact.normals[drivenSlot].zero();
        state.contact.support[drivenSlot] = 0.0F;
        state.contact.hold[drivenSlot] = 0;
    }

    static void record(
            int drivenSlot,
            float dt,
            SpringBoneState state,
            SpringBoneScratch scratch
    ) {
        Vector3f normal = state.contact.normals[drivenSlot];
        if (scratch.collisionCorrected
                && scratch.collisionNormal.lengthSquared() > EPSILON) {
            float support = state.contact.support[drivenSlot];
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
            state.contact.support[drivenSlot] = Math.min(
                    1.0F,
                    support + RISE
            );
            // A correction is contact, so any tally of free frames is stale.
            state.contact.hold[drivenSlot] = 0;
            return;
        }
        float support = state.contact.support[drivenSlot];
        if (support <= 0.0F) {
            return;
        }
        if (!Float.isFinite(dt) || dt <= 0.0F) {
            return;
        }
        /*
         * Decay is gated on whether a collider still lies against the segment,
         * which is asked of the projector rather than inferred from whether one
         * pushed. The two differ exactly where it matters: a segment settled on a
         * surface is pushed by nothing at all, so the push alone cannot tell
         * resting contact from a surface that has moved away.
         *
         * <p>Inferring it from the push cost both ways. Fading on uncorrected
         * frames — most frames, for anything at rest — bled support off a segment
         * that was still resting, which weakened the cancellation until the
         * segment sank in and one frame threw it back out: nine_tailed's Tail58
         * ran that loop at 1.00 down to 0.46 support, 0.095 rad in, 0.39 rad out.
         * And keeping support alive on a timer after contact ended left the spring
         * cancelled against a surface no longer there, which let winefox's FR1
         * wander 38 px off its equilibrium with the projector flat at zero.
         */
        /*
         * <p>Contact only stays the decay; it does not grant support of its own.
         * Letting it grant support was tried, to cure segments stuck at a partial
         * level, and it strands a segment short of the surface it is falling onto,
         * because support cancels the very force that would carry it the last of
         * the way — a hair strand hung 0.077 to 0.089 blocks off its mesh. Every
         * way of exempting the approach failed too: by clearance, since the same
         * band cannot be both wide enough to survive a graze and narrow enough to
         * withhold on approach, and by direction, since a segment already pinned
         * has a clearance that stops changing and so never reads as approaching.
         */
        float clearance = scratch.collision.restClearance();
        if (clearance < HOLD_BAND) {
            state.contact.hold[drivenSlot] = 0;
            return;
        }
        /*
         * Release waits for the surface to be gone several frames running. A
         * contact grazing across a frame boundary reads free for a frame while
         * still resting, and acting on that one frame restarts the loop above.
         */
        int free = Math.min(0, state.contact.hold[drivenSlot]) - 1;
        state.contact.hold[drivenSlot] = free;
        float seconds = free <= -FREE_FRAMES ? RELEASE_SECONDS : FADE_SECONDS;
        float faded = support * (float) Math.exp(
                -Math.min(dt, 0.1F) / seconds
        );
        if (faded <= FLOOR) {
            state.contact.support[drivenSlot] = 0.0F;
            normal.zero();
        } else {
            state.contact.support[drivenSlot] = faded;
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
        float support = state.contact.support[drivenSlot];
        if (support <= 0.0F) {
            return;
        }
        Vector3f normal = state.contact.normals[drivenSlot];
        if (normal.lengthSquared() <= EPSILON) {
            return;
        }
        float inward = force.dot(normal);
        if (inward >= 0.0F) {
            return;
        }
        force.fma(-inward * support, normal);
    }

    /**
     * Removes the part of {@code velocity} carrying a supported segment off its
     * surface, scaled by how sure of that surface we are.
     *
     * <p>Cancelling only the force leaves a segment being launched off the very
     * surface holding it up. Support rises the frame a correction lands, so more
     * of the load pressing inwards is cancelled from that frame on, and the
     * outward velocity accumulated before then is still in the step — nothing
     * balances it any more. Measured on winefox_magical's hatsidefront2: support
     * stepped 0.51 to 0.71 and the segment left in the same frame, 0.373 rad in
     * one step and 1.13 px further from the collider than it had been, then drifted
     * back as support faded, touched, and went again on a thirty-frame cycle — the
     * period of {@link #FADE_SECONDS}, not of anything physical.
     *
     * <p>Only the outward component and only in proportion to support, so a part
     * genuinely thrown clear by a moving collider still leaves: that arrives as
     * fresh velocity while support is still low, and a part fully supported is one
     * that should be resting, not climbing.
     */
    static void cancelOutward(
            int drivenSlot,
            SpringBoneState state,
            Vector3f velocity
    ) {
        float support = state.contact.support[drivenSlot];
        if (support <= 0.0F) {
            return;
        }
        Vector3f normal = state.contact.normals[drivenSlot];
        if (normal.lengthSquared() <= EPSILON) {
            return;
        }
        float outward = velocity.dot(normal);
        if (outward <= 0.0F) {
            return;
        }
        velocity.fma(-outward * support, normal);
    }
}
