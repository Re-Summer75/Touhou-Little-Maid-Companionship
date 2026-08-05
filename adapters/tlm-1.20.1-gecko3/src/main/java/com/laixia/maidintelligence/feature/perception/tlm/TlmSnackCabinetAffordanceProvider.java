package com.laixia.maidintelligence.feature.perception.tlm;

import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntitySnackCabinet;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceAdvertisement;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceTargetId;
import com.laixia.maidintelligence.feature.behavior.domain.perception.CompanionAffordanceIds;
import com.laixia.maidintelligence.feature.behavior.port.AffordanceIndexPort;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Publishes cabinet location on chunk/block state events; item eligibility is
 * deliberately revalidated only for top-K query results.
 */
@SuppressWarnings("null")
final class TlmSnackCabinetAffordanceProvider {
    private final Map<AffordanceTargetId, Long> revisions =
            new HashMap<>();
    private long nextRevision = 1L;

    void observe(
            ServerLevel level,
            BlockPos position,
            long gameTime,
            AffordanceIndexPort index
    ) {
        if (!level.isLoaded(position)) {
            remove(level, position, index);
            return;
        }
        observeKnown(
                level,
                position,
                level.getBlockEntity(position),
                gameTime,
                index
        );
    }

    void observeKnown(
            ServerLevel level,
            BlockPos position,
            BlockEntity blockEntity,
            long gameTime,
            AffordanceIndexPort index
    ) {
        if (!(blockEntity instanceof TileEntitySnackCabinet cabinet)) {
            remove(level, position, index);
            return;
        }
        AffordanceTargetId target = target(level, position);
        long revision = nextRevision++;
        AffordanceAdvertisement advertisement =
                new AffordanceAdvertisement(
                        target,
                        Set.of(
                                CompanionAffordanceIds.TAKE_FOOD,
                                CompanionAffordanceIds.OPEN_CONTAINER
                        ),
                        Map.of(
                                CompanionAffordanceIds.HUNGER_RELIEF,
                                /*
                                 * Read from the stock rather than asserted.
                                 * A flat 1.0 made every cabinet identical, so
                                 * ranking collapsed to distance and she walked
                                 * to the nearest one whether or not it held
                                 * anything.
                                 */
                                TlmHungerCommodity.of(cabinet, null)
                        ),
                        TlmPerceptionCoordinates.at(level, position),
                        revision,
                        gameTime,
                        Long.MAX_VALUE,
                        Map.of(
                                "block_pos",
                                Long.toString(position.asLong())
                        )
                );
        if (index.upsert(advertisement)) {
            revisions.put(target, revision);
        }
    }

    void remove(
            ServerLevel level,
            BlockPos position,
            AffordanceIndexPort index
    ) {
        AffordanceTargetId target = target(level, position);
        Long revision = revisions.remove(target);
        if (revision != null) {
            index.remove(target, revision);
        }
    }

    private static AffordanceTargetId target(
            ServerLevel level,
            BlockPos position
    ) {
        return new AffordanceTargetId(
                "tlm_snack_cabinet",
                TlmPerceptionCoordinates.dimension(level)
                        + "/" + position.asLong()
        );
    }
}
