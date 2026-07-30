package com.laixia.maidintelligence.feature.advancement.criterion;

import com.google.gson.JsonObject;
import com.laixia.maidintelligence.feature.advancement.domain.ItemId;
import com.laixia.maidintelligence.feature.advancement.domain.MaidStatistics;
import com.laixia.maidintelligence.feature.advancement.codec.MinecraftResourceIds;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * 玩家亲手喂食女仆时触发。{@code count} 比的是累计次数而不是这一次的数量：
 * 带 {@code item} 时只累计匹配该谓词的食物，不带时累计全部喂食。
 *
 * <pre>{@code
 * "conditions": { "item": { "items": ["minecraft:cake"] }, "count": { "min": 8 } }
 * }</pre>
 */
public final class MaidFedTrigger extends SimpleCriterionTrigger<MaidFedTrigger.Instance> {
    private final ResourceLocation id;

    public MaidFedTrigger(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    protected Instance createInstance(
            JsonObject json,
            ContextAwarePredicate player,
            DeserializationContext context
    ) {
        return new Instance(
                id,
                player,
                ItemPredicate.fromJson(json.get("item")),
                MinMaxBounds.Ints.fromJson(json.get("count"))
        );
    }

    public void trigger(ServerPlayer mirror, ItemStack food, MaidStatistics statistics) {
        trigger(mirror, instance -> instance.matches(food, statistics));
    }

    public static final class Instance extends AbstractCriterionTriggerInstance {
        private final ItemPredicate item;
        private final MinMaxBounds.Ints count;

        Instance(
                ResourceLocation criterion,
                ContextAwarePredicate player,
                ItemPredicate item,
                MinMaxBounds.Ints count
        ) {
            super(criterion, player);
            this.item = item;
            this.count = count;
        }

        boolean matches(ItemStack food, MaidStatistics statistics) {
            if (!item.matches(food)) {
                return false;
            }
            return count.matches(item == ItemPredicate.ANY
                    ? statistics.feedCount()
                    : matchingFeeds(statistics));
        }

        /**
         * 谓词可能写成物品标签，所以只能拿统计里出现过的物品逐个回问谓词。
         */
        private int matchingFeeds(MaidStatistics statistics) {
            int matched = 0;
            for (Map.Entry<ItemId, Integer> entry
                    : statistics.feedByItem().entrySet()) {
                Item fed = BuiltInRegistries.ITEM.get(
                        MinecraftResourceIds.toMinecraft(
                                entry.getKey()
                        )
                );
                if (item.matches(new ItemStack(fed))) {
                    matched += entry.getValue();
                }
            }
            return matched;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            if (item != ItemPredicate.ANY) {
                json.add("item", item.serializeToJson());
            }
            if (!count.isAny()) {
                json.add("count", count.serializeToJson());
            }
            return json;
        }
    }
}
