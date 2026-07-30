package com.laixia.maidintelligence.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFeedAnimalTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.MaidAdvancementFeature;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 女仆喂食动物促成繁殖时，TLM 传给 {@code setInLove} 的是 {@code null}，
 * 所以原版不会把 {@code bred_animals} 记到任何人名下。这里记下「这只动物是这只女仆撮合的」，
 * 等幼崽出生的 Forge 事件再补上判定。
 */
@Mixin(value = MaidFeedAnimalTask.class, remap = false)
public abstract class MaidFeedAnimalTaskMixin {
    @Shadow
    private Animal feedEntity;

    @Inject(
            method = "start(Lnet/minecraft/server/level/ServerLevel;"
                    + "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/Animal;"
                            + "setInLove(Lnet/minecraft/world/entity/player/Player;)V",
                    remap = true
            ),
            remap = false
    )
    private void maidIntelligence$rememberCourtedAnimal(
            ServerLevel level,
            EntityMaid maid,
            long gameTime,
            CallbackInfo callback
    ) {
        if (feedEntity != null) {
            MaidAdvancementFeature.INSTANCE.bridgeMemory().rememberCourtedAnimal(maid, feedEntity.getId());
        }
    }
}
