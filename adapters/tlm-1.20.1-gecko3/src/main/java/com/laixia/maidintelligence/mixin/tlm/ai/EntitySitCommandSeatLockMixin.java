package com.laixia.maidintelligence.mixin.tlm.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.MaidCommandSeatBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntitySit.class, remap = false)
public abstract class EntitySitCommandSeatLockMixin {
    @Inject(
            method = "tickMaid("
                    + "Lcom/github/tartaricacid/touhoulittlemaid/"
                    + "entity/passive/EntityMaid;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void maidIntelligence$preserveCommandSeat(
            EntityMaid maid,
            CallbackInfo callback
    ) {
        if (!MaidCommandSeatBridge.isSeatProtected(maid)) {
            return;
        }
        EntitySit seat = (EntitySit) (Object) this;
        maid.setYRot(seat.getYRot());
        maid.setYHeadRot(seat.getYRot());
        callback.cancel();
    }
}
