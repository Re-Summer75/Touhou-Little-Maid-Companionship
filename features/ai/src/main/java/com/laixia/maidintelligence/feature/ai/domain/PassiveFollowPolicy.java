package com.laixia.maidintelligence.feature.ai.domain;

/**
 * Keeps passive following from interrupting useful work while the owner waits.
 */
public final class PassiveFollowPolicy {
    public static final PassiveFollowPolicy INSTANCE =
            new PassiveFollowPolicy();

    private PassiveFollowPolicy() {
    }

    public boolean shouldDefer(
            boolean ownerStationary,
            boolean protectedTaskActive,
            double distanceSquared,
            double followDistance,
            double teleportDistance
    ) {
        if (!ownerStationary
                || !protectedTaskActive
                || !Double.isFinite(distanceSquared)
                || followDistance < 0.0D
                || teleportDistance <= followDistance) {
            return false;
        }
        double followDistanceSquared = followDistance * followDistance;
        double teleportDistanceSquared = teleportDistance * teleportDistance;
        return distanceSquared >= followDistanceSquared
                && distanceSquared < teleportDistanceSquared;
    }
}
