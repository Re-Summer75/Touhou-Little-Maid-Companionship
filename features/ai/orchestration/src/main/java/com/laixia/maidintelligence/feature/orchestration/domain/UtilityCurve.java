package com.laixia.maidintelligence.feature.orchestration.domain;

public enum UtilityCurve {
    LINEAR {
        @Override
        public double apply(double normalized) {
            return normalized;
        }
    },
    INVERSE_LINEAR {
        @Override
        public double apply(double normalized) {
            return 1.0D - normalized;
        }
    },
    STEP {
        @Override
        public double apply(double normalized) {
            return normalized >= 1.0D ? 1.0D : 0.0D;
        }
    };

    public abstract double apply(double normalized);
}
