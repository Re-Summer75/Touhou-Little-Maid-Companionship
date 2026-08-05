package com.laixia.maidintelligence.feature.perception.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceAdvertisement;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceTargetId;
import com.laixia.maidintelligence.feature.behavior.domain.perception.CompanionAffordanceIds;
import com.laixia.maidintelligence.feature.behavior.port.AffordanceIndexPort;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Food lying on the ground, including whatever the owner just threw her.
 *
 * <p>Unlike a cabinet, an item is picked up, despawns, or is washed away by
 * water, and none of those raise an event this could listen for. So these
 * advertisements are the first that genuinely lapse: each is stamped to expire
 * shortly after the observation that made it, and only another sighting renews
 * it. An item that is gone stops being advertised because nothing said it was
 * still there — not because something noticed it leave.
 */
@SuppressWarnings("null")
final class TlmItemEntityAffordanceProvider {
    private static final double OBSERVATION_RANGE = 16.0D;

    /** Enough to cover a scattered drop without sweeping a whole field. */
    private static final int MAX_NEARBY_ITEMS = 24;

    /**
     * Long enough to outlive a few missed observations, short enough that an
     * item picked up is forgotten within a moment rather than sending her to an
     * empty patch of ground.
     */
    private static final int ADVERTISEMENT_TTL_TICKS = 60;

    private final Map<AffordanceTargetId, Long> revisions = new HashMap<>();
    private long nextRevision = 1L;

    void observeNearby(
            EntityMaid maid,
            long gameTime,
            AffordanceIndexPort index
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        AABB bounds = maid.getBoundingBox().inflate(
                OBSERVATION_RANGE,
                OBSERVATION_RANGE / 2.0D,
                OBSERVATION_RANGE
        );
        int seen = 0;
        for (ItemEntity item : level.getEntitiesOfClass(
                ItemEntity.class,
                bounds,
                entity -> entity.isAlive() && !entity.hasPickUpDelay()
        )) {
            if (seen++ >= MAX_NEARBY_ITEMS) {
                break;
            }
            advertise(maid, item, gameTime, index);
        }
    }

    private void advertise(
            EntityMaid maid,
            ItemEntity item,
            long gameTime,
            AffordanceIndexPort index
    ) {
        ItemStack stack = item.getItem();
        double relief = TlmHungerCommodity.of(stack, maid);
        AffordanceTargetId target = target(item);
        if (relief <= 0.0D) {
            // Not food to her. Withdraw any earlier claim rather than leaving
            // one standing that would only lapse on its own later.
            remove(item, index);
            return;
        }
        long revision = nextRevision++;
        AffordanceAdvertisement advertisement = new AffordanceAdvertisement(
                target,
                Set.of(CompanionAffordanceIds.TAKE_FOOD),
                Map.of(CompanionAffordanceIds.HUNGER_RELIEF, relief),
                TlmPerceptionCoordinates.at(item),
                revision,
                gameTime,
                gameTime + ADVERTISEMENT_TTL_TICKS,
                Map.of("entity_id", Integer.toString(item.getId()))
        );
        if (index.upsert(advertisement)) {
            revisions.put(target, revision);
        }
    }

    void remove(ItemEntity item, AffordanceIndexPort index) {
        AffordanceTargetId target = target(item);
        Long revision = revisions.remove(target);
        if (revision != null) {
            index.remove(target, revision);
        }
    }

    private static AffordanceTargetId target(ItemEntity item) {
        return new AffordanceTargetId(
                "entity_item",
                item.getUUID().toString()
        );
    }
}
