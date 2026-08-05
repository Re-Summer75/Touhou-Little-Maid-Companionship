package com.laixia.maidintelligence.feature.orchestration.domain.utility;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Objects;

public record UtilityConsideration(
        OrchestrationId fact,
        double minimum,
        double maximum,
        double weight,
        UtilityCurve curve
) {
    public UtilityConsideration {
        Objects.requireNonNull(fact, "fact");
        Objects.requireNonNull(curve, "curve");
        if (!Double.isFinite(minimum)
                || !Double.isFinite(maximum)
                || maximum <= minimum
                || !Double.isFinite(weight)) {
            throw new IllegalArgumentException(
                    "Utility range and weight must be finite and non-empty"
            );
        }
    }

    /**
     * Additive term for {@link UtilityAggregation#SUM}. Unbounded on purpose:
     * {@code weight} scales the curve directly and may be negative, which is
     * the only way a summed definition can express a penalty.
     */
    public double contribution(double actual) {
        if (!Double.isFinite(actual)) {
            return 0.0D;
        }
        return curve.apply(normalize(actual)) * weight;
    }

    /**
     * Multiplicative factor for {@link UtilityAggregation#PRODUCT}, always in
     * {@code [0, 1]} when {@code weight} is.
     *
     * <p>{@code weight} cannot scale the curve here the way it does for a sum:
     * multiplying by a weight below one would floor the whole intent even when
     * the consideration is fully satisfied, and one above one would let a
     * single term push the product past its own ceiling. It instead
     * interpolates between the curve and a neutral one — weight {@code 1}
     * applies the curve as written, weight {@code 0} makes the consideration
     * inert, and values between soften how hard it can pull the product down.
     *
     * <p>The veto survives that softening only at full weight, which is the
     * intended trade: a consideration that should be able to zero an intent has
     * to say so by keeping its weight at one.
     */
    public double factor(double actual) {
        if (!Double.isFinite(actual)) {
            // Each aggregation absorbs an unreadable fact into its own
            // identity: zero adds nothing to a sum, one multiplies nothing out
            // of a product. Vetoing instead would let one broken fact reader
            // silently disable every intent that consults it.
            return 1.0D;
        }
        double curved = curve.apply(normalize(actual));
        return Math.max(
                0.0D,
                Math.min(1.0D, 1.0D - weight * (1.0D - curved))
        );
    }

    private double normalize(double actual) {
        if (!Double.isFinite(actual)) {
            return 0.0D;
        }
        return Math.max(
                0.0D,
                Math.min(1.0D, (actual - minimum) / (maximum - minimum))
        );
    }
}
