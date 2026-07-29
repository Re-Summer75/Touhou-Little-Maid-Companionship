package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import org.joml.Vector3f;

/**
 * Settles a segment that two constraints cannot both satisfy.
 *
 * <p>A projection solves for a legal pose, but a squeezed segment has none: a
 * seated pose can close a gap below the thickness of what hangs in it, and two
 * colliders then demand opposite things. Relaxation answers whichever spoke
 * last, the ranking that picks it is redone from the live pose every frame, and
 * the segment ends up hopping between two surfaces at frame rate. That reads as
 * a buzz, and the rate limit only bounds how far each hop travels.
 *
  * <p>The signature of that state is a correction that reverses every frame, so
 * this watches for it and, while it lasts, applies successively less of what
 * the projection asks for. Alternating demands then converge on the pose in
 * between — overlapping both colliders a little, which is what a squeeze
 * physically is — instead of oscillating between them.
 *
 * <p>Reversal alone does not identify it, because the pose a segment is drawn
 * back towards moves. Wind leans it, and gusts wander in direction, so cloth
 * merely leaning against a body has its correction reverse as readily as one
 * with nowhere to go — and damping that let a gust press cloth through the
 * body it was leaning on. The relaxation itself settles the question: a single
 * collider is satisfied in one pass and stays satisfied, while colliders
 * wanting opposite things never converge however many passes they are given.
 * Requiring both keeps a squeeze damped without reading a moving target as one.
 */
final class SpringProjectionDamper {
    /**
     * How long a settled segment takes to regain full response.
     *
     * <p>Recovery has to be slower than the streak needed to re-detect a squeeze,
     * or damping simply oscillates: it reaches the floor, releases far enough for
     * the cycle to restart, and clamps down again. Traced on winefox_momo's
     * bowL9, damping ran 1.00 down to 0.67 and straight back up every six frames
     * while the segment kept covering ground — travel per unit of progress on
     * that one segment was an order of magnitude above any other model's.
     *
     * <p>Held against the other end deliberately, because this also decides how
     * long a segment stays dulled after a squeeze genuinely clears. A gust or a
     * limb leaving should find the cloth responsive again quickly, so this is
     * long enough to outlast the three-frame streak by a wide margin and no
     * longer.
     */
    private static final float RECOVERY_SECONDS = 0.25F;
    /**
     * Consecutive settled frames before damping starts to release.
     *
     * <p>Releasing the moment a frame comes back clean is what made damping
     * oscillate: the segment needs only three reversing frames to be judged stuck
     * again, so a squeeze that is still there gets re-detected as fast as it is
     * forgiven, and damping runs up and down instead of holding. Waiting for a
     * few quiet frames first costs nothing when a squeeze has really cleared and
     * stops the cycle when it has not — traced on winefox_momo's bowL9, travel
     * per unit of progress fell from 42412 to 845.
     */
    private static final int SETTLED_BEFORE_RELEASE = 6;
    /** Response floor, low enough to stop a reversal within a few frames. */
    private static final float MINIMUM_RESPONSE = 0.12F;
    private static final float RISE = 0.5F;
    /** Below this a correction is numerical noise rather than a demand. */
    private static final float SIGNIFICANT = 1.0E-4F;
    /**
     * Consecutive reversing frames before the segment counts as stuck.
     *
     * <p>A single reversal is not evidence of anything: a collider that sweeps
     * across a segment and back — a leg while walking — reverses once per
     * stride, and damping on that basis is what makes a skirt stop reacting to
     * being kicked, which is the one thing it most needs to react to. An
     * unsatisfiable squeeze is a period-2 cycle instead: it reverses on every
     * single frame, so requiring a streak separates the two without needing to
     * measure anything about the collider.
     */
    private static final int SUSTAINED = 3;
    /**
     * Reversals within {@link #WINDOW} frames that count as stuck.
     *
     * <p>A consecutive streak only recognises a period-2 cycle. Longer cycles
     * exist and are worse: winefox_momo's bowL9 settles into a period-3 orbit of
     * 0.039, 0.136 and 0.096 rad, whose middle step runs with the previous one,
     * so the streak resets every third frame and never reaches {@link #SUSTAINED}
     * however long the orbit persists. Damping sat at 0.50 while the tip shook
     * 4.1 px per frame — the largest visible buzz left in any model.
     *
     * <p>Counting over a window catches any short cycle without loosening what a
     * single reversal means: a limb sweeping across and back still reverses only
     * once or twice in this many frames, well under the threshold.
     */
    private static final int WINDOW = 8;
    private static final int WINDOW_REVERSALS = 4;

    private SpringProjectionDamper() {
    }

    /** Shifts one frame into the history, dropping anything past the window. */
    private static int record(int history, boolean reversed) {
        return ((history << 1) | (reversed ? 1 : 0)) & ((1 << WINDOW) - 1);
    }

    static void apply(
            int drivenSlot,
            Vector3f before,
            Vector3f after,
            boolean unresolved,
            float dt,
            SpringBoneState state
    ) {
        Vector3f previous = state.projectionCorrections[drivenSlot];
        float cx = after.x - before.x;
        float cy = after.y - before.y;
        float cz = after.z - before.z;
        if (cx * cx + cy * cy + cz * cz <= SIGNIFICANT * SIGNIFICANT) {
            /*
             * Nothing was asked of the projection, which is a quiet frame rather
             * than a settled squeeze. It counts towards release on the same run
             * as any other quiet frame; releasing outright here would reopen the
             * cycle the moment a squeeze paused for one frame.
             */
            state.projectionReversals[drivenSlot] =
                    record(state.projectionReversals[drivenSlot], false);
            int quiet = ++state.projectionQuiet[drivenSlot];
            if (quiet >= SETTLED_BEFORE_RELEASE) {
                state.projectionDamping[drivenSlot] = decay(
                        state.projectionDamping[drivenSlot],
                        dt
                );
            }
            previous.zero();
            return;
        }
        boolean reversed = previous.lengthSquared()
                > SIGNIFICANT * SIGNIFICANT
                && cx * previous.x + cy * previous.y + cz * previous.z < 0.0F;
        int history = record(state.projectionReversals[drivenSlot], reversed);
        state.projectionReversals[drivenSlot] = history;
        int quiet = reversed ? 0 : state.projectionQuiet[drivenSlot] + 1;
        state.projectionQuiet[drivenSlot] = quiet;
        /*
         * Either a run of reversals or enough of them close together. The streak
         * still catches the plain period-2 flip on the frame it becomes clear;
         * the window additionally catches longer orbits, whose reversals are
         * frequent but not consecutive.
         */
        int recent = Integer.bitCount(history);
        boolean stuck = unresolved
                && (Integer.numberOfTrailingZeros(~history) >= SUSTAINED
                        || recent >= WINDOW_REVERSALS);
        boolean releasing = quiet >= SETTLED_BEFORE_RELEASE && recent == 0;
        float damping;
        if (stuck) {
            damping = Math.min(
                    1.0F,
                    state.projectionDamping[drivenSlot] + RISE
            );
        } else if (releasing) {
            damping = decay(state.projectionDamping[drivenSlot], dt);
        } else {
            damping = state.projectionDamping[drivenSlot];
        }
        state.projectionDamping[drivenSlot] = damping;
        // Detection reads the demand, not the damped answer to it.
        previous.set(cx, cy, cz);
        if (damping <= 0.0F) {
            return;
        }
        float response = 1.0F - (1.0F - MINIMUM_RESPONSE) * damping;
        after.set(
                before.x + cx * response,
                before.y + cy * response,
                before.z + cz * response
        );
        if (after.lengthSquared() <= SpringBoneMath.EPSILON) {
            after.set(before);
        } else {
            after.normalize();
        }
    }

    private static float decay(float damping, float dt) {
        if (damping <= 0.0F) {
            return 0.0F;
        }
        if (!Float.isFinite(dt) || dt <= 0.0F) {
            return damping;
        }
        float next = damping * (float) Math.exp(
                -Math.min(dt, 0.1F) / RECOVERY_SECONDS
        );
        return next <= 1.0E-3F ? 0.0F : next;
    }
}
