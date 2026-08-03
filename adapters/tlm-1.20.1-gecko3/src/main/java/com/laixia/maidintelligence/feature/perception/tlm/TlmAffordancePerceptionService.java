package com.laixia.maidintelligence.feature.perception.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntitySnackCabinet;
import com.laixia.maidintelligence.feature.behavior.application.perception.DefaultAffordanceIndex;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordancePosition;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceQuery;
import com.laixia.maidintelligence.feature.behavior.domain.perception.CompanionAffordanceIds;
import com.laixia.maidintelligence.feature.behavior.port.AffordanceIndexPort;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

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
    private static final int EXAMINATION_BUDGET_PER_TICK = 256;
    private static final int SUBJECT_OBSERVATION_INTERVAL = 20;

    private final Map<ServerLevel, AffordanceIndexPort> indexes =
            new WeakHashMap<>();
    private final Map<EntityMaid, Long> nextSubjectObservation =
            new WeakHashMap<>();
    private final TlmSnackCabinetAffordanceProvider cabinets =
            new TlmSnackCabinetAffordanceProvider();
    private final TlmSeatAffordanceProvider seats =
            new TlmSeatAffordanceProvider();
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

    public List<BlockPos> querySnackCabinets(
            EntityMaid maid,
            int topK,
            long gameTime
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return List.of();
        }
        observeMaid(maid, gameTime);
        List<AffordanceCandidate> candidates = index(level).query(
                new AffordanceQuery(
                        java.util.Set.of(
                                CompanionAffordanceIds.TAKE_FOOD
                        ),
                        CompanionAffordanceIds.HUNGER_RELIEF,
                        position(maid),
                        8.0D,
                        Math.max(1, Math.min(32, topK)),
                        gameTime
                )
        );
        List<BlockPos> result = new ArrayList<>();
        for (AffordanceCandidate candidate : candidates) {
            String encoded = candidate.advertisement()
                    .attributes()
                    .get("block_pos");
            BlockPos position = parseBlockPos(encoded);
            if (position != null
                    && level.isLoaded(position)
                    && level.getBlockEntity(position)
                    instanceof TileEntitySnackCabinet) {
                result.add(position);
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
        observeMaid(maid, gameTime);
        List<AffordanceCandidate> candidates = index(level).query(
                new AffordanceQuery(
                        java.util.Set.of(
                                CompanionAffordanceIds.OCCUPY_SEAT
                        ),
                        CompanionAffordanceIds.SEATING,
                        position(maid),
                        range,
                        Math.max(1, Math.min(32, topK)),
                        gameTime
                )
        );
        LivingEntity owner = maid.getOwner();
        List<Entity> result = new ArrayList<>();
        for (AffordanceCandidate candidate : candidates) {
            Entity entity = entity(
                    level,
                    candidate.advertisement()
                            .attributes()
                            .get("entity_uuid")
            );
            if (entity != null
                    && entity.isAlive()
                    && TlmSeatAffordanceProvider.accepts(
                    entity,
                    maid,
                    owner
            )) {
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
