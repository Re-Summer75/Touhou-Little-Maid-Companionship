package com.laixia.maidintelligence.feature.advancement.criterion;

import com.google.gson.JsonObject;
import com.laixia.maidintelligence.feature.advancement.domain.MaidStatistics;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 女仆累计获得的等级经验达到阈值时触发，比的是统计量而不是单次经验球。
 *
 * <pre>{@code
 * "conditions": { "experience": { "min": 1000 } }
 * }</pre>
 */
public final class MaidExperienceTrigger extends SimpleCriterionTrigger<MaidExperienceTrigger.Instance> {
    private final ResourceLocation id;

    public MaidExperienceTrigger(ResourceLocation id) {
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
        return new Instance(id, player, MinMaxBounds.Ints.fromJson(json.get("experience")));
    }

    public void trigger(ServerPlayer mirror, MaidStatistics statistics) {
        trigger(mirror, instance -> instance.matches(statistics));
    }

    public static final class Instance extends AbstractCriterionTriggerInstance {
        private final MinMaxBounds.Ints experience;

        Instance(ResourceLocation criterion, ContextAwarePredicate player, MinMaxBounds.Ints experience) {
            super(criterion, player);
            this.experience = experience;
        }

        boolean matches(MaidStatistics statistics) {
            return experience.matches(statistics.experienceGained());
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            if (!experience.isAny()) {
                json.add("experience", experience.serializeToJson());
            }
            return json;
        }
    }
}
