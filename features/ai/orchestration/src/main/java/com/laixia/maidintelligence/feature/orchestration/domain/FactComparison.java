package com.laixia.maidintelligence.feature.orchestration.domain;

public enum FactComparison {
    LESS_THAN {
        @Override
        public boolean test(double actual, double expected) {
            return actual < expected;
        }
    },
    LESS_OR_EQUAL {
        @Override
        public boolean test(double actual, double expected) {
            return actual <= expected;
        }
    },
    EQUAL {
        @Override
        public boolean test(double actual, double expected) {
            return Double.compare(actual, expected) == 0;
        }
    },
    NOT_EQUAL {
        @Override
        public boolean test(double actual, double expected) {
            return Double.compare(actual, expected) != 0;
        }
    },
    GREATER_OR_EQUAL {
        @Override
        public boolean test(double actual, double expected) {
            return actual >= expected;
        }
    },
    GREATER_THAN {
        @Override
        public boolean test(double actual, double expected) {
            return actual > expected;
        }
    };

    public abstract boolean test(double actual, double expected);
}
