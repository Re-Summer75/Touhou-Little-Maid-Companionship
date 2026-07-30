package com.laixia.maidintelligence.feature.level.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.level.api.ExperienceSource;
import com.laixia.maidintelligence.feature.level.api.LevelChange;
import com.laixia.maidintelligence.feature.level.api.MaidLevelApi;
import com.laixia.maidintelligence.feature.level.domain.LevelCurve;
import com.laixia.maidintelligence.feature.level.domain.LevelProgress;

import java.util.Objects;

public final class DefaultMaidLevelService implements MaidLevelApi {
    private final MaidLevelStore store;
    private final LevelCurve curve;
    private final LevelNotificationPort notification;

    public DefaultMaidLevelService(
            MaidLevelStore store,
            LevelCurve curve,
            LevelNotificationPort notification
    ) {
        this.store = Objects.requireNonNull(store);
        this.curve = Objects.requireNonNull(curve);
        this.notification = Objects.requireNonNull(notification);
    }

    @Override
    public LevelProgress getProgress(EntityMaid maid) {
        return store.get(maid);
    }

    @Override
    public LevelChange awardExperience(EntityMaid maid, int amount, ExperienceSource source) {
        if (amount < 0) {
            throw new IllegalArgumentException("experience amount must not be negative");
        }

        LevelProgress before = getProgress(maid);
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
            store.set(maid, after);
        }
        if (after.level() > before.level()) {
            notification.notifyLevelUp(maid, before.level(), after.level());
        }
        return new LevelChange(before, after, amount, source);
    }

    @Override
    public LevelProgress setProgress(EntityMaid maid, int level, int experience) {
        int boundedLevel = Math.max(1, Math.min(curve.maxLevel(), level));
        int boundedExperience = boundedLevel == curve.maxLevel()
                ? 0
                : Math.max(0, Math.min(experience, curve.experienceRequiredForNextLevel(boundedLevel) - 1));
        LevelProgress before = getProgress(maid);
        LevelProgress after = new LevelProgress(boundedLevel, boundedExperience);

        if (!after.equals(before)) {
            store.set(maid, after);
        }
        if (after.level() > before.level()) {
            notification.notifyLevelUp(maid, before.level(), after.level());
        }
        return after;
    }
}
