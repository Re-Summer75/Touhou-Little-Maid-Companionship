package com.laixia.maidintelligence.feature.ai.domain;

/**
 * Whitelisted TLM movement writers in their native Brain priority order.
 *
 * <p>Lower values have higher priority, matching Minecraft Brain behavior
 * registration. Unknown or addon-owned writers are intentionally absent and
 * therefore remain unmanaged. A pickup that already owns the lease has one
 * bounded exception: normal following waits for that pickup commitment.</p>
 */
public enum MovementIntentSource {
    BREATH_AIR(0),
    HOME_RETURN(1),
    COMBAT(2),
    FOLLOW_OWNER(3),
    COMPANION(4),
    BUILT_IN_WORK(5),
    BEG(6),
    STEAL_EDIBLE(8),
    PICKUP(10);

    private final int priority;

    MovementIntentSource(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
