package com.laixia.maidintelligence.feature.orchestration.domain.utility;

/**
 * Response curves mapping a normalized fact in {@code [0, 1]} to a utility in
 * {@code [0, 1]}.
 *
 * <p>Every curve is anchored so that {@code apply(0) == 0} and
 * {@code apply(1) == 1}, or their mirror for the inverse variants. Product
 * aggregation multiplies these values together, so a curve that fell short of
 * its endpoints would silently distort every intent that used it: a
 * consideration meant to veto could never drive the product to zero, and one
 * meant to be fully satisfied would still shave the score.
 *
 * <p>{@code INVERSE_*} always means the curve mirrored on the input axis —
 * {@code inverse(x) == curve(1 - x)}, not {@code 1 - curve(x)}. The two differ
 * for every non-linear curve, and the mirror is the one that keeps "a high
 * fact means low utility" reading the same way across the whole set.
 */
public enum UtilityCurve {
    LINEAR {
        @Override
        public double apply(double normalized) {
            return clamp(normalized);
        }
    },
    INVERSE_LINEAR {
        @Override
        public double apply(double normalized) {
            return 1.0D - clamp(normalized);
        }
    },
    STEP {
        @Override
        public double apply(double normalized) {
            return clamp(normalized) >= 1.0D ? 1.0D : 0.0D;
        }
    },
    /**
     * Accelerating: stays near zero until the fact is well into its range. Use
     * when only the upper part of the range should carry weight.
     */
    QUADRATIC {
        @Override
        public double apply(double normalized) {
            double clamped = clamp(normalized);
            return clamped * clamped;
        }
    },
    /**
     * Mirror of {@link #QUADRATIC}: decays quickly, then flattens near zero.
     */
    INVERSE_QUADRATIC {
        @Override
        public double apply(double normalized) {
            double mirrored = 1.0D - clamp(normalized);
            return mirrored * mirrored;
        }
    },
    /**
     * Sigmoid with a soft threshold near the middle of the range. This is the
     * curve for "matters once it crosses roughly half way", and it is the one
     * to reach for instead of {@link #STEP} under product aggregation: a step
     * is discontinuous, so an intent scored through it blinks in and out while
     * a fact merely hovers at the boundary.
     */
    LOGISTIC {
        @Override
        public double apply(double normalized) {
            return logistic(clamp(normalized));
        }
    },
    /**
     * Mirror of {@link #LOGISTIC}.
     */
    INVERSE_LOGISTIC {
        @Override
        public double apply(double normalized) {
            return logistic(1.0D - clamp(normalized));
        }
    },
    /**
     * Exact inverse of {@link #LOGISTIC}: steep at both ends, flat through the
     * middle. Use when only the extremes of a range are informative.
     *
     * <p>{@code LOGIT.apply(LOGISTIC.apply(x))} returns {@code x} across the
     * whole domain. That round trip is what makes the pair safe to use as a
     * matched encode/decode when a fact is published in one space and scored
     * in the other.
     */
    LOGIT {
        @Override
        public double apply(double normalized) {
            return logit(clamp(normalized));
        }
    };

    /**
     * Steepness shared by the sigmoid pair. Eight leaves roughly the middle
     * half of the range responsive while keeping the tails visibly flat.
     * Much higher degenerates towards {@link #STEP} and much lower towards
     * {@link #LINEAR}, so neither extreme would earn its own curve.
     */
    private static final double STEEPNESS = 8.0D;

    /**
     * Raw sigmoid value at the low end of the range. The raw sigmoid reaches
     * neither zero nor one, so both sigmoid curves are rescaled against these
     * anchors to land on the endpoints exactly.
     */
    private static final double LOW_ANCHOR =
            1.0D / (1.0D + Math.exp(STEEPNESS * 0.5D));

    private static final double ANCHOR_SPAN = 1.0D - 2.0D * LOW_ANCHOR;

    public abstract double apply(double normalized);

    /**
     * {@link UtilityConsideration} already normalizes into the unit interval
     * before applying, but {@code apply} is public and the sigmoid pair has no
     * meaningful value outside it, so every curve restates the bound rather
     * than assuming its caller. Clamping uniformly also keeps the endpoint
     * contract above true of the enum as a whole, instead of true only for
     * the curves that happen to need the guard arithmetically.
     */
    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double logistic(double clamped) {
        double raw = 1.0D
                / (1.0D + Math.exp(-STEEPNESS * (clamped - 0.5D)));
        return clamp((raw - LOW_ANCHOR) / ANCHOR_SPAN);
    }

    private static double logit(double clamped) {
        double raw = clamped * ANCHOR_SPAN + LOW_ANCHOR;
        // raw stays strictly inside (0, 1) for every clamped input, so the
        // ratio below is finite without a further guard.
        return clamp(0.5D + Math.log(raw / (1.0D - raw)) / STEEPNESS);
    }
}
