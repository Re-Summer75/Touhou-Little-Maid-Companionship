package com.laixia.maidintelligence.feature.orchestration;

import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityCurve;

import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.close;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.require;

/**
 * Shape contract for the response curves.
 *
 * <p>Product aggregation makes these properties load bearing rather than
 * cosmetic: an endpoint that misses zero removes a consideration's ability to
 * veto, an endpoint that misses one taxes every intent that is fully
 * satisfied, and a curve that leaves the unit interval turns a single
 * consideration into an unbounded multiplier over the whole catalog.
 */
public final class UtilityCurveVerification {
    private static final int SAMPLES = 1_000;

    private UtilityCurveVerification() {
    }

    public static void main(String[] args) {
        everyCurveStaysInsideTheUnitInterval();
        risingCurvesAnchorOnBothEndpoints();
        fallingCurvesAnchorOnBothEndpoints();
        inverseMeansMirroredOnTheInputAxis();
        logitInvertsLogistic();
        monotonicityHoldsAcrossTheDomain();
        quadraticAndLogisticBendTheExpectedWay();
        nonFiniteInputStaysFinite();
    }

    private static void everyCurveStaysInsideTheUnitInterval() {
        for (UtilityCurve curve : UtilityCurve.values()) {
            for (int sample = 0; sample <= SAMPLES; sample++) {
                double value = curve.apply(at(sample));
                require(
                        Double.isFinite(value)
                                && value >= 0.0D
                                && value <= 1.0D,
                        "Curve " + curve + " left the unit interval at "
                                + at(sample)
                );
            }
            // Out-of-range input reaches apply directly whenever a caller
            // scores without normalizing first.
            require(close(curve.apply(-5.0D), curve.apply(0.0D)),
                    "Curve " + curve + " did not clamp below range");
            require(close(curve.apply(5.0D), curve.apply(1.0D)),
                    "Curve " + curve + " did not clamp above range");
        }
    }

    private static void risingCurvesAnchorOnBothEndpoints() {
        UtilityCurve[] rising = {
                UtilityCurve.LINEAR,
                UtilityCurve.STEP,
                UtilityCurve.QUADRATIC,
                UtilityCurve.LOGISTIC,
                UtilityCurve.LOGIT
        };
        for (UtilityCurve curve : rising) {
            require(close(curve.apply(0.0D), 0.0D),
                    "Rising curve " + curve + " does not start at zero");
            require(close(curve.apply(1.0D), 1.0D),
                    "Rising curve " + curve + " does not end at one");
        }
    }

    private static void fallingCurvesAnchorOnBothEndpoints() {
        UtilityCurve[] falling = {
                UtilityCurve.INVERSE_LINEAR,
                UtilityCurve.INVERSE_QUADRATIC,
                UtilityCurve.INVERSE_LOGISTIC
        };
        for (UtilityCurve curve : falling) {
            require(close(curve.apply(0.0D), 1.0D),
                    "Falling curve " + curve + " does not start at one");
            require(close(curve.apply(1.0D), 0.0D),
                    "Falling curve " + curve + " does not end at zero");
        }
    }

    /**
     * {@code INVERSE_X(x)} must equal {@code X(1 - x)} and not
     * {@code 1 - X(x)}. The two agree only for the linear pair, so testing the
     * linear pair alone would not catch the mistake.
     */
    private static void inverseMeansMirroredOnTheInputAxis() {
        assertMirror(UtilityCurve.LINEAR, UtilityCurve.INVERSE_LINEAR);
        assertMirror(UtilityCurve.QUADRATIC, UtilityCurve.INVERSE_QUADRATIC);
        assertMirror(UtilityCurve.LOGISTIC, UtilityCurve.INVERSE_LOGISTIC);

        // The distinction itself, stated once so a future simplification back
        // to `1 - curve(x)` fails here rather than silently reshaping data.
        double quadratic = UtilityCurve.QUADRATIC.apply(0.25D);
        double mirrored = UtilityCurve.INVERSE_QUADRATIC.apply(0.25D);
        require(!close(mirrored, 1.0D - quadratic),
                "Inverse quadratic collapsed into one-minus-curve");
    }

    private static void assertMirror(
            UtilityCurve curve,
            UtilityCurve inverse
    ) {
        for (int sample = 0; sample <= SAMPLES; sample++) {
            double point = at(sample);
            require(
                    close(inverse.apply(point), curve.apply(1.0D - point)),
                    "Curve " + inverse + " is not the input-axis mirror of "
                            + curve + " at " + point
            );
        }
    }

    private static void logitInvertsLogistic() {
        for (int sample = 0; sample <= SAMPLES; sample++) {
            double point = at(sample);
            double roundTrip = UtilityCurve.LOGIT.apply(
                    UtilityCurve.LOGISTIC.apply(point)
            );
            require(close(roundTrip, point),
                    "Logit did not invert logistic at " + point
                            + " (got " + roundTrip + ")");
        }
    }

    private static void monotonicityHoldsAcrossTheDomain() {
        for (UtilityCurve curve : UtilityCurve.values()) {
            boolean falling = curve == UtilityCurve.INVERSE_LINEAR
                    || curve == UtilityCurve.INVERSE_QUADRATIC
                    || curve == UtilityCurve.INVERSE_LOGISTIC;
            double previous = curve.apply(0.0D);
            for (int sample = 1; sample <= SAMPLES; sample++) {
                double current = curve.apply(at(sample));
                boolean ordered = falling
                        ? current <= previous + 1.0E-12D
                        : current >= previous - 1.0E-12D;
                require(ordered,
                        "Curve " + curve + " is not monotonic at "
                                + at(sample));
                previous = current;
            }
        }
    }

    /**
     * Endpoints and monotonicity alone would also be satisfied by a straight
     * line, so the interior shape is pinned separately.
     */
    private static void quadraticAndLogisticBendTheExpectedWay() {
        double point = 0.25D;
        require(UtilityCurve.QUADRATIC.apply(point) < point,
                "Quadratic is not accelerating below the midpoint");
        require(UtilityCurve.LOGISTIC.apply(point) < point,
                "Logistic does not suppress the lower tail");
        require(UtilityCurve.LOGIT.apply(point) > point,
                "Logit does not lift the lower tail");
        require(close(UtilityCurve.LOGISTIC.apply(0.5D), 0.5D),
                "Logistic is not symmetric about the midpoint");
        require(close(UtilityCurve.LOGIT.apply(0.5D), 0.5D),
                "Logit is not symmetric about the midpoint");
    }

    private static void nonFiniteInputStaysFinite() {
        UtilityCurve[] guarded = {
                UtilityCurve.QUADRATIC,
                UtilityCurve.INVERSE_QUADRATIC,
                UtilityCurve.LOGISTIC,
                UtilityCurve.INVERSE_LOGISTIC,
                UtilityCurve.LOGIT
        };
        for (UtilityCurve curve : guarded) {
            require(Double.isFinite(curve.apply(Double.NaN)),
                    "Curve " + curve + " propagated NaN");
            require(Double.isFinite(curve.apply(Double.POSITIVE_INFINITY)),
                    "Curve " + curve + " propagated positive infinity");
            require(Double.isFinite(curve.apply(Double.NEGATIVE_INFINITY)),
                    "Curve " + curve + " propagated negative infinity");
        }
    }

    private static double at(int sample) {
        return (double) sample / SAMPLES;
    }
}
