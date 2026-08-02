package com.laixia.maidintelligence.feature.orchestration.domain;

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

    public double contribution(double actual) {
        if (!Double.isFinite(actual)) {
            return 0.0D;
        }
        double normalized = Math.max(
                0.0D,
                Math.min(1.0D, (actual - minimum) / (maximum - minimum))
        );
        return curve.apply(normalized) * weight;
    }
}
