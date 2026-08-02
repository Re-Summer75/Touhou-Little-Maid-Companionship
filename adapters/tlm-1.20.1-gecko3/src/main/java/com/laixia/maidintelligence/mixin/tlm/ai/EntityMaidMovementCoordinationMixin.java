package com.laixia.maidintelligence.mixin.tlm.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentLease;
import com.laixia.maidintelligence.feature.ai.tlm.ActivityRadiusBridge;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationAccess;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidMovementCoordinationMixin
        implements MovementCoordinationAccess {
    @Unique
    private final MovementIntentLease maidIntelligence$movementIntentLease =
            new MovementIntentLease();

    @Override
    public MovementIntentLease maidIntelligence$movementIntentLease() {
        return maidIntelligence$movementIntentLease;
    }

    @Inject(
            method = "refreshBrain("
                    + "Lnet/minecraft/server/level/ServerLevel;)V",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void maidIntelligence$resetBeforeBrainRefresh(
            ServerLevel level,
            CallbackInfo callback
    ) {
        EntityMaid maid = maidIntelligence$self();
        ActivityRadiusBridge.reset(maid);
        MovementCoordinationBridge.hardReset(maid);
    }

    @Unique
    private EntityMaid maidIntelligence$self() {
        return (EntityMaid) (Object) this;
    }
}
