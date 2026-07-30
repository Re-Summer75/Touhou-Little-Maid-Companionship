package com.laixia.maidintelligence.feature.level;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.laixia.maidintelligence.feature.level.api.ExperienceSource;
import com.laixia.maidintelligence.feature.level.domain.DefaultLevelCurve;
import com.laixia.maidintelligence.feature.level.domain.LevelProgress;
import com.laixia.maidintelligence.feature.level.service.DefaultMaidLevelService;
import com.laixia.maidintelligence.feature.level.service.MaidLevelStore;

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
        JsonElement encoded = LevelProgress.CODEC.encodeStart(JsonOps.INSTANCE, expected)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        LevelProgress decoded = LevelProgress.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        require(expected.equals(decoded), "Codec round trip changed level progress");
    }

    private static void verifiesMultiLevelProgression() {
        InMemoryStore store = new InMemoryStore();
        int[] notification = new int[2];
        DefaultMaidLevelService service = new DefaultMaidLevelService(
                store,
                DefaultLevelCurve.INSTANCE,
                (maid, oldLevel, newLevel) -> {
                    notification[0] = oldLevel;
                    notification[1] = newLevel;
                }
        );

        service.awardExperience(null, 200, ExperienceSource.COMMAND);
        require(store.progress.equals(new LevelProgress(3, 75)), "Unexpected multi-level result");
        require(notification[0] == 1 && notification[1] == 3, "Unexpected level-up notification");
    }

    private static void verifiesMaximumLevelClamp() {
        InMemoryStore store = new InMemoryStore();
        DefaultMaidLevelService service = new DefaultMaidLevelService(
                store,
                DefaultLevelCurve.INSTANCE,
                (maid, oldLevel, newLevel) -> {
                }
        );

        service.awardExperience(null, Integer.MAX_VALUE, ExperienceSource.COMMAND);
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

    private static final class InMemoryStore implements MaidLevelStore {
        private LevelProgress progress = LevelProgress.initial();

        @Override
        public LevelProgress get(EntityMaid maid) {
            return progress;
        }

        @Override
        public void set(EntityMaid maid, LevelProgress progress) {
            this.progress = progress;
        }
    }
}
