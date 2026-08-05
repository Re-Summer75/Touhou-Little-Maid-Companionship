package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.GazeGestureTiming;
import com.laixia.maidintelligence.feature.behavior.domain.GazeGestureTracker;
import com.laixia.maidintelligence.feature.behavior.domain.GazeGestureTracker.Signal;

/**
 * The gesture that has to tell "I want to look at her" apart from "come here".
 *
 * <p>The first check is the whole reason this replaced a hold timer, and it is
 * the one a duration threshold can never pass: staring at her indefinitely must
 * never summon her, however long the stare.
 */
public final class GazeGestureVerification {
    private static final int MAID = 17;
    private static final int OTHER_MAID = 23;
    private static final long SPOT = 1_000L;
    private static final long OTHER_SPOT = 2_000L;
    private static final long NONE = GazeGestureTracker.NO_DESTINATION;

    /** Six ticks to be noticed, two seconds to point, four ticks to settle. */
    private static final GazeGestureTiming TIMING =
            new GazeGestureTiming(6, 40, 4);

    private GazeGestureVerification() {
    }

    public static void main(String[] args) {
        admiringHerNeverSummons();
        theGestureSummons();
        aGlanceIsNotAcknowledged();
        sweepingTheViewAwayDoesNotSummon();
        theWindowExpires();
        eyeContactKeepsTheGestureOpen();
        aimingAtAnotherMaidRestartsTheGesture();
        pointingWhileStillLookingAtHerIsIgnored();
        summoningRequiresAFreshGesture();
    }

    /**
     * Someone who likes the model will hold their view on her for a long time.
     * That has to remain a pure interaction: she looks back, and nothing else
     * ever happens.
     */
    private static void admiringHerNeverSummons() {
        GazeGestureTracker gesture = new GazeGestureTracker();
        boolean acknowledged = false;
        for (int tick = 0; tick < 600; tick++) {
            Signal signal = gesture.observe(MAID, NONE, TIMING);
            require(signal != Signal.SUMMON,
                    "Looking at her for " + tick + " ticks summoned her");
            acknowledged |= signal == Signal.ACKNOWLEDGED;
        }
        require(acknowledged,
                "A sustained look never got her to look back");
    }

    private static void theGestureSummons() {
        GazeGestureTracker gesture = new GazeGestureTracker();
        require(look(gesture, 6) == Signal.ACKNOWLEDGED,
                "Six ticks of aim did not get her attention");
        for (int tick = 0; tick < 3; tick++) {
            require(gesture.observe(-1, SPOT, TIMING) == Signal.ACKNOWLEDGED,
                    "The gesture closed before the spot settled");
        }
        require(gesture.observe(-1, SPOT, TIMING) == Signal.SUMMON,
                "Pointing at a settled spot did not summon her");
        require(gesture.targetId() == MAID,
                "The summon lost track of which maid it was for");
        require(gesture.destination() == SPOT,
                "The summon lost the spot that was pointed at");
    }

    /** Panning a camera across her is not addressing her. */
    private static void aGlanceIsNotAcknowledged() {
        GazeGestureTracker gesture = new GazeGestureTracker();
        for (int tick = 0; tick < 5; tick++) {
            require(gesture.observe(MAID, NONE, TIMING) == Signal.NONE,
                    "A glance got her attention after only " + tick + " ticks");
        }
        require(gesture.observe(-1, NONE, TIMING) == Signal.NONE,
                "Looking away after a glance did something");
        require(!gesture.acknowledged(),
                "A glance left the gesture open");
    }

    /**
     * The most likely false positive in play: look at her, then turn to walk
     * off. The view crosses the ground the whole way, so what must not count is
     * a spot the aim merely passed over.
     */
    private static void sweepingTheViewAwayDoesNotSummon() {
        GazeGestureTracker gesture = new GazeGestureTracker();
        look(gesture, 6);
        for (int tick = 0; tick < 30; tick++) {
            // A different block every tick, as a turning view produces.
            Signal signal = gesture.observe(-1, SPOT + tick, TIMING);
            require(signal != Signal.SUMMON,
                    "A sweeping view summoned her at tick " + tick);
        }
    }

    private static void theWindowExpires() {
        GazeGestureTracker gesture = new GazeGestureTracker();
        look(gesture, 6);
        for (int tick = 0; tick < 39; tick++) {
            require(gesture.observe(-1, NONE, TIMING) == Signal.ACKNOWLEDGED,
                    "The window closed early at tick " + tick);
        }
        require(gesture.observe(-1, NONE, TIMING) == Signal.NONE,
                "The window did not expire");
        require(!gesture.acknowledged(), "An expired gesture stayed open");

        // Pointing after it lapsed must do nothing on its own.
        for (int tick = 0; tick < 10; tick++) {
            require(gesture.observe(-1, SPOT, TIMING) == Signal.NONE,
                    "A lapsed gesture still fired when a spot settled");
        }
    }

    /**
     * The window is time to point after looking away, not a deadline on
     * looking at her.
     */
    private static void eyeContactKeepsTheGestureOpen() {
        GazeGestureTracker gesture = new GazeGestureTracker();
        look(gesture, 6);
        for (int tick = 0; tick < 400; tick++) {
            require(gesture.observe(MAID, NONE, TIMING) == Signal.ACKNOWLEDGED,
                    "Holding eye contact expired the gesture");
        }
        for (int tick = 0; tick < 3; tick++) {
            gesture.observe(-1, SPOT, TIMING);
        }
        require(gesture.observe(-1, SPOT, TIMING) == Signal.SUMMON,
                "A long-held gaze could no longer be followed by pointing");
    }

    private static void aimingAtAnotherMaidRestartsTheGesture() {
        GazeGestureTracker gesture = new GazeGestureTracker();
        look(gesture, 6);
        require(gesture.observe(OTHER_MAID, NONE, TIMING) == Signal.NONE,
                "Switching maids kept the first one's gesture open");
        require(gesture.targetId() == OTHER_MAID,
                "Switching maids did not retarget");
        // One tick of hers is already counted by the switch above.
        require(look(gesture, OTHER_MAID, 5) == Signal.ACKNOWLEDGED,
                "The second maid's gesture did not build from scratch");

        // The point must now be for the second maid, not the first.
        for (int tick = 0; tick < 3; tick++) {
            gesture.observe(-1, SPOT, TIMING);
        }
        require(gesture.observe(-1, SPOT, TIMING) == Signal.SUMMON
                        && gesture.targetId() == OTHER_MAID,
                "The destination was applied to the wrong maid");
    }

    /**
     * While the aim is still on her there is no pointing happening, whatever a
     * ray behind her would have hit.
     */
    private static void pointingWhileStillLookingAtHerIsIgnored() {
        GazeGestureTracker gesture = new GazeGestureTracker();
        look(gesture, 6);
        for (int tick = 0; tick < 20; tick++) {
            require(gesture.observe(MAID, SPOT, TIMING) == Signal.ACKNOWLEDGED,
                    "A spot settled while the aim was still on her");
        }
    }

    private static void summoningRequiresAFreshGesture() {
        GazeGestureTracker gesture = new GazeGestureTracker();
        look(gesture, 6);
        for (int tick = 0; tick < 3; tick++) {
            gesture.observe(-1, SPOT, TIMING);
        }
        require(gesture.observe(-1, SPOT, TIMING) == Signal.SUMMON,
                "The gesture did not complete");
        for (int tick = 0; tick < 40; tick++) {
            require(gesture.observe(-1, OTHER_SPOT, TIMING) == Signal.NONE,
                    "Pointing again re-summoned without catching her eye");
        }
    }

    private static Signal look(GazeGestureTracker gesture, int ticks) {
        return look(gesture, MAID, ticks);
    }

    private static Signal look(
            GazeGestureTracker gesture,
            int maidId,
            int ticks
    ) {
        Signal signal = Signal.NONE;
        for (int tick = 0; tick < ticks; tick++) {
            signal = gesture.observe(maidId, NONE, TIMING);
        }
        return signal;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
