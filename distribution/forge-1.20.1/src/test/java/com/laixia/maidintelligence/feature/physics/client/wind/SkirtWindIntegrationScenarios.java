package com.laixia.maidintelligence.feature.physics.client.wind;

import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.EnvironmentalWindVerificationFacade.require;
import static com.laixia.maidintelligence.feature.physics.client.wind.WindScenarioSupport.ZERO;
import static com.laixia.maidintelligence.feature.physics.client.wind.WindScenarioSupport.angleBetween;
import static com.laixia.maidintelligence.feature.physics.client.wind.WindScenarioSupport.correlation;
import static com.laixia.maidintelligence.feature.physics.client.wind.WindScenarioSupport.createChainFixture;
import static com.laixia.maidintelligence.feature.physics.client.wind.WindScenarioSupport.createFixture;
import static com.laixia.maidintelligence.feature.physics.client.wind.WindScenarioSupport.highPass;

public final class SkirtWindIntegrationScenarios {
    private SkirtWindIntegrationScenarios() {
    }

    public static void run() {
        verifiesSkirtWindAngleIsBounded();
        verifiesChainRipplesAlongItsLength();
    }

    /**
     * The lean settles at the safety ceiling instead of overshooting it, so
     * this has to be measured after the spring converges rather than on a
     * single frame. Measuring against a calm fixture rather than against the
     * authored axis keeps the reading to the wind's own contribution whatever
     * else settles the segment.
     */
    private static void verifiesSkirtWindAngleIsBounded() {
        WindScenarioSupport.WindFixture rest =
                createFixture("skirt_rest", "SKIRT");
        WindScenarioSupport.WindFixture windy =
                createFixture("skirt_bounded", "SKIRT");
        Vector3f restDirection = new Vector3f();
        Vector3f windyDirection = new Vector3f();
        Vector3f gale = new Vector3f(100.0F, 0.0F, 0.0F);
        for (int frame = 0; frame < 240; frame++) {
            rest.solver().restoreAnimationPose();
            windy.solver().restoreAnimationPose();
            rest.solver().solve(
                    ZERO, ZERO, 0.0F, 1.0F / 60.0F, false
            );
            windy.solver().solve(
                    ZERO, gale, 0.0F, 1.0F / 60.0F, false
            );
        }
        require(
                rest.solver().copyCurrentDirection(
                        rest.drivenSlot(),
                        restDirection
                )
                        && windy.solver().copyCurrentDirection(
                        windy.drivenSlot(),
                        windyDirection
                ),
                "Bounded skirt wind direction was unavailable"
        );
        float angle = angleBetween(restDirection, windyDirection);
        require(
                angle > 0.17F && angle <= 0.23F,
                "Skirt wind angle escaped its safety ceiling: " + angle
        );
    }

    /**
     * A disturbance crossing a surface reaches the far end of a chain after the
     * near end, so the tip must repeat the root's motion a moment later rather
     * than beside it. Both halves of that matter: shared timing (a ripple, not
     * independent jitter) and a delay (a travelling wave, not a lockstep swing).
     */
    private static void verifiesChainRipplesAlongItsLength() {
        WindScenarioSupport.ChainWindFixture fixture = createChainFixture();
        int samples = 600;
        int segments = fixture.slots().length;
        float[][] history = new float[segments][samples];
        Vector3f wind = new Vector3f(0.22F, 0.0F, 0.0F);
        Vector3f direction = new Vector3f();
        for (int frame = 0; frame < samples + 120; frame++) {
            fixture.solver().restoreAnimationPose();
            fixture.solver().solve(
                    ZERO, wind, 0.0F, 1.0F / 60.0F, false
            );
            if (frame < 120) {
                continue;
            }
            for (int segment = 0; segment < segments; segment++) {
                require(
                        fixture.solver().copyCurrentDirection(
                                fixture.slots()[segment],
                                direction
                        ),
                        "Chain ripple direction was unavailable"
                );
                history[segment][frame - 120] = direction.x;
            }
        }
        float[] root = highPass(history[0]);
        float[] tip = highPass(history[segments - 1]);
        int lag = bestLag(root, tip);
        float aligned = shifted(root, tip, 0);
        float delayed = shifted(root, tip, lag);
        /*
         * The spring itself carries some delay down a chain, so a positive lag
         * alone proves nothing: with the travelling gust switched off this
         * fixture still reads 5 frames. What separates a wave from a chain
         * merely dragging behind its root is that the two ends stop resembling
         * each other when compared frame for frame — 0.19 here against 0.86
         * without it — while resembling each other closely once the delay is
         * taken out.
         */
        require(
                lag >= 10 && delayed > 0.80F,
                "No gust travelled down the chain; the tip never reproduced"
                        + " the root's motion later: lag " + lag
                        + " at " + delayed
        );
        require(
                aligned < 0.50F,
                "Chain segments leaned in lockstep instead of rippling: "
                        + aligned
        );
    }

    /**
     * Delay, in frames, at which the tail signal best reproduces the root
     * signal. Searching only positive lags would find one by construction, so
     * negative lags are searched too and a leading tip reads as failure.
     */
    private static final int LAG_SPAN = 60;

    private static int bestLag(float[] root, float[] tip) {
        int best = -LAG_SPAN - 1;
        float strongest = Float.NEGATIVE_INFINITY;
        for (int lag = -LAG_SPAN; lag <= LAG_SPAN; lag++) {
            float value = shifted(root, tip, lag);
            if (value > strongest) {
                strongest = value;
                best = lag;
            }
        }
        return best;
    }

    /** Correlation of the tip signal advanced by {@code lag} frames. */
    private static float shifted(float[] root, float[] tip, int lag) {
        int length = root.length - 2 * LAG_SPAN;
        float[] left = new float[length];
        float[] right = new float[length];
        for (int index = 0; index < length; index++) {
            left[index] = root[index + LAG_SPAN];
            right[index] = tip[index + LAG_SPAN + lag];
        }
        return correlation(left, right);
    }
}
