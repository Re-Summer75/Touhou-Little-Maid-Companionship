package com.laixia.maidintelligence.feature.perception.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.mixin.accessor.EntityAccessor;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceAdvertisement;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceTargetId;
import com.laixia.maidintelligence.feature.behavior.domain.perception.CompanionAffordanceIds;
import com.laixia.maidintelligence.feature.behavior.port.AffordanceIndexPort;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Discovers any loaded entity whose passenger contract accepts the maid or
 * owner, rather than hard-coding command seating to one entity type.
 */
@SuppressWarnings("null")
final class TlmSeatAffordanceProvider {
    private static final double OBSERVATION_RANGE = PerceptionRange.BLOCKS;
    private static final int MAX_NEARBY_ENTITIES = 32;

    private final Map<AffordanceTargetId, Long> revisions =
            new HashMap<>();
    private long nextRevision = 1L;

    void observeNearby(
            EntityMaid maid,
            long gameTime,
            AffordanceIndexPort index
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity owner = maid.getOwner();
        AABB bounds = maid.getBoundingBox().inflate(
                OBSERVATION_RANGE,
                4.0D,
                OBSERVATION_RANGE
        );
        level.getEntities(
                maid,
                bounds,
                entity -> entity.isAlive() && entity != owner
        ).stream()
                .sorted(Comparator.comparingDouble(maid::distanceToSqr))
                .filter(entity -> accepts(entity, maid, owner))
                .limit(MAX_NEARBY_ENTITIES)
                .forEach(entity -> advertise(
                        entity,
                        gameTime,
                        index
                ));
    }

    void observeKnown(
            Entity entity,
            long gameTime,
            AffordanceIndexPort index
    ) {
        if (knownSeat(entity)) {
            advertise(entity, gameTime, index);
        }
    }

    void remove(Entity entity, AffordanceIndexPort index) {
        AffordanceTargetId target = target(entity);
        Long revision = revisions.remove(target);
        if (revision != null) {
            index.remove(target, revision);
        }
    }

    private void advertise(
            Entity entity,
            long gameTime,
            AffordanceIndexPort index
    ) {
        if (!(entity.level() instanceof ServerLevel)) {
            return;
        }
        AffordanceTargetId target = target(entity);
        long revision = nextRevision++;
        boolean transport = entity instanceof Boat;
        Set<OrchestrationId> affordances = transport
                ? Set.of(
                        CompanionAffordanceIds.OCCUPY_SEAT,
                        CompanionAffordanceIds.RIDE_VEHICLE
                )
                : Set.of(CompanionAffordanceIds.OCCUPY_SEAT);
        Map<OrchestrationId, Double> commodities = transport
                ? Map.of(
                        CompanionAffordanceIds.SEATING,
                        1.0D,
                        CompanionAffordanceIds.TRANSPORT,
                        1.0D
                )
                : Map.of(CompanionAffordanceIds.SEATING, 1.0D);
        if (index.upsert(new AffordanceAdvertisement(
                target,
                affordances,
                commodities,
                TlmPerceptionCoordinates.at(entity),
                revision,
                gameTime,
                TlmPerceptionCoordinates.deadline(gameTime, 60L),
                Map.of(
                        "entity_uuid",
                        entity.getUUID().toString(),
                        "entity_type",
                        entity.getType().toString()
                )
        ))) {
            revisions.put(target, revision);
        }
    }

    static boolean accepts(
            Entity entity,
            EntityMaid maid,
            LivingEntity owner
    ) {
        EntityAccessor access = (EntityAccessor) entity;
        return access.tlmCanAddPassenger(maid)
                || owner != null && access.tlmCanAddPassenger(owner);
    }

    private static boolean knownSeat(Entity entity) {
        return entity instanceof Boat
                || entity.getType() == EntityChair.TYPE
                || entity.getType() == EntitySit.TYPE;
    }

    private static AffordanceTargetId target(Entity entity) {
        return new AffordanceTargetId(
                "entity_seat",
                entity.getUUID().toString()
        );
    }
}
