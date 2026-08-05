package com.laixia.maidintelligence.feature.orchestration.insight;

import com.laixia.maidintelligence.feature.orchestration.api.insight.MaidInsight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Supplies the panel's contents without the sender needing to know what a maid
 * is.
 *
 * <p>Stated in vanilla types on purpose. Everything about which entities count
 * and how their decisions read lives in the TLM adapter; everything about
 * throttling and sending lives here, and neither has to import the other's
 * world.
 */
public interface MaidInsightSource {
    /**
     * Maids close enough to the player to be worth describing. Implementations
     * must not load chunks or scan beyond the radius given.
     */
    List<Entity> maidsNear(ServerPlayer player, double radius);

    MaidInsight insightFor(Entity maid);
}
