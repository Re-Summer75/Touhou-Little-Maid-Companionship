package com.laixia.maidintelligence.mixin.tlm.ai.arbitration;

import com.github.tartaricacid.touhoulittlemaid.block.BlockJoy;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.tlm.NativeBehaviorArbitrationBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockJoy.class, remap = false)
public abstract class BlockJoyOwnerCommandSuppressionMixin {
    @Inject(
            method = "startMaidSit("
                    + "Lcom/github/tartaricacid/touhoulittlemaid/"
                    + "entity/passive/EntityMaid;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void maidIntelligence$suppressIdleLeisureSeat(
            EntityMaid maid,
            BlockState state,
            Level level,
            BlockPos position,
            CallbackInfo callback
    ) {
        if (!level.isClientSide()
                && maid.getScheduleDetail() != Activity.WORK
                && NativeBehaviorArbitrationBridge.ownerCommandActive(
                maid,
                level.getGameTime()
        )) {
            callback.cancel();
        }
    }
}
