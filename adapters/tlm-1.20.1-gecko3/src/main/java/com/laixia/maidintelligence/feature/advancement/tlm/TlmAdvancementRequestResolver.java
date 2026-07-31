package com.laixia.maidintelligence.feature.advancement.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidAdvancementAccess;
import com.laixia.maidintelligence.feature.advancement.server.MaidAdvancementSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Resolves TLM maid requests without owning Forge menu or channel operations.
 */
public final class TlmAdvancementRequestResolver {
    private static final double OPEN_DISTANCE = 3.0D;
    private static final double SNAPSHOT_DISTANCE = 8.0D;

    private final MaidAdvancementAccess advancementAccess;

    public TlmAdvancementRequestResolver(
            MaidAdvancementAccess advancementAccess
    ) {
        this.advancementAccess = Objects.requireNonNull(
                advancementAccess,
                "advancementAccess"
        );
    }

    public OptionalInt resolveOpenPage(
            ServerPlayer player,
            int maidEntityId
    ) {
        if (!(player.level().getEntity(maidEntityId)
                instanceof EntityMaid maid)
                || !maid.isOwnedBy(player)
                || !maid.isAlive()
                || maid.isSleeping()
                || !player.canReach(maid, OPEN_DISTANCE)) {
            return OptionalInt.empty();
        }
        maid.getNavigation().stop();
        return OptionalInt.of(maid.getId());
    }

    public Optional<MaidAdvancementSnapshot> resolveSnapshot(
            ServerPlayer player,
            int maidEntityId
    ) {
        MinecraftServer server = player.getServer();
        if (server == null
                || !(player.level().getEntity(maidEntityId)
                instanceof EntityMaid maid)
                || !player.canReach(maid, SNAPSHOT_DISTANCE)) {
            return Optional.empty();
        }
        return advancementAccess.snapshot(maid, server.getAdvancements());
    }
}
