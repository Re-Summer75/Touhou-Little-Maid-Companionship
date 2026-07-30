package com.laixia.maidintelligence.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidClientRandomHealingMixin {
    @Inject(
            method = "randomRestoreHealth",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void maidIntelligence$preventClientRandomHealing(CallbackInfo callback) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (maid.level().isClientSide()) {
            callback.cancel();
        }
    }
}
