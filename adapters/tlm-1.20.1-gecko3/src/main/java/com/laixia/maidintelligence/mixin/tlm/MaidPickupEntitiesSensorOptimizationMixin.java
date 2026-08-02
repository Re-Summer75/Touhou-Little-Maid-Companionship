package com.laixia.maidintelligence.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.sensor.MaidPickupEntitiesSensor;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.ai.api.MaidAiOptimizationApi;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;

import static com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask.VERTICAL_SEARCH_RANGE;

@Mixin(value = MaidPickupEntitiesSensor.class, remap = false)
public abstract class MaidPickupEntitiesSensorOptimizationMixin {
    @Unique
    private EntityMaid maidIntelligence$sortingMaid;

    @Unique
    private final Comparator<Entity> maidIntelligence$distanceComparator =
            (left, right) -> Double.compare(
                    maidIntelligence$sortingMaid.distanceToSqr(left),
                    maidIntelligence$sortingMaid.distanceToSqr(right)
            );

    @Unique
    private MaidAiOptimizationApi maidIntelligence$ai;

    @Inject(
            method = "doTick(Lnet/minecraft/server/level/ServerLevel;"
                    + "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/"
                    + "EntityMaid;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void maidIntelligence$scanWithoutCollector(
            ServerLevel level,
            EntityMaid maid,
            CallbackInfo callback
    ) {
        MaidAiOptimizationApi ai = maidIntelligence$ai();
        if (!ai.enabled()) {
            return;
        }
        callback.cancel();
        if (!maid.isTame()) {
            return;
        }

        float radius = maid.getRestrictRadius();
        AABB searchBounds = maid.hasRestriction()
                ? new AABB(maid.getRestrictCenter()).inflate(
                        radius,
                        VERTICAL_SEARCH_RANGE,
                        radius
                )
                : maid.getBoundingBox().inflate(
                        radius,
                        VERTICAL_SEARCH_RANGE,
                        radius
                );
        List<Entity> entities = level.getEntitiesOfClass(
                Entity.class,
                searchBounds,
                Entity::isAlive
        );
        maidIntelligence$sortingMaid = maid;
        try {
            entities.sort(maidIntelligence$distanceComparator);
        } finally {
            maidIntelligence$sortingMaid = null;
        }

        int candidateCount = entities.size();
        int selectedCount = 0;
        for (int readIndex = 0; readIndex < candidateCount; readIndex++) {
            Entity entity = entities.get(readIndex);
            if (maid.canPickup(entity, true)
                    && entity.closerThan(maid, radius + 1)
                    && maid.isWithinRestriction(entity.blockPosition())
                    && maid.hasLineOfSight(entity)) {
                entities.set(selectedCount++, entity);
            }
        }
        for (int index = candidateCount - 1; index >= selectedCount; index--) {
            entities.remove(index);
        }

        maid.getBrain().setMemory(
                InitEntities.VISIBLE_PICKUP_ENTITIES.get(),
                entities
        );
        ai.recordPickupScan(candidateCount, selectedCount);
    }

    @Unique
    private MaidAiOptimizationApi maidIntelligence$ai() {
        MaidAiOptimizationApi current = maidIntelligence$ai;
        if (current == null) {
            current = AdapterRuntime.require(MaidAiOptimizationApi.class);
            maidIntelligence$ai = current;
        }
        return current;
    }
}
