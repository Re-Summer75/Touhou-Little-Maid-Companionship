package com.laixia.maidintelligence.mixin.tlm.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidPickupEntitiesTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.tlm.MaidSeatAutonomyBridge;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MaidPickupEntitiesTask.class, remap = false)
public abstract class MaidPickupMovementCoordinationMixin {
    @Unique
    private WalkTarget maidIntelligence$previousWalkTarget;

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
            require = 0,
            remap = false
    )
    private void maidIntelligence$leaveSeatBeforePickupCheck(
            ServerLevel level,
            EntityMaid maid,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (MovementCoordinationBridge.isExactImplementation(
                this,
                MaidPickupEntitiesTask.class
        )) {
            MaidSeatAutonomyBridge.leaveSeatForPickup(maid);
        }
    }

    @Inject(
            method = {
                    "start(Lnet/minecraft/server/level/ServerLevel;"
                            + "Lcom/github/tartaricacid/touhoulittlemaid/"
                            + "entity/passive/EntityMaid;J)V",
                    "m_6735_(Lnet/minecraft/server/level/ServerLevel;"
                            + "Lcom/github/tartaricacid/touhoulittlemaid/"
                            + "entity/passive/EntityMaid;J)V"
            },
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void maidIntelligence$capturePickupTarget(
            ServerLevel level,
            EntityMaid maid,
            long gameTime,
            CallbackInfo callback
    ) {
        maidIntelligence$previousWalkTarget =
                MovementCoordinationBridge.capture(maid);
    }

    @Inject(
            method = {
                    "start(Lnet/minecraft/server/level/ServerLevel;"
                            + "Lcom/github/tartaricacid/touhoulittlemaid/"
                            + "entity/passive/EntityMaid;J)V",
                    "m_6735_(Lnet/minecraft/server/level/ServerLevel;"
                            + "Lcom/github/tartaricacid/touhoulittlemaid/"
                            + "entity/passive/EntityMaid;J)V"
            },
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void maidIntelligence$coordinatePickupTarget(
            ServerLevel level,
            EntityMaid maid,
            long gameTime,
            CallbackInfo callback
    ) {
        MovementCoordinationBridge.finishKnownWrite(
                maid,
                maidIntelligence$previousWalkTarget,
                MovementIntentSource.PICKUP,
                MovementCoordinationBridge.isExactImplementation(
                        this,
                        MaidPickupEntitiesTask.class
                )
        );
        maidIntelligence$previousWalkTarget = null;
    }
}
