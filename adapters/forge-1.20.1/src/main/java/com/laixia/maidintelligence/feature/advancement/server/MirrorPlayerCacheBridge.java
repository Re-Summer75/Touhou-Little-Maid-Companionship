package com.laixia.maidintelligence.feature.advancement.server;

import com.laixia.maidintelligence.mixin.common.PlayerListAccessor;
import com.laixia.maidintelligence.mixin.common.ServerPlayerAccessor;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;

import java.util.Objects;
import java.util.UUID;

/**
 * Removes the vanilla player data that {@link ServerPlayer}'s constructor
 * creates for an offline mirror and reuses the mirror's real maid tracker.
 */
public final class MirrorPlayerCacheBridge {
    private MirrorPlayerCacheBridge() {
    }

    /**
     * Detaches the constructor-created advancement and statistics entries.
     * The returned tracker remains in the player field until the maid tracker
     * is ready, but no global cache or criterion listener keeps it alive.
     */
    public static PlayerAdvancements detachBootstrap(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        PlayerAdvancements bootstrap = rawAdvancements(player);
        evictCachedEntries(player, bootstrap);
        bootstrap.stopListening();
        return bootstrap;
    }

    /**
     * Replaces the stopped bootstrap tracker with the existing per-maid
     * tracker. Identity validation prevents overwriting unrelated state.
     */
    public static void installAdvancements(
            ServerPlayer player,
            PlayerAdvancements bootstrap,
            PlayerAdvancements replacement
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(replacement, "replacement");
        ServerPlayerAccessor accessor = playerAccessor(player);
        PlayerAdvancements current = accessor.maidIntelligence$getAdvancements();
        if (current == replacement) {
            evictCachedEntries(player, current);
            return;
        }
        if (bootstrap == null || current != bootstrap) {
            throw new IllegalStateException(
                    "Mirror player advancement state changed before tracker installation"
            );
        }
        evictCachedEntries(player, current);
        accessor.maidIntelligence$setAdvancements(replacement);
    }

    /**
     * Handles cache entries recreated by unexpected third-party calls. The
     * installed maid tracker is removed from the vanilla map but never stopped;
     * its lifecycle remains owned by MaidAdvancementTracker.
     */
    public static void release(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        evictCachedEntries(player, rawAdvancements(player));
    }

    public static boolean isDetached(ServerPlayer player) {
        PlayerListAccessor accessor = listAccessor(player);
        UUID id = player.getUUID();
        return !accessor.maidIntelligence$getStats().containsKey(id)
                && !accessor.maidIntelligence$getAdvancements().containsKey(id);
    }

    public static boolean usesAdvancements(
            ServerPlayer player,
            PlayerAdvancements advancements
    ) {
        return rawAdvancements(player) == advancements;
    }

    private static void evictCachedEntries(
            ServerPlayer player,
            PlayerAdvancements installed
    ) {
        PlayerList playerList = player.server.getPlayerList();
        PlayerListAccessor accessor = (PlayerListAccessor) playerList;
        UUID id = player.getUUID();

        if (playerList.getPlayer(id) != null) {
            // The factory rejects this collision before construction. Keep
            // identity checks here as a final guard against deleting real data.
            accessor.maidIntelligence$getStats().remove(id, player.getStats());
            accessor.maidIntelligence$getAdvancements().remove(id, installed);
            return;
        }

        ServerStatsCounter cachedStats =
                accessor.maidIntelligence$getStats().get(id);
        if (cachedStats != null) {
            accessor.maidIntelligence$getStats().remove(id, cachedStats);
        }

        PlayerAdvancements cachedAdvancements =
                accessor.maidIntelligence$getAdvancements().get(id);
        if (cachedAdvancements == null) {
            return;
        }
        boolean removed = accessor.maidIntelligence$getAdvancements()
                .remove(id, cachedAdvancements);
        if (removed && cachedAdvancements != installed) {
            cachedAdvancements.stopListening();
        }
    }

    private static PlayerAdvancements rawAdvancements(ServerPlayer player) {
        return playerAccessor(player).maidIntelligence$getAdvancements();
    }

    private static ServerPlayerAccessor playerAccessor(ServerPlayer player) {
        return (ServerPlayerAccessor) player;
    }

    private static PlayerListAccessor listAccessor(ServerPlayer player) {
        return (PlayerListAccessor) player.server.getPlayerList();
    }
}
