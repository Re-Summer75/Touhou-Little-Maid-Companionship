package com.laixia.maidintelligence.feature.advancement.criterion;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 「等级达到某个值」的触发器。等级与好感等级形状一样，只是注册成两个 ID。
 * 除了升级瞬间，定时对账也会用当前等级重放一次，所以用指令改等级同样算数。
 *
 * <pre>{@code
 * "conditions": { "level": { "min": 5 } }
 * }</pre>
 */
public final class MaidLevelTrigger extends SimpleCriterionTrigger<MaidLevelTrigger.Instance> {
    private final ResourceLocation id;

    public MaidLevelTrigger(ResourceLocation id) {
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
        return new Instance(id, player, MinMaxBounds.Ints.fromJson(json.get("level")));
    }

    public void trigger(ServerPlayer mirror, int level) {
        trigger(mirror, instance -> instance.matches(level));
    }

    public static final class Instance extends AbstractCriterionTriggerInstance {
        private final MinMaxBounds.Ints level;

        Instance(ResourceLocation criterion, ContextAwarePredicate player, MinMaxBounds.Ints level) {
            super(criterion, player);
            this.level = level;
        }

        boolean matches(int current) {
            return level.matches(current);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            if (!level.isAny()) {
                json.add("level", level.serializeToJson());
            }
            return json;
        }
    }
}
