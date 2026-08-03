package com.laixia.maidintelligence.feature.ai.domain;

import com.laixia.maidintelligence.feature.ai.domain.arbitration.MovementIntentAuthority;

/**
 * Whitelisted movement writers with cross-system authority and Brain priority.
 *
 * <p>Authority is compared before the numeric priority. Unknown or addon-owned
 * writers remain unmanaged and therefore force fail-open reconciliation.</p>
 */
public enum MovementIntentSource {
    BREATH_AIR(MovementIntentAuthority.EMERGENCY, 0),
    COMBAT(MovementIntentAuthority.EMERGENCY, 2),
    HOME_RETURN(MovementIntentAuthority.NATIVE_COMMITMENT, 1),
    BUILT_IN_WORK(MovementIntentAuthority.NATIVE_COMMITMENT, 5),
    STEAL_EDIBLE(MovementIntentAuthority.NATIVE_COMMITMENT, 8),
    PICKUP(MovementIntentAuthority.NATIVE_COMMITMENT, 10),
    OWNER_COMMAND(MovementIntentAuthority.OWNER_COMMAND, 4),
    FOLLOW_OWNER(MovementIntentAuthority.NATIVE_SOFT, 3),
    FOLLOW_OWNER_VEHICLE(MovementIntentAuthority.NATIVE_SOFT, 3),
    LEISURE(MovementIntentAuthority.NATIVE_SOFT, 7),
    BEG(MovementIntentAuthority.NATIVE_SOFT, 6),
    RANDOM_STROLL(MovementIntentAuthority.NATIVE_SOFT, 20),
    COMPANION(MovementIntentAuthority.PASSIVE_COMPANION, 4);

    private final MovementIntentAuthority authority;
    private final int priority;

    MovementIntentSource(
            MovementIntentAuthority authority,
            int priority
    ) {
        this.authority = authority;
        this.priority = priority;
    }

    public MovementIntentAuthority authority() {
        return authority;
    }

    public int priority() {
        return priority;
    }
}
