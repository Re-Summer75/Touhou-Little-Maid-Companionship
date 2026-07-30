package com.laixia.maidintelligence.feature.level;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.laixia.maidintelligence.feature.level.application.DefaultMaidLevelService;
import com.laixia.maidintelligence.feature.level.api.ExperienceSource;
import com.laixia.maidintelligence.feature.level.domain.DefaultLevelCurve;
import com.laixia.maidintelligence.feature.level.domain.LevelProgress;
import com.laixia.maidintelligence.feature.level.port.MaidLevelStore;
import com.laixia.maidintelligence.feature.level.codec.LevelProgressCodec;

public final class LevelDomainVerification {
    private LevelDomainVerification() {
    }

    public static void main(String[] args) {
        verifiesCodecRoundTrip();
        verifiesMultiLevelProgression();
        verifiesMaximumLevelClamp();
        System.out.println("Level domain verification passed.");
    }

    private static void verifiesCodecRoundTrip() {
        LevelProgress expected = new LevelProgress(7, 42);
        JsonElement encoded = LevelProgressCodec.CODEC.encodeStart(JsonOps.INSTANCE, expected)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        LevelProgress decoded = LevelProgressCodec.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        require(expected.equals(decoded), "Codec round trip changed level progress");
    }

    private static void verifiesMultiLevelProgression() {
        InMemoryStore store = new InMemoryStore();
        Object maid = new Object();
        int[] notification = new int[2];
        DefaultMaidLevelService<Object> service = new DefaultMaidLevelService<>(
                store,
                DefaultLevelCurve.INSTANCE,
                (subject, oldLevel, newLevel) -> {
                    notification[0] = oldLevel;
                    notification[1] = newLevel;
                }
        );

        service.awardExperience(maid, 200, ExperienceSource.COMMAND);
        require(store.progress.equals(new LevelProgress(3, 75)), "Unexpected multi-level result");
        require(notification[0] == 1 && notification[1] == 3, "Unexpected level-up notification");
    }

    private static void verifiesMaximumLevelClamp() {
        InMemoryStore store = new InMemoryStore();
        Object maid = new Object();
        DefaultMaidLevelService<Object> service = new DefaultMaidLevelService<>(
                store,
                DefaultLevelCurve.INSTANCE,
                (subject, oldLevel, newLevel) -> {
                }
        );

        service.awardExperience(maid, Integer.MAX_VALUE, ExperienceSource.COMMAND);
        require(
                store.progress.equals(new LevelProgress(DefaultLevelCurve.MAX_LEVEL, 0)),
                "Maximum level was not clamped"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class InMemoryStore implements MaidLevelStore<Object> {
        private LevelProgress progress = LevelProgress.initial();

        @Override
        public LevelProgress get(Object maid) {
            return progress;
        }

        @Override
        public void set(Object maid, LevelProgress progress) {
            this.progress = progress;
        }
    }
}
