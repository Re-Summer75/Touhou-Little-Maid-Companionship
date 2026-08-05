package com.laixia.maidintelligence.feature.behavior.domain;

/**
 * Sensor timing defaults; intent eligibility and arrival are data-driven.
 */
public final class GazeRecallPolicy {
    /**
     * How long the owner holds their gaze before the maid looks back.
     *
     * <p>Six ticks is long enough that panning the camera past her does not
     * count, and short enough to feel immediate. It is only the first half of
     * the gesture, so it does not have to tell intent apart from interest on
     * its own — that is what pointing afterwards is for, which is why it can
     * stay this short without becoming a hair trigger.
     */
    public static final int DEFAULT_HOLD_TICKS = 6;

    /**
     * How long the gesture stays open after the owner looks away from her. Two
     * seconds is a comfortable beat in which to move one's aim somewhere
     * deliberate, and short enough that a glance a minute later is unrelated.
     */
    public static final int DEFAULT_WINDOW_TICKS = 40;

    /**
     * How long the owner's aim must rest on a spot for it to count as pointing
     * at it. This is what separates indicating a place from sweeping the view
     * across it while turning to walk away.
     */
    public static final int DEFAULT_SETTLE_TICKS = 4;

    /**
     * How far the gaze sensor reaches, matching what a maid perceives by every
     * other sense. Eight blocks meant she could be looked at across a room but
     * not across the building she lives in.
     */
    public static final double DEFAULT_RANGE = 16.0D;

    public static final int CURRENT_TIMING_REVISION = 4;

    /** Revision 1: a one-second hold. */
    private static final int LEGACY_DEFAULT_HOLD_TICKS = 20;

    /**
     * Revision 2: two ticks, which was not a gesture at all — panning a camera
     * past a maid triggered it. The gesture that replaced it needs a real
     * dwell, so this default is migrated as well.
     */
    private static final int GLANCE_DEFAULT_HOLD_TICKS = 2;

    /** Revisions 1 to 3: a single room's worth of reach. */
    private static final double LEGACY_DEFAULT_RANGE = 8.0D;

    private GazeRecallPolicy() {
    }

    /**
     * Companion to {@link #migrateHoldTicks}: moves a range left at the old
     * default onto the current one, and leaves a chosen value alone.
     */
    public static double migrateRange(double configured, int timingRevision) {
        if (timingRevision >= CURRENT_TIMING_REVISION) {
            return configured;
        }
        return configured == LEGACY_DEFAULT_RANGE
                ? DEFAULT_RANGE
                : configured;
    }

    /**
     * Moves a configuration written for an older gesture onto the current
     * default, but only while it still holds that gesture's own default. A
     * value somebody chose deliberately is left alone.
     */
    public static int migrateHoldTicks(int configured, int timingRevision) {
        if (timingRevision >= CURRENT_TIMING_REVISION) {
            return configured;
        }
        if (configured == LEGACY_DEFAULT_HOLD_TICKS
                || configured == GLANCE_DEFAULT_HOLD_TICKS) {
            return DEFAULT_HOLD_TICKS;
        }
        return configured;
    }
}
