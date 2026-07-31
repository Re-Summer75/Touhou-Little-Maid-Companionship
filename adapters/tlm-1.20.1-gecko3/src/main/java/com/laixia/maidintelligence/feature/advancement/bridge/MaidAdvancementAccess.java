package com.laixia.maidintelligence.feature.advancement.bridge;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.server.MaidAdvancementSnapshot;
import net.minecraft.server.ServerAdvancementManager;

import java.util.Optional;
import java.util.UUID;

/**
 * Narrow query and lifecycle API used by packets and GameTests.
 */
public interface MaidAdvancementAccess {
    Optional<MaidAdvancementSnapshot> snapshot(
            EntityMaid maid,
            ServerAdvancementManager advancements
    );

    void release(UUID maidId);
}
