package com.laixia.maidintelligence.feature.orchestration.domain.fact;

public sealed interface FactValue
        permits FactValue.BooleanValue,
        FactValue.NumberValue,
        FactValue.SignalValue {
    FactType type();

    double encoded();

    record BooleanValue(boolean value) implements FactValue {
        @Override
        public FactType type() {
            return FactType.BOOLEAN;
        }

        @Override
        public double encoded() {
            return value ? 1.0D : 0.0D;
        }
    }

    record NumberValue(double value) implements FactValue {
        public NumberValue {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Numeric fact value must be finite"
                );
            }
        }

        @Override
        public FactType type() {
            return FactType.NUMBER;
        }

        @Override
        public double encoded() {
            return value;
        }
    }

    record SignalValue(boolean active) implements FactValue {
        @Override
        public FactType type() {
            return FactType.SIGNAL;
        }

        @Override
        public double encoded() {
            return active ? 1.0D : 0.0D;
        }
    }
}
