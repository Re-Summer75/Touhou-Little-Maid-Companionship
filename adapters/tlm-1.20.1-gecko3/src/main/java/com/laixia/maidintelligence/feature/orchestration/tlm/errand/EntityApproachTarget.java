package com.laixia.maidintelligence.feature.orchestration.tlm.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationResourceKey;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmCoordinationClaims;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;

import java.util.Objects;

/**
 * A target that moves: a dropped item, a chair, another creature.
 *
 * <p>Claimed by its identity rather than by where it is standing, since where
 * it is standing is the part that changes.
 */
public final class EntityApproachTarget implements ApproachTarget {
    private final Entity entity;

    public EntityApproachTarget(Entity entity) {
        this.entity = Objects.requireNonNull(entity, "entity");
    }

    public Entity entity() {
        return entity;
    }

    @Override
    public PositionTracker tracker() {
        return new EntityTracker(entity, false);
    }

    @Override
    public double distanceToSqr(EntityMaid maid) {
        return maid.distanceToSqr(entity);
    }

    @Override
    public CoordinationResourceKey claimKey(ServerLevel level) {
        return TlmCoordinationClaims.itemEntity(level, entity);
    }

    @Override
    public String identity() {
        return entity.getUUID().toString();
    }

    @Override
    public boolean valid() {
        return entity.isAlive();
    }

    @Override
    public boolean matches(PositionTracker other) {
        return other instanceof EntityTracker tracker
                && entity.equals(tracker.getEntity());
    }
}
