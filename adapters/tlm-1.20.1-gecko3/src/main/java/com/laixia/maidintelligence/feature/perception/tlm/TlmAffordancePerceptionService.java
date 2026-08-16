package com.laixia.maidintelligence.feature.perception.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntitySnackCabinet;
import com.laixia.maidintelligence.feature.behavior.application.perception.DefaultAffordanceIndex;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordancePosition;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceQuery;
import com.laixia.maidintelligence.feature.behavior.domain.perception.CompanionAffordanceIds;
import com.laixia.maidintelligence.feature.behavior.port.AffordanceIndexPort;
import net.minecraft.core.BlockPos;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server-level object index. Querying only touches advertisements and loaded
 * targets; chunk loading is never requested.
 */
@SuppressWarnings("null")
public final class TlmAffordancePerceptionService {
    /**
     * Doubled alongside the reach below. Quadrupling the searched area without
     * giving the sweep more room to work would only mean queries running out of
     * budget further out, which reads in play as a maid intermittently failing
     * to notice things she noticed a moment ago.
     */
    private static final int EXAMINATION_BUDGET_PER_TICK = 512;

    /**
     * One shared reach for everything a maid perceives. Eight blocks was a room;
     * sixteen is a building, which is the scale she is actually expected to keep
     * house over. Seats already used this figure, so this brings food in line
     * with them rather than introducing a new number.
     */
    /** Single source: every advertiser and query is bounded by perception. */
    public static final double PERCEPTION_RANGE = PerceptionRange.BLOCKS;

    /** Cabinets re-read for stock per observation. */
    private static final int MAX_RESTOCK_CHECKS = 8;
    private static final int SUBJECT_OBSERVATION_INTERVAL = 20;

    private final Map<ServerLevel, AffordanceIndexPort> indexes =
            new WeakHashMap<>();
    private final Map<EntityMaid, Long> nextSubjectObservation =
            new WeakHashMap<>();
    private final TlmSnackCabinetAffordanceProvider cabinets =
            new TlmSnackCabinetAffordanceProvider();
    private final TlmSeatAffordanceProvider seats =
            new TlmSeatAffordanceProvider();
    private final TlmItemEntityAffordanceProvider items =
            new TlmItemEntityAffordanceProvider(
                    new TlmWeaponScanner(RangedWeaponRecognizer.NONE)
            );
    private final TlmOwnerAffordanceProvider owners =
            new TlmOwnerAffordanceProvider();

    public void observeMaid(EntityMaid maid, long gameTime) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        Long next = nextSubjectObservation.get(maid);
        if (next != null && gameTime < next) {
            return;
        }
        nextSubjectObservation.put(
                maid,
                TlmPerceptionCoordinates.deadline(
                        gameTime,
                        SUBJECT_OBSERVATION_INTERVAL
                )
        );
        AffordanceIndexPort index = index(level);
        owners.observe(maid, gameTime, index);
        seats.observeNearby(maid, gameTime, index);
        items.observeNearby(maid, gameTime, index);
        restockCabinets(maid, level, gameTime, index);
    }

    /**
     * Re-reads the stock of cabinets already known nearby.
     *
     * <p>Putting food into a cabinet raises no block event — the block does not
     * change, only its contents do — so an advertisement made when it was empty
     * would keep saying so. Rather than scan for cabinets, this asks the index
     * where it already believes they are, which costs a query and a handful of
     * lookups on the same slow clock as the rest of an observation.
     *
     * <p>An emptied cabinet advertises zero rather than withdrawing, so it is
     * still found here and can be seen to have been refilled.
     */
    private void restockCabinets(
            EntityMaid maid,
            ServerLevel level,
            long gameTime,
            AffordanceIndexPort index
    ) {
        for (AffordanceCandidate candidate : index.query(new AffordanceQuery(
                java.util.Set.of(
                        CompanionAffordanceIds.TAKE_FOOD,
                        CompanionAffordanceIds.OPEN_CONTAINER
                ),
                CompanionAffordanceIds.HUNGER_RELIEF,
                position(maid),
                PERCEPTION_RANGE,
                MAX_RESTOCK_CHECKS,
                gameTime
        ))) {
            String encoded = candidate.advertisement()
                    .attributes()
                    .get("block_pos");
            if (encoded == null) {
                continue;
            }
            cabinets.observe(
                    level,
                    BlockPos.of(Long.parseLong(encoded)),
                    gameTime,
                    index
            );
        }
    }

    public void onChunkLoaded(
            ServerLevel level,
            LevelChunk chunk,
            long gameTime
    ) {
        AffordanceIndexPort index = index(level);
        // A ChunkEvent.Load chunk is not yet safe to request through Level:
        // use its supplied block entities to avoid recursively waiting on it.
        for (Map.Entry<BlockPos, BlockEntity> entry
                : chunk.getBlockEntities().entrySet()) {
            cabinets.observeKnown(
                    level,
                    entry.getKey(),
                    entry.getValue(),
                    gameTime,
                    index
            );
        }
    }

    public void onChunkUnloaded(ServerLevel level, LevelChunk chunk) {
        AffordanceIndexPort index = index(level);
        for (BlockPos position : chunk.getBlockEntities().keySet()) {
            cabinets.remove(level, position, index);
        }
    }

    public void onBlockChanged(
            ServerLevel level,
            BlockPos position,
            long gameTime
    ) {
        cabinets.observe(level, position, gameTime, index(level));
    }

    public void onBlockRemoved(ServerLevel level, BlockPos position) {
        cabinets.remove(level, position, index(level));
    }

    public void onEntityJoined(Entity entity, long gameTime) {
        if (entity.level() instanceof ServerLevel level) {
            seats.observeKnown(entity, gameTime, index(level));
        }
    }

    public void onEntityLeft(Entity entity) {
        if (entity.level() instanceof ServerLevel level) {
            seats.remove(entity, index(level));
        }
        if (entity instanceof EntityMaid maid) {
            nextSubjectObservation.remove(maid);
        }
    }

    public void onLevelUnloaded(ServerLevel level) {
        indexes.remove(level);
    }

    /**
     * The one query every specific lookup is built on.
     *
     * <p>Observing, ranking, and the shared per-tick budget were written out
     * three times over, once per kind of thing a maid looks for, and the three
     * copies could only stay in step by being edited together. What genuinely
     * differs between them is which advertisements qualify and what is read
     * back off the winners — so that is all the callers below still say.
     *
     * <p>Because the qualifying set is an argument, a caller that gets it from
     * a data pack can point a maid at an advertiser this mod has never heard
     * of, so long as something publishes one.
     */
    public List<AffordanceCandidate> queryAffordances(
            EntityMaid maid,
            java.util.Set<OrchestrationId> affordances,
            OrchestrationId commodity,
            double range,
            int topK,
            long gameTime
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return List.of();
        }
        observeMaid(maid, gameTime);
        return index(level).query(new AffordanceQuery(
                affordances,
                commodity,
                position(maid),
                range,
                Math.max(1, Math.min(32, topK)),
                gameTime
        ));
    }
    public List<BlockPos> querySnackCabinets(
            EntityMaid maid,
            int topK,
            long gameTime
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return List.of();
        }
        List<BlockPos> result = new ArrayList<>();
        for (AffordanceCandidate candidate : queryAffordances(
                maid,
                java.util.Set.of(
                        CompanionAffordanceIds.TAKE_FOOD,
                        CompanionAffordanceIds.OPEN_CONTAINER
                ),
                CompanionAffordanceIds.HUNGER_RELIEF,
                PERCEPTION_RANGE,
                topK,
                gameTime
        )) {
            BlockPos position = parseBlockPos(
                    candidate.advertisement().attributes().get("block_pos")
            );
            // Re-checked against the world: the index is a hint, not a promise.
            if (position != null
                    && level.isLoaded(position)
                    && level.getBlockEntity(position)
                    instanceof TileEntitySnackCabinet) {
                result.add(position);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Edible items lying within reach, best first.
     *
     * <p>The counterpart to {@link #querySnackCabinets}: that one asks for food
     * behind a door she has to open, this one for food she could walk over and
     * pick up — including whatever the owner just threw at her.
     */
    /**
     * 地上够得着的武器，最好的排在前面。
     *
     * <p>与 {@link #queryLooseFood} 逐字同形，只换了要的那件商品——这正是把
     * 掉落物做成广告的意义：登记一次，谁需要什么自己去问，而不是为每一种需求
     * 各扫一遍世界。
     */
    public List<ItemEntity> queryGroundWeapons(
            EntityMaid maid,
            int topK,
            long gameTime
    ) {
        return queryLooseItems(
                maid,
                CompanionAffordanceIds.TAKE_WEAPON,
                CompanionAffordanceIds.ARMAMENT,
                topK,
                gameTime
        );
    }

    /**
     * 她会收走的东西，最近的在前。
     *
     * <p><b>这一问不走广告板，直接扫实体。</b>其余的感知走广告板，是因为它们问的
     * 是方块——柜子、椅子——那种东西每个人各扫一遍世界太贵，登记一次共享才划算。
     * 掉落物不是那种问题：它就是身边的一圈实体，原版判"附近有没有东西可捡"也是
     * 这么判的。
     *
     * <p>送进广告板的代价是量出来的：二十件散落，她收了几件之后就站着不动，读数
     * 是 {@code blocked: loose_drop_available} 占一百七十一 tick——**她确实看不见**。
     * 三道关卡叠在一起：每只女仆二十 tick 才观察一次、广告有六十 tick 的存活期、
     * 而索引每 tick 有检查预算（下限八条，还要和同一 tick 里别的查询分）。一堆
     * 随捡随消失的东西，正好把这三道关卡的短处全占了。
     *
     * <p>直接扫的代价是一次 {@code getEntitiesOfClass}，按区块分段索引，原版每
     * tick 都在做。它顺带取消了三件事：观察节流、广告过期、以及"捡走之后广告还
     * 挂着"——最后这一条曾经把索引的检查预算吃光，让她对满地的东西视而不见。
     *
     * <p>返回 {@code Entity} 而不是 {@code ItemEntity}：经验球和 P 点也在这一批
     * 里，而它们不是物品。收成一类是有意的——按距离一件件收，不分类别，否则她会
     * 为了一颗远处的经验球放着脚边的铁锭不管。
     */
    public List<Entity> queryLooseDrops(
            EntityMaid maid,
            int topK,
            long gameTime
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return List.of();
        }
        AABB bounds = maid.getBoundingBox().inflate(
                PERCEPTION_RANGE,
                PERCEPTION_RANGE / 2.0D,
                PERCEPTION_RANGE
        );
        // 先按类型和距离筛——两步都很便宜；`canPickup` 会模拟一次背包插入，
        // 那一步留到最后，且只对真正要返回的那几件做。
        List<Entity> nearby = new ArrayList<>(
                level.getEntities(maid, bounds, TlmLooseDrop::couldBeTaken)
        );
        nearby.sort(java.util.Comparator.comparingDouble(maid::distanceToSqr));
        List<Entity> found = new ArrayList<>();
        for (Entity candidate : nearby) {
            if (found.size() >= Math.max(1, topK)) {
                break;
            }
            if (TlmLooseDrop.collectable(maid, candidate)) {
                found.add(candidate);
            }
        }
        return List.copyOf(found);
    }

    public List<ItemEntity> queryLooseFood(
            EntityMaid maid,
            int topK,
            long gameTime
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return List.of();
        }
        return queryLooseItems(
                maid,
                CompanionAffordanceIds.TAKE_FOOD,
                CompanionAffordanceIds.HUNGER_RELIEF,
                topK,
                gameTime
        );
    }

    private List<ItemEntity> queryLooseItems(
            EntityMaid maid,
            com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId offer,
            com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId commodity,
            int topK,
            long gameTime
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return List.of();
        }
        List<ItemEntity> result = new ArrayList<>();
        for (AffordanceCandidate candidate : queryAffordances(
                maid,
                java.util.Set.of(offer),
                commodity,
                PERCEPTION_RANGE,
                topK,
                gameTime
        )) {
            String encoded = candidate.advertisement()
                    .attributes()
                    .get("entity_id");
            if (encoded == null) {
                // A cabinet, which cannot be picked up off the floor.
                continue;
            }
            if (level.getEntity(Integer.parseInt(encoded))
                    instanceof ItemEntity item && item.isAlive()) {
                result.add(item);
            }
        }
        return List.copyOf(result);
    }

    public List<Entity> queryCompatibleSeats(
            EntityMaid maid,
            int topK,
            double range,
            long gameTime
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return List.of();
        }
        LivingEntity owner = maid.getOwner();
        List<Entity> result = new ArrayList<>();
        for (AffordanceCandidate candidate : queryAffordances(
                maid,
                java.util.Set.of(CompanionAffordanceIds.OCCUPY_SEAT),
                CompanionAffordanceIds.SEATING,
                range,
                topK,
                gameTime
        )) {
            Entity entity = entity(
                    level,
                    candidate.advertisement().attributes().get("entity_uuid")
            );
            if (entity != null
                    && entity.isAlive()
                    && TlmSeatAffordanceProvider.accepts(entity, maid, owner)) {
                result.add(entity);
            }
        }
        return List.copyOf(result);
    }

    public int indexedTargets(ServerLevel level) {
        return index(level).size();
    }

    private AffordanceIndexPort index(ServerLevel level) {
        return indexes.computeIfAbsent(
                level,
                ignored -> new DefaultAffordanceIndex(
                        () -> EXAMINATION_BUDGET_PER_TICK
                )
        );
    }

    private static AffordancePosition position(Entity entity) {
        return TlmPerceptionCoordinates.at(entity);
    }

    private static BlockPos parseBlockPos(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            return BlockPos.of(Long.parseLong(encoded));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Entity entity(ServerLevel level, String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            return level.getEntity(UUID.fromString(encoded));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
