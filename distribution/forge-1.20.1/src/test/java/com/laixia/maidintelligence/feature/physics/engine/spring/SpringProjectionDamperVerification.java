package com.laixia.maidintelligence.feature.physics.engine.spring;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

/**
 * Focused regression for recurring collision cycles measured in game.
 */
public final class SpringProjectionDamperVerification {
    private SpringProjectionDamperVerification() {
    }

    public static void run() {
        SpringIntegrationDamperVerification.run();
        SpringLinearCorrectionVerification.run();
        SpringAuthoredPoseAnchorVerification.run();
        if (SpringProjectionDamper.significantReentry(0.019F * 0.019F)
                || !SpringProjectionDamper.significantReentry(
                0.021F * 0.021F
        )) {
            throw new AssertionError(
                    "Visible re-entry threshold accepted support noise"
            );
        }
        long history = 0L;
        int gap = 0;
        for (int frame = 0; frame < 30; frame++) {
            boolean corrected = frame % 5 == 4;
            history = SpringProjectionDamper.recordSlow(
                    history,
                    corrected
                            && SpringProjectionDamper.resumedAfterGap(gap)
            );
            gap = corrected ? 0 : gap + 1;
            if (frame < 29 && SpringProjectionDamper.slowCycle(history)) {
                throw new AssertionError(
                        "Slow-cycle suppression armed before six re-entries"
                );
            }
        }
        if (!SpringProjectionDamper.slowCycle(history)) {
            throw new AssertionError(
                    "Five-frame collision cycle escaped suppression"
            );
        }

        long alternating = 0L;
        gap = 0;
        for (int frame = 0; frame < 12; frame++) {
            boolean corrected = frame % 2 == 1;
            alternating = SpringProjectionDamper.recordSlow(
                    alternating,
                    corrected
                            && SpringProjectionDamper.resumedAfterGap(gap)
            );
            gap = corrected ? 0 : gap + 1;
        }
        if (!SpringProjectionDamper.slowCycle(alternating)) {
            throw new AssertionError(
                    "Alternating-frame collision cycle escaped suppression"
            );
        }

        long continuous = 0L;
        for (int frame = 0; frame < 30; frame++) {
            continuous = SpringProjectionDamper.recordSlow(
                    continuous,
                    SpringProjectionDamper.resumedAfterGap(0)
            );
        }
        if (SpringProjectionDamper.slowCycle(continuous)) {
            throw new AssertionError(
                    "Continuous contact was mistaken for a re-entry cycle"
            );
        }

        for (int frame = 0; frame < 30; frame++) {
            history = SpringProjectionDamper.recordSlow(history, false);
        }
        if (SpringProjectionDamper.slowCycle(history)) {
            throw new AssertionError(
                    "Slow-cycle history did not clear after a quiet window"
            );
        }
        SpringInterlockRecoveryVerification.run();
    }
}
