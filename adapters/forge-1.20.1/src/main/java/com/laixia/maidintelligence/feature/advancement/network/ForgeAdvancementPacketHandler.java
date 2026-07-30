package com.laixia.maidintelligence.feature.advancement.network;

import com.laixia.maidintelligence.feature.advancement.server.MaidAdvancementSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.network.NetworkHooks;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntFunction;

/**
 * Owns Forge-only menu and channel operations while delegating TLM entity
 * validation and snapshot creation through narrow callbacks.
 */
public final class ForgeAdvancementPacketHandler
        implements MaidAdvancementServerPacketHandler {
    private final OpenPageResolver openPageResolver;
    private final SnapshotResolver snapshotResolver;
    private final IntFunction<MenuProvider> menuFactory;

    public ForgeAdvancementPacketHandler(
            OpenPageResolver openPageResolver,
            SnapshotResolver snapshotResolver,
            IntFunction<MenuProvider> menuFactory
    ) {
        this.openPageResolver = Objects.requireNonNull(
                openPageResolver,
                "openPageResolver"
        );
        this.snapshotResolver = Objects.requireNonNull(
                snapshotResolver,
                "snapshotResolver"
        );
        this.menuFactory = Objects.requireNonNull(
                menuFactory,
                "menuFactory"
        );
    }

    @Override
    public void openPage(ServerPlayer player, int maidEntityId) {
        openPageResolver.resolve(player, maidEntityId)
                .ifPresent(resolvedId -> NetworkHooks.openScreen(
                        player,
                        menuFactory.apply(resolvedId),
                        buffer -> buffer.writeInt(resolvedId)
                ));
    }

    @Override
    public void sendSnapshot(ServerPlayer player, int maidEntityId) {
        snapshotResolver.resolve(player, maidEntityId)
                .ifPresent(snapshot -> AdvancementNetwork.sendSnapshot(
                        player,
                        maidEntityId,
                        snapshot
                ));
    }

    @FunctionalInterface
    public interface OpenPageResolver {
        OptionalInt resolve(ServerPlayer player, int maidEntityId);
    }

    @FunctionalInterface
    public interface SnapshotResolver {
        Optional<MaidAdvancementSnapshot> resolve(
                ServerPlayer player,
                int maidEntityId
        );
    }
}
