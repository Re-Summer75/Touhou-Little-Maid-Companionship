package com.laixia.maidintelligence.feature.behavior.handler;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.TlmOwnerCoordinationGroups;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmCoordinationClaims;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;

import java.util.Objects;

/**
 * Feeds loaded-world lifecycle edges into the object-centric index.
 */
public final class AffordancePerceptionHandler {
    private final TlmAffordancePerceptionService perception;

    public AffordancePerceptionHandler(
            TlmAffordancePerceptionService perception
    ) {
        this.perception = Objects.requireNonNull(
                perception,
                "perception"
        );
    }

    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getChunk() instanceof LevelChunk chunk) {
            perception.onChunkLoaded(
                    level,
                    chunk,
                    level.getGameTime()
            );
        }
    }

    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getChunk() instanceof LevelChunk chunk) {
            perception.onChunkUnloaded(level, chunk);
        }
    }

    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            perception.onBlockChanged(
                    level,
                    event.getPos(),
                    level.getGameTime()
            );
        }
    }

    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            perception.onBlockRemoved(level, event.getPos());
        }
    }

    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            perception.onEntityJoined(
                    event.getEntity(),
                    event.getLevel().getGameTime()
            );
        }
    }

    public void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            perception.onEntityLeft(event.getEntity());
            if (event.getEntity() instanceof EntityMaid maid) {
                TlmCoordinationClaims.release(maid, "entity_leave");
                TlmOwnerCoordinationGroups.release(maid);
            }
        }
    }

    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            perception.onLevelUnloaded(level);
            TlmCoordinationClaims.unload(level);
            TlmOwnerCoordinationGroups.unload(level);
        }
    }
}
