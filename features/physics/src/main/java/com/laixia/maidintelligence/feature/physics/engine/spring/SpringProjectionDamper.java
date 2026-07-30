package com.laixia.maidintelligence.feature.physics.engine.spring;


import org.joml.Vector3f;

/**
 * Settles repeating projection cycles without suppressing ordinary contact.
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
 *
 * <p>A second cycle needs a separate signature. One collider can eject a segment,
 * the spring can pull it back over several uncorrected frames, and the same
 * collider can eject it again in the same direction. That correction never
 * reverses and each projection converges, so repeated correction after quiet gaps
 * is tracked independently. After six returns in half a second, colliders that
 * the latest response pushed into are suppressed until the segment genuinely
 * leaves them; the selected responder keeps ownership.
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
    /** Response floor used only for an unresolved multi-collider squeeze. */
    private static final float SQUEEZE_MINIMUM_RESPONSE = 0.12F;
    private static final float RISE = 0.5F;
    /** Below this a correction is numerical noise rather than a demand. */
    private static final float SIGNIFICANT = 1.0E-4F;
    /**
     * Small recurring corrections are a normal force, not a visible re-entry.
     *
     * <p>Wind leaning cloth onto a body produces intermittent sub-degree
     * corrections separated by clear frames. Counting those armed responder
     * suppression and let the same wind drive the cloth through the body. The
     * measured FM/FFM failures re-enter by several degrees at a time, so this
     * threshold keeps support contact while retaining the harmful cycles.
     */
    private static final float REENTRY_CORRECTION = 0.02F;
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
    /**
     * Frames of history kept for recognising a repeated re-entry cycle.
     *
     * <p>The correction does not reverse in this cycle: one collider ejects in
     * the same direction every time, then the spring pulls back silently until
     * the next ejection. What repeats is projection resuming after several quiet
     * frames. The short reversal window cannot see that, and requiring
     * {@code unresolved} cannot either because every individual ejection settles
     * in one pass. Measured in game on winefox's FFM1_1, which held a five-frame
     * cycle for twelve seconds — 12 corrections per 60 frames, ejected 15 px each
     * turn, offset running to 57 px, with damping flat at zero throughout.
     */
    private static final int SLOW_WINDOW = 30;
    /**
     * Re-entries within {@link #SLOW_WINDOW} frames that count as a cycle.
     *
     * <p>Six returns, which no one-off event produces. A limb sweeping across a
     * skirt and back enters twice; a segment returning six times in half a second
     * is orbiting.
     */
    private static final int SLOW_REENTRIES = 6;
    /** Even one clear frame separates recurring projection from steady contact. */
    private static final int REENTRY_GAP = 1;

    private SpringProjectionDamper() {
    }

    /** Shifts one frame into the history, dropping anything past the window. */
    private static int record(int history, boolean reversed) {
        return ((history << 1) | (reversed ? 1 : 0)) & ((1 << WINDOW) - 1);
    }

    /** Long counterpart to {@link #record(int, boolean)} for re-entry history. */
    static long recordSlow(long history, boolean reentered) {
        return ((history << 1) | (reentered ? 1L : 0L))
                & ((1L << SLOW_WINDOW) - 1L);
    }

    /** Kept separate so the exact slow-cycle threshold has a focused test. */
    static boolean slowCycle(long reentries) {
        return Long.bitCount(reentries) >= SLOW_REENTRIES;
    }

    static boolean resumedAfterGap(int quietFrames) {
        return quietFrames >= REENTRY_GAP;
    }

    static boolean apply(
            int drivenSlot,
            Vector3f before,
            Vector3f after,
            boolean unresolved,
            boolean collisionCorrected,
            float dt,
            SpringBoneState state
    ) {
        SpringOscillationState oscillation = state.oscillation;
        Vector3f previous = oscillation.projectionCorrections[drivenSlot];
        float cx = after.x - before.x;
        float cy = after.y - before.y;
        float cz = after.z - before.z;
        if (!collisionCorrected
                || cx * cx + cy * cy + cz * cz
                <= SIGNIFICANT * SIGNIFICANT) {
            /*
             * Nothing was asked of collision projection, which is a quiet frame
             * rather than a settled squeeze. Swing-only correction deliberately
             * stays outside this state: collision damping may not weaken the
             * segment's legal swing bound.
             */
            oscillation.projectionReversals[drivenSlot] =
                    record(oscillation.projectionReversals[drivenSlot], false);
            long reentries = recordSlow(
                    oscillation.projectionReentries[drivenSlot],
                    false
            );
            oscillation.projectionReentries[drivenSlot] = reentries;
            oscillation.projectionCorrectionGap[drivenSlot] = Math.min(
                    SLOW_WINDOW,
                    oscillation.projectionCorrectionGap[drivenSlot] + 1
            );
            int quiet = ++oscillation.projectionQuiet[drivenSlot];
            if (quiet >= SETTLED_BEFORE_RELEASE
                    && Long.bitCount(reentries) == 0) {
                oscillation.projectionDamping[drivenSlot] = decay(
                        oscillation.projectionDamping[drivenSlot],
                        dt
                );
            }
            previous.zero();
            return false;
        }
        boolean reversed = previous.lengthSquared()
                > SIGNIFICANT * SIGNIFICANT
                && cx * previous.x + cy * previous.y + cz * previous.z < 0.0F;
        int history = record(
                oscillation.projectionReversals[drivenSlot],
                reversed
        );
        oscillation.projectionReversals[drivenSlot] = history;
        boolean reentered = resumedAfterGap(
                oscillation.projectionCorrectionGap[drivenSlot]
        ) && significantReentry(cx * cx + cy * cy + cz * cz);
        oscillation.projectionCorrectionGap[drivenSlot] = 0;
        long reentries = recordSlow(
                oscillation.projectionReentries[drivenSlot],
                reentered
        );
        oscillation.projectionReentries[drivenSlot] = reentries;
        int quiet = reversed
                ? 0
                : oscillation.projectionQuiet[drivenSlot] + 1;
        oscillation.projectionQuiet[drivenSlot] = quiet;
        /*
         * Either a run of reversals or enough of them close together. The streak
         * still catches the plain period-2 flip on the frame it becomes clear;
         * the window additionally catches longer orbits, whose reversals are
         * frequent but not consecutive.
         */
        int recent = Integer.bitCount(history);
        int slowRecent = Long.bitCount(reentries);
        boolean reversalStuck = unresolved
                && (Integer.numberOfTrailingZeros(~history) >= SUSTAINED
                        || recent >= WINDOW_REVERSALS);
        boolean reentryStuck = slowCycle(reentries);
        boolean releasing = quiet >= SETTLED_BEFORE_RELEASE
                && recent == 0
                && slowRecent == 0;
        float damping;
        if (reentryStuck) {
            /*
             * The caller rolls this correction back and suppresses competing
             * colliders entered by its undamped result. Keeping even the ordinary
             * 12% response retained a measured 5–8 px jump on FFM1_1.
             */
            damping = 1.0F;
        } else if (reversalStuck) {
            damping = Math.min(
                    1.0F,
                    oscillation.projectionDamping[drivenSlot] + RISE
            );
        } else if (releasing) {
            damping = decay(
                    oscillation.projectionDamping[drivenSlot],
                    dt
            );
        } else {
            damping = oscillation.projectionDamping[drivenSlot];
        }
        oscillation.projectionDamping[drivenSlot] = damping;
        // Detection reads the demand, not the damped answer to it.
        previous.set(cx, cy, cz);
        if (damping <= 0.0F) {
            return false;
        }
        float minimumResponse = reentryStuck
                ? 0.0F
                : SQUEEZE_MINIMUM_RESPONSE;
        float response = 1.0F
                - (1.0F - minimumResponse) * damping;
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
        return reentryStuck;
    }

    static boolean significantReentry(float correctionSquared) {
        return correctionSquared
                >= REENTRY_CORRECTION * REENTRY_CORRECTION;
    }

    /** Clears damping once the selected responder owns the contact alone. */
    static void competitorsSuppressed(int drivenSlot, SpringBoneState state) {
        SpringOscillationState oscillation = state.oscillation;
        oscillation.projectionCorrections[drivenSlot].zero();
        oscillation.projectionReversals[drivenSlot] = 0;
        oscillation.projectionReentries[drivenSlot] = 0L;
        oscillation.projectionCorrectionGap[drivenSlot] = 0;
        oscillation.projectionQuiet[drivenSlot] = 0;
        oscillation.projectionDamping[drivenSlot] = 0.0F;
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
