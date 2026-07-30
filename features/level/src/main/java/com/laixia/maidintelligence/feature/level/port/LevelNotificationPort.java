package com.laixia.maidintelligence.feature.level.port;

/**
 * Publishes a level-up fact without coupling the application service to its transport.
 */
@FunctionalInterface
public interface LevelNotificationPort<S> {
    void notifyLevelUp(S subject, int oldLevel, int newLevel);
}
