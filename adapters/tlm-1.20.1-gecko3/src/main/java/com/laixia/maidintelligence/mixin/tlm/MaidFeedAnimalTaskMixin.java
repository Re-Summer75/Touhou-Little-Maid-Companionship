package com.laixia.maidintelligence.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFeedAnimalTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidFeatAdvancementTriggers;
import com.laixia.maidintelligence.feature.advancement.port.MaidCourtshipMemory;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
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
        if (feedEntity == null) {
            return;
        }
        AdapterRuntime.require(MaidCourtshipMemory.class)
                .rememberCourtedAnimal(maid, feedEntity.getId());
        // 同一动作也是 player_interacted_with_entity：原版从
        // Player#interactOn 发出，而她走的是宿主自己的喂食任务，那一句判定
        // 便永远轮不到她——可这件事她实实在在做了。
        AdapterRuntime.require(MaidFeatAdvancementTriggers.class)
                .interactedWith(maid, maid.getMainHandItem(), feedEntity);
    }
}
