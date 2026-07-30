package com.laixia.maidintelligence.kernel.feature;

import java.util.Objects;

public record FeatureId(String value) {
    public FeatureId {
        Objects.requireNonNull(value, "value");
        if (!value.matches("[a-z][a-z0-9_-]*")) {
            throw new IllegalArgumentException("Invalid feature id: " + value);
        }
    }
}
