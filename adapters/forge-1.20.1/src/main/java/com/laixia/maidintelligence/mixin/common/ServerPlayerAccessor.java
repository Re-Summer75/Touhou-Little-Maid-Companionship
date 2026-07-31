package com.laixia.maidintelligence.mixin.common;

import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayer.class)
public interface ServerPlayerAccessor {
    @Accessor("advancements")
    PlayerAdvancements maidIntelligence$getAdvancements();

    @Mutable
    @Accessor("advancements")
    void maidIntelligence$setAdvancements(PlayerAdvancements advancements);
}
