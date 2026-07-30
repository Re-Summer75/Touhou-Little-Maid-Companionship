package com.laixia.maidintelligence.mixin.common;

import com.laixia.maidintelligence.feature.advancement.bridge.HoneySlideHandler;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.HoneyBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 蜜块下滑的判定条件全在方块自己手里（贴着方块、在下落、离方块中心足够近），
 * 原版只是在最后一步把非玩家挡掉了。接在这里就能白拿那套条件。
 */
@Mixin(HoneyBlock.class)
public abstract class HoneyBlockSlideMixin {
    @Inject(method = "maybeDoSlideAchievement", at = @At("HEAD"))
    private void maidIntelligence$reportMaidSlide(
            Entity entity,
            BlockPos position,
            CallbackInfo callback
    ) {
        AdapterRuntime.require(HoneySlideHandler.class)
                .onHoneySlide(entity, position);
    }
}
