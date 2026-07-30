package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class AnimationTimelineClockVerification {
    private static final float EPSILON = 1.0E-6F;

    private AnimationTimelineClockVerification() {
    }

    static void run() {
        AnimationTimelineClock clock = new AnimationTimelineClock();
        requireNear(clock.advance(100.25D, false), 0.0F, "initial");
        requireNear(clock.advance(100.50D, false), 0.0125F, "advance");
        requireNear(clock.advance(100.50D, false), 0.0F, "duplicate");
        requireNear(clock.advance(100.40D, false), 0.0F, "out of order");
        requireNear(clock.advance(100.75D, false), 0.0125F, "recovery");

        requireNear(clock.advance(101.0D, true), 0.0F, "pause");
        requireNear(clock.advance(101.0D, false), 0.0F, "resume duplicate");
        requireNear(clock.advance(101.20D, false), 0.01F, "resume advance");

        /*
         * One game tick sits exactly on the ceiling and has to pass through
         * intact. That is what keeps the clamp off every frame rate from 20 up,
         * leaving it to act only on a genuine stall.
         */
        requireNear(clock.advance(102.20D, false), 0.05F, "one tick");

        requireNear(clock.advance(105.0D, false), 0.05F, "clamp");
        require(
                !clock.discontinuous(),
                "A finite clamped animation step was treated as a cut"
        );
        requireNear(clock.advance(111.0D, false), 0.0F, "large gap");
        require(clock.discontinuous(), "Large animation gap was not detected");
        requireNear(clock.advance(111.20D, false), 0.01F, "gap recovery");

        requireNear(
                clock.advance(Double.NaN, false),
                0.0F,
                "non-finite"
        );
        require(clock.discontinuous(), "Non-finite animation time survived");
        requireNear(clock.advance(120.0D, false), 0.0F, "reset baseline");
    }

    private static void requireNear(
            float actual,
            float expected,
            String label
    ) {
        require(
                Math.abs(actual - expected) < EPSILON,
                "Animation timeline " + label + " step was "
                        + actual + " instead of " + expected
        );
    }
}
