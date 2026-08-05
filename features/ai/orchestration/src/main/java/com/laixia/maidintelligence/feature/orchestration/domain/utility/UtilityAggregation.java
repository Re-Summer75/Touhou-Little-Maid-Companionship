package com.laixia.maidintelligence.feature.orchestration.domain.utility;

/**
 * How an intent folds its considerations into a single score.
 *
 * <p>{@link #SUM} is the original behaviour and stays bit-identical for every
 * {@code format_version: 1} definition. {@link #PRODUCT} is the Infinite Axis
 * form and is what new definitions should use.
 *
 * <p>The difference is not cosmetic. Under {@code SUM} a consideration that
 * reads zero is simply out-voted by the others, so "must not be in combat"
 * cannot be expressed as a consideration at all — it has to become a hard
 * condition, and the number of conditions grows with every nuance. Under
 * {@code PRODUCT} a single zero factor drives the whole score to zero, so a
 * consideration carries its own veto and the two mechanisms stop competing.
 *
 * <p>{@code SUM} is also unbounded: adding considerations raises the ceiling,
 * so {@code base_score} values are only comparable between intents that happen
 * to declare the same number of terms. {@code PRODUCT} keeps every score in
 * {@code [0, 1]}, which is what makes scores comparable across a catalog that
 * is still growing.
 */
public enum UtilityAggregation {
    /**
     * Weighted sum. Terms may be negative when a definition uses a negative
     * weight, so a partial sum bounds the final score in neither direction and
     * {@link #monotonicallyNonIncreasing()} is false.
     */
    SUM {
        @Override
        public double term(UtilityConsideration consideration, double actual) {
            return consideration.contribution(actual);
        }

        @Override
        public double combine(double accumulated, double term) {
            return accumulated + term;
        }

        @Override
        public double finish(double accumulated, int considerationCount) {
            return accumulated;
        }

        @Override
        public boolean monotonicallyNonIncreasing() {
            return false;
        }
    },
    /**
     * Product of per-consideration factors, each in {@code [0, 1]}, followed by
     * the Infinite Axis compensation described on
     * {@link #finish(double, int)}.
     */
    PRODUCT {
        @Override
        public double term(UtilityConsideration consideration, double actual) {
            return consideration.factor(actual);
        }

        @Override
        public double combine(double accumulated, double term) {
            return accumulated * term;
        }

        /**
         * Multiplying many values below one drives every option towards zero
         * purely because it was described in detail, which would punish the
         * best-specified intents hardest. The compensation lifts the product
         * back by an amount that grows with the number of terms.
         *
         * <p>It is deliberately anchored: a raw product of zero stays zero and
         * a raw product of one stays one. Losing either would cost the veto
         * property that is the whole reason to multiply.
         */
        @Override
        public double finish(double accumulated, int considerationCount) {
            if (considerationCount <= 1) {
                return accumulated;
            }
            double modification = 1.0D - 1.0D / considerationCount;
            return accumulated
                    + (1.0D - accumulated) * modification * accumulated;
        }

        @Override
        public boolean monotonicallyNonIncreasing() {
            return true;
        }
    };

    public abstract double term(
            UtilityConsideration consideration,
            double actual
    );

    public abstract double combine(double accumulated, double term);

    public abstract double finish(double accumulated, int considerationCount);

    /**
     * Whether a partial accumulation is an upper bound on the finished score.
     *
     * <p>Only then may the selection engine stop early: it needs to prove that
     * an intent cannot overtake the incumbent, and a running value that could
     * still rise proves nothing.
     */
    public abstract boolean monotonicallyNonIncreasing();
}
