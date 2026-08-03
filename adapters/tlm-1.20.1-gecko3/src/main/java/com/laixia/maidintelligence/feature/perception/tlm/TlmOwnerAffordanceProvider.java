package com.laixia.maidintelligence.feature.perception.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceAdvertisement;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceTargetId;
import com.laixia.maidintelligence.feature.behavior.domain.perception.CompanionAffordanceIds;
import com.laixia.maidintelligence.feature.behavior.port.AffordanceIndexPort;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.Set;

final class TlmOwnerAffordanceProvider {
    private long nextRevision = 1L;

    void observe(
            EntityMaid maid,
            long gameTime,
            AffordanceIndexPort index
    ) {
        LivingEntity owner = maid.getOwner();
        if (!(maid.level() instanceof ServerLevel)
                || owner == null
                || owner.level() != maid.level()
                || !owner.isAlive()) {
            return;
        }
        AffordanceTargetId target = new AffordanceTargetId(
                "owner",
                owner.getUUID().toString()
        );
        index.upsert(new AffordanceAdvertisement(
                target,
                Set.of(CompanionAffordanceIds.SOCIALIZE_WITH_OWNER),
                Map.of(
                        CompanionAffordanceIds.COMPANIONSHIP,
                        1.0D
                ),
                TlmPerceptionCoordinates.at(owner),
                nextRevision++,
                gameTime,
                TlmPerceptionCoordinates.deadline(gameTime, 40L),
                Map.of("entity_uuid", owner.getUUID().toString())
        ));
    }
}
