package com.laixia.maidintelligence.feature.level.application;

import com.laixia.maidintelligence.feature.level.api.ExperienceSource;
import com.laixia.maidintelligence.feature.level.api.LevelChange;
import com.laixia.maidintelligence.feature.level.api.MaidLevelApi;
import com.laixia.maidintelligence.feature.level.domain.LevelCurve;
import com.laixia.maidintelligence.feature.level.domain.LevelProgress;
import com.laixia.maidintelligence.feature.level.port.LevelNotificationPort;
import com.laixia.maidintelligence.feature.level.port.MaidLevelStore;

import java.util.Objects;

public final class DefaultMaidLevelService<S> implements MaidLevelApi<S> {
    private final MaidLevelStore<S> store;
    private final LevelCurve curve;
    private final LevelNotificationPort<S> notification;

    public DefaultMaidLevelService(
            MaidLevelStore<S> store,
            LevelCurve curve,
            LevelNotificationPort<S> notification
    ) {
        this.store = Objects.requireNonNull(store);
        this.curve = Objects.requireNonNull(curve);
        this.notification = Objects.requireNonNull(notification);
    }

    @Override
    public LevelProgress getProgress(S subject) {
        return store.get(subject);
    }

    @Override
    public LevelChange awardExperience(S subject, int amount, ExperienceSource source) {
        if (amount < 0) {
            throw new IllegalArgumentException("experience amount must not be negative");
        }

        LevelProgress before = getProgress(subject);
        int level = before.level();
        long experience = (long) before.experience() + amount;

        while (level < curve.maxLevel()) {
            int required = curve.experienceRequiredForNextLevel(level);
            if (experience < required) {
                break;
            }
            experience -= required;
            level++;
        }

        if (level >= curve.maxLevel()) {
            level = curve.maxLevel();
            experience = 0;
        }

        LevelProgress after = new LevelProgress(level, Math.toIntExact(experience));
        if (!after.equals(before)) {
            store.set(subject, after);
        }
        if (after.level() > before.level()) {
            notification.notifyLevelUp(subject, before.level(), after.level());
        }
        return new LevelChange(before, after, amount, source);
    }

    @Override
    public LevelProgress setProgress(S subject, int level, int experience) {
        int boundedLevel = Math.max(1, Math.min(curve.maxLevel(), level));
        int boundedExperience = boundedLevel == curve.maxLevel()
                ? 0
                : Math.max(
                        0,
                        Math.min(
                                experience,
                                curve.experienceRequiredForNextLevel(boundedLevel) - 1
                        )
                );
        LevelProgress before = getProgress(subject);
        LevelProgress after = new LevelProgress(boundedLevel, boundedExperience);

        if (!after.equals(before)) {
            store.set(subject, after);
        }
        if (after.level() > before.level()) {
            notification.notifyLevelUp(subject, before.level(), after.level());
        }
        return after;
    }
}
