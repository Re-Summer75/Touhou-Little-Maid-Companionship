package com.laixia.maidintelligence.feature.advancement.codec;

import com.laixia.maidintelligence.feature.advancement.domain.ItemId;
import com.laixia.maidintelligence.feature.advancement.domain.MaidStatistics;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mojang serialization stays in the TLM adapter while preserving TaskData
 * field names and defaults.
 */
public final class MaidStatisticsCodec {
    private static final Codec<Map<ItemId, Integer>> ITEM_COUNTS =
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                    .xmap(
                            MaidStatisticsCodec::toCoreItemCounts,
                            MaidStatisticsCodec::toMinecraftItemCounts
                    );

    public static final Codec<MaidStatistics> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("feed_count", 0)
                            .forGetter(MaidStatistics::feedCount),
                    ITEM_COUNTS.optionalFieldOf(
                            "feed_by_item",
                            Map.of()
                    ).forGetter(MaidStatistics::feedByItem),
                    Codec.INT.optionalFieldOf(
                            "experience_gained",
                            0
                    ).forGetter(MaidStatistics::experienceGained)
            ).apply(instance, MaidStatistics::new));

    private MaidStatisticsCodec() {
    }

    private static Map<ItemId, Integer> toCoreItemCounts(
            Map<ResourceLocation, Integer> source
    ) {
        Map<ItemId, Integer> converted = new LinkedHashMap<>();
        source.forEach((id, count) -> converted.put(
                new ItemId(MinecraftResourceIds.toCore(id)),
                count
        ));
        return converted;
    }

    private static Map<ResourceLocation, Integer> toMinecraftItemCounts(
            Map<ItemId, Integer> source
    ) {
        Map<ResourceLocation, Integer> converted =
                new LinkedHashMap<>();
        source.forEach((id, count) -> converted.put(
                MinecraftResourceIds.toMinecraft(id),
                count
        ));
        return converted;
    }
}
