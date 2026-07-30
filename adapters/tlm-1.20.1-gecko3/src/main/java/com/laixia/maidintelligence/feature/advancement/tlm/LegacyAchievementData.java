package com.laixia.maidintelligence.feature.advancement.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * 旧自定义成就进度的只读入口。键还得注册着，否则老存档里的 {@code achievement_progress}
 * 读不出来也就没法迁移；迁移完会写回空值，让它自然从存档里消失。
 */
public final class LegacyAchievementData {
    /** 只保留迁移用得上的两个字段，多余字段由 Codec 直接忽略。 */
    public record Progress(Map<ResourceLocation, Integer> counters, Map<ResourceLocation, Long> unlocked) {
        public static final Codec<Progress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("counters", Map.of())
                        .forGetter(Progress::counters),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG).optionalFieldOf("unlocked", Map.of())
                        .forGetter(Progress::unlocked)
        ).apply(instance, Progress::new));

        private static final Progress EMPTY = new Progress(Map.of(), Map.of());

        public static Progress empty() {
            return EMPTY;
        }

        public boolean isEmpty() {
            return counters.isEmpty() && unlocked.isEmpty();
        }

        public int counter(ResourceLocation achievement) {
            return counters.getOrDefault(achievement, 0);
        }

        public boolean isUnlocked(ResourceLocation achievement) {
            return unlocked.containsKey(achievement);
        }
    }

    private static TaskDataKey<Progress> key;

    private LegacyAchievementData() {
    }

    public static void register(TaskDataRegister register) {
        if (key != null) {
            throw new IllegalStateException("Legacy achievement task data has already been registered");
        }
        key = register.register(ModResources.id("achievement_progress"), Progress.CODEC);
    }

    public static Progress get(EntityMaid maid) {
        if (key == null) {
            return Progress.empty();
        }
        Progress progress = maid.getData(key);
        return progress != null ? progress : Progress.empty();
    }

    public static void clear(EntityMaid maid) {
        if (key != null && !maid.level().isClientSide()) {
            maid.setAndSyncData(key, Progress.empty());
        }
    }
}
