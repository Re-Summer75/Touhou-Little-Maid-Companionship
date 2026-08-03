package com.laixia.maidintelligence.mixin.tlm.ai.arbitration;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidRunOne;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import com.laixia.maidintelligence.feature.ai.tlm.NativeBehaviorArbitrationBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MaidRunOne.class, remap = false)
public abstract class MaidRunOneMovementCoordinationMixin {
    @Unique
    private WalkTarget maidIntelligence$previousWalkTarget;

    @Inject(
            method = {
                    "tryStart(Lnet/minecraft/server/level/ServerLevel;"
                            + "Lcom/github/tartaricacid/touhoulittlemaid/"
                            + "entity/passive/EntityMaid;J)Z",
                    "m_22554_(Lnet/minecraft/server/level/ServerLevel;"
                            + "Lcom/github/tartaricacid/touhoulittlemaid/"
                            + "entity/passive/EntityMaid;J)Z"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void maidIntelligence$captureRandomStroll(
            ServerLevel level,
            EntityMaid maid,
            long gameTime,
            CallbackInfoReturnable<Boolean> callback
    ) {
        maidIntelligence$previousWalkTarget =
                MovementCoordinationBridge.capture(maid);
        if (NativeBehaviorArbitrationBridge.ownerCommandActive(
                maid,
                gameTime
        )) {
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = {
                    "tryStart(Lnet/minecraft/server/level/ServerLevel;"
                            + "Lcom/github/tartaricacid/touhoulittlemaid/"
                            + "entity/passive/EntityMaid;J)Z",
                    "m_22554_(Lnet/minecraft/server/level/ServerLevel;"
                            + "Lcom/github/tartaricacid/touhoulittlemaid/"
                            + "entity/passive/EntityMaid;J)Z"
            },
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void maidIntelligence$coordinateRandomStroll(
            ServerLevel level,
            EntityMaid maid,
            long gameTime,
            CallbackInfoReturnable<Boolean> callback
    ) {
        MovementCoordinationBridge.finishKnownWrite(
                maid,
                maidIntelligence$previousWalkTarget,
                MovementIntentSource.RANDOM_STROLL,
                MovementCoordinationBridge.isExactImplementation(
                        this,
                        MaidRunOne.class
                ) && MovementCoordinationBridge.isManagedRandomStroll(
                        maid,
                        maidIntelligence$previousWalkTarget
                )
        );
        maidIntelligence$previousWalkTarget = null;
    }
}
