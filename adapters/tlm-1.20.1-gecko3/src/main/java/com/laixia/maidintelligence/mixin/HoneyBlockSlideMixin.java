package com.laixia.maidintelligence.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.server.MaidCriteria;
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
        // 原版对玩家也是每秒才报一次，跟着同一个节流走。
        if (entity instanceof EntityMaid maid
                && !maid.level().isClientSide()
                && maid.level().getGameTime() % 20L == 0L) {
            MaidCriteria.slidDownBlock(maid, maid.level().getBlockState(position));
        }
    }
}
