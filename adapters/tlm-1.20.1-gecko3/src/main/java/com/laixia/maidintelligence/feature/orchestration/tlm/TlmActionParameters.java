package com.laixia.maidintelligence.feature.orchestration.tlm;

import java.util.Map;

/**
 * Reads plan parameters, which arrive as strings from a data pack.
 *
 * <p>Every value is clamped rather than rejected. A plan that asks for a speed
 * of fifty is describing an intent badly, not a reason to abandon the step, and
 * an action that failed on it would leave a maid stuck with no way for the
 * author to see why.
 */
final class TlmActionParameters {
    private TlmActionParameters() {
    }

    static int integer(
            Map<String, String> parameters,
            String name,
            int fallback,
            int minimum,
            int maximum
    ) {
        try {
            int value = Integer.parseInt(parameters.getOrDefault(
                    name,
                    Integer.toString(fallback)
            ));
            return Math.max(minimum, Math.min(maximum, value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    static float number(
            Map<String, String> parameters,
            String name,
            float fallback,
            float minimum,
            float maximum
    ) {
        try {
            float value = Float.parseFloat(parameters.getOrDefault(
                    name,
                    Float.toString(fallback)
            ));
            if (!Float.isFinite(value)) {
                return fallback;
            }
            return Math.max(minimum, Math.min(maximum, value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
