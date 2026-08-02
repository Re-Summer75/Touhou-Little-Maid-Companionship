package com.laixia.maidintelligence.mixin.tlm.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerVehicleTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.MaidCommandSeatBridge;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MaidFollowOwnerVehicleTask.class, remap = false)
public abstract class MaidFollowOwnerVehicleSeatLockMixin {
    @Inject(
            method = {
                    "checkExtraStartConditions("
                            + "Lnet/minecraft/server/level/ServerLevel;"
                            + "Lcom/github/tartaricacid/touhoulittlemaid/"
                            + "entity/passive/EntityMaid;)Z",
                    "m_6114_(Lnet/minecraft/server/level/ServerLevel;"
                            + "Lcom/github/tartaricacid/touhoulittlemaid/"
                            + "entity/passive/EntityMaid;)Z"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void maidIntelligence$preserveCommandSeat(
            ServerLevel level,
            EntityMaid maid,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (MaidCommandSeatBridge.isSeatProtected(maid)) {
            callback.setReturnValue(false);
        }
    }
}
