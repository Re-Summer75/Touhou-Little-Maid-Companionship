package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.api.CoordinationClaimService;
import com.laixia.maidintelligence.feature.orchestration.application.claim.DefaultCoordinationClaimService;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationResourceKey;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationResourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * ServerLevel-scoped claim directory; movement leases remain per-maid and do
 * not enter this registry.
 */
public final class TlmCoordinationClaims {
    private static final Map<ServerLevel, CoordinationClaimService> SERVICES =
            new WeakHashMap<>();

    private TlmCoordinationClaims() {
    }

    public static synchronized CoordinationClaimService service(
            ServerLevel level
    ) {
        return SERVICES.computeIfAbsent(
                level,
                ignored -> new DefaultCoordinationClaimService()
        );
    }

    public static CoordinationResourceKey containerSlot(
            ServerLevel level,
            BlockPos position,
            int slot
    ) {
        return key(
                level,
                CoordinationResourceType.CONTAINER_SLOT,
                position.asLong() + "/" + slot
        );
    }

    public static CoordinationResourceKey seat(
            ServerLevel level,
            Entity entity,
            int seatIndex
    ) {
        return key(
                level,
                CoordinationResourceType.SEAT,
                entity.getUUID() + "/" + seatIndex
        );
    }

    public static CoordinationResourceKey itemEntity(
            ServerLevel level,
            Entity item
    ) {
        return key(
                level,
                CoordinationResourceType.ITEM_ENTITY,
                item.getUUID().toString()
        );
    }

    public static CoordinationResourceKey placement(
            ServerLevel level,
            BlockPos position
    ) {
        return key(
                level,
                CoordinationResourceType.PLACEMENT_POINT,
                Long.toString(position.asLong())
        );
    }

    public static CoordinationResourceKey request(
            ServerLevel level,
            UUID requestId
    ) {
        return key(
                level,
                CoordinationResourceType.REQUEST,
                requestId.toString()
        );
    }

    public static UUID operation(EntityMaid maid, String purpose) {
        return UUID.nameUUIDFromBytes(
                (maid.getUUID() + "\u001f" + purpose)
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    public static synchronized void release(EntityMaid maid, String reason) {
        if (maid.level() instanceof ServerLevel level) {
            CoordinationClaimService service = SERVICES.get(level);
            if (service != null) {
                service.releaseHolder(
                        maid.getUUID(),
                        level.getGameTime(),
                        reason
                );
            }
        }
    }

    public static synchronized void unload(ServerLevel level) {
        CoordinationClaimService service = SERVICES.remove(level);
        if (service != null) {
            service.invalidateEpoch(level.getGameTime(), "level_unload");
        }
    }

    public static synchronized void reload() {
        for (Map.Entry<ServerLevel, CoordinationClaimService> entry
                : SERVICES.entrySet()) {
            entry.getValue().invalidateEpoch(
                    entry.getKey().getGameTime(),
                    "data_reload"
            );
        }
    }

    private static CoordinationResourceKey key(
            ServerLevel level,
            CoordinationResourceType type,
            String value
    ) {
        return new CoordinationResourceKey(
                level.dimension().location().toString(),
                type,
                value
        );
    }
}
