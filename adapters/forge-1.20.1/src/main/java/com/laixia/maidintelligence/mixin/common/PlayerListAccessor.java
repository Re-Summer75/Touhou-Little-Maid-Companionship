package com.laixia.maidintelligence.mixin.common;

import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

@Mixin(PlayerList.class)
public interface PlayerListAccessor {
    @Accessor("stats")
    Map<UUID, ServerStatsCounter> maidIntelligence$getStats();

    @Accessor("advancements")
    Map<UUID, PlayerAdvancements> maidIntelligence$getAdvancements();
}
