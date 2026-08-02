package com.laixia.maidintelligence.mixin.tlm.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidUpdateActivityFromSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.MaidCommandSeatBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MaidUpdateActivityFromSchedule.class, remap = false)
public abstract class MaidScheduleCommandSeatLockMixin {
    @Inject(
            method = "updateActivityFromSchedule("
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Lcom/github/tartaricacid/touhoulittlemaid/"
                    + "entity/passive/EntityMaid;"
                    + "Lnet/minecraft/world/entity/ai/Brain;J)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void maidIntelligence$preserveCommandSeat(
            ServerLevel level,
            EntityMaid maid,
            Brain<EntityMaid> brain,
            long gameTime,
            CallbackInfo callback
    ) {
        if (MaidCommandSeatBridge.isSeatProtected(maid)) {
            callback.cancel();
        }
    }
}
