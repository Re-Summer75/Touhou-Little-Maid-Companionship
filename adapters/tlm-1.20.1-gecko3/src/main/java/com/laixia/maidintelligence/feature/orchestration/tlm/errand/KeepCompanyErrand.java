package com.laixia.maidintelligence.feature.orchestration.tlm.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.perception.CompanionAffordanceIds;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceCandidate;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Drift over and be near her owner because she wants to, not to be summoned.
 *
 * <p>The owner has advertised companionship since the perception layer existed
 * and nothing has ever asked for it. This is the first errand that does, which
 * is why it goes through the affordance index rather than simply reading
 * {@code getOwner} — the advertisement carries how welcome she is likely to be,
 * and answering that question belongs with whoever publishes it.
 *
 * <p>Reserves nothing: a person can be kept company by several maids at once,
 * and claiming him would mean the first to think of it was the only one
 * allowed to.
 */
public final class KeepCompanyErrand implements Errand {
    private static final int CANDIDATES = 2;
    private static final double SEARCH_RANGE = 16.0D;

    private final TlmAffordancePerceptionService perception;

    public KeepCompanyErrand(TlmAffordancePerceptionService perception) {
        this.perception = Objects.requireNonNull(perception, "perception");
    }

    @Override
    public String name() {
        return "keep_company";
    }

    @Override
    public boolean requiresClaim() {
        return false;
    }

    @Override
    public ApproachTarget find(EntityMaid maid, long gameTime) {
        LivingEntity owner = maid.getOwner();
        if (owner == null || !owner.isAlive() || owner.isSpectator()) {
            return null;
        }
        List<AffordanceCandidate> candidates = perception.queryAffordances(
                maid,
                Set.of(CompanionAffordanceIds.SOCIALIZE_WITH_OWNER),
                CompanionAffordanceIds.COMPANIONSHIP,
                SEARCH_RANGE,
                CANDIDATES,
                gameTime
        );
        String ownerId = owner.getUUID().toString();
        for (AffordanceCandidate candidate : candidates) {
            if (ownerId.equals(candidate.advertisement()
                    .attributes()
                    .get("entity_uuid"))) {
                return new EntityApproachTarget(owner);
            }
        }
        return null;
    }

    @Override
    public boolean commit(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        return true;
    }
}
