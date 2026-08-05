package com.laixia.maidintelligence.feature.behavior.domain;

/**
 * Two-stage gaze gesture: catch her eye, then point.
 *
 * <p>Replaces a hold timer, which could not work. Looking longer is still only
 * looking — someone admiring a model and someone calling her over produce the
 * same signal, and no threshold separates them. Intent needs a second component
 * rather than more of the first.
 *
 * <p>So the gesture is the one people actually use. Resting your gaze on her
 * makes her look back, which costs nothing and commits nothing; that <em>is</em>
 * the interaction for wanting to look at her. Moving your aim to a spot while
 * she is watching is what says "there" — and it is precisely what someone who
 * only wanted to look never does, because their view stays on her.
 *
 * <p>Allocation-free and free of any world types, so the timing rules can be
 * tested without a game. The adapter decides what counts as looking at a maid
 * and what counts as a usable destination; everything about <em>when</em> those
 * observations add up to a gesture is here.
 */
public final class GazeGestureTracker {
    public static final long NO_DESTINATION = Long.MIN_VALUE;
    private static final int NO_TARGET = -1;

    /**
     * What the observation means for the maid this tick.
     */
    public enum Signal {
        /** Nothing to do. */
        NONE,
        /**
         * She should be looking at the owner right now. Emitted every tick the
         * gesture is open, not once, so a caller can keep refreshing a
         * short-lived look target instead of having to remember to clear one.
         */
        ACKNOWLEDGED,
        /** The gesture completed: the owner pointed somewhere. */
        SUMMON
    }

    private int targetId = NO_TARGET;
    private int gazeTicks;
    private boolean acknowledged;
    private int windowLeft;
    private long destination = NO_DESTINATION;
    private int destinationTicks;

    /**
     * @param gazedMaidId    the maid the owner is aiming at, or negative
     * @param destinationKey a packed position the owner is aiming at that would
     *                       be a usable destination, or {@link #NO_DESTINATION}
     * @return what the caller should do about it
     */
    public Signal observe(
            int gazedMaidId,
            long destinationKey,
            GazeGestureTiming timing
    ) {
        if (!acknowledged) {
            return building(gazedMaidId, timing);
        }
        if (gazedMaidId >= 0 && gazedMaidId != targetId) {
            // Aiming at a different maid abandons this gesture and starts
            // building one on her, rather than letting the first maid inherit
            // a destination meant for the second.
            reset();
            targetId = gazedMaidId;
            gazeTicks = 1;
            return Signal.NONE;
        }
        if (gazedMaidId == targetId) {
            /*
             * Eye contact holds the gesture open indefinitely. The window is
             * time to point after looking away, not a deadline on looking at
             * her — someone who simply wants to watch her should be able to,
             * for as long as they like, without it either expiring or firing.
             */
            windowLeft = timing.windowTicks();
            clearDestination();
            return Signal.ACKNOWLEDGED;
        }
        if (--windowLeft <= 0) {
            reset();
            return Signal.NONE;
        }
        return pointing(destinationKey, timing);
    }

    private Signal building(int gazedMaidId, GazeGestureTiming timing) {
        if (gazedMaidId < 0) {
            targetId = NO_TARGET;
            gazeTicks = 0;
            return Signal.NONE;
        }
        if (gazedMaidId != targetId) {
            targetId = gazedMaidId;
            gazeTicks = 1;
        } else {
            gazeTicks++;
        }
        if (gazeTicks < timing.acknowledgeTicks()) {
            return Signal.NONE;
        }
        acknowledged = true;
        windowLeft = timing.windowTicks();
        clearDestination();
        return Signal.ACKNOWLEDGED;
    }

    private Signal pointing(long destinationKey, GazeGestureTiming timing) {
        if (destinationKey == NO_DESTINATION) {
            // Aiming at sky, at something too far, or at nothing usable. The
            // gesture stays open; only the settle restarts.
            clearDestination();
            return Signal.ACKNOWLEDGED;
        }
        if (destinationKey != destination) {
            destination = destinationKey;
            destinationTicks = 1;
        } else {
            destinationTicks++;
        }
        if (destinationTicks < timing.settleTicks()) {
            return Signal.ACKNOWLEDGED;
        }
        /*
         * Target and destination stay readable for the rest of this tick so the
         * caller can act on the signal; the next observation clears them.
         */
        acknowledged = false;
        gazeTicks = 0;
        windowLeft = 0;
        destinationTicks = 0;
        return Signal.SUMMON;
    }

    /** The maid this gesture is about; valid while a signal is being handled. */
    public int targetId() {
        return targetId;
    }

    /** The pointed-at position; valid on the tick {@link Signal#SUMMON} is returned. */
    public long destination() {
        return destination;
    }

    public boolean acknowledged() {
        return acknowledged;
    }

    public void reset() {
        targetId = NO_TARGET;
        gazeTicks = 0;
        acknowledged = false;
        windowLeft = 0;
        clearDestination();
    }

    private void clearDestination() {
        destination = NO_DESTINATION;
        destinationTicks = 0;
    }
}
