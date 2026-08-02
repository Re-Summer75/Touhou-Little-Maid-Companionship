package com.laixia.maidintelligence.feature.orchestration.domain.action;

public enum ActionParameterType {
    INTEGER {
        @Override
        public boolean accepts(String value) {
            try {
                Integer.parseInt(value);
                return true;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
    },
    NUMBER {
        @Override
        public boolean accepts(String value) {
            try {
                return Double.isFinite(Double.parseDouble(value));
            } catch (NumberFormatException exception) {
                return false;
            }
        }
    },
    BOOLEAN {
        @Override
        public boolean accepts(String value) {
            return "true".equals(value) || "false".equals(value);
        }
    },
    STRING {
        @Override
        public boolean accepts(String value) {
            return value != null;
        }
    };

    public abstract boolean accepts(String value);
}
