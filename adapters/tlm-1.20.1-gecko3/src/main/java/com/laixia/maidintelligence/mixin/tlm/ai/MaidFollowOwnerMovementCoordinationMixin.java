package com.laixia.maidintelligence.mixin.tlm.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.tlm.MaidSeatAutonomyBridge;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import com.laixia.maidintelligence.feature.ai.tlm.PassiveFollowBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MaidFollowOwnerTask.class, remap = false)
public abstract class MaidFollowOwnerMovementCoordinationMixin {
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
            cancellable = true,
            require = 0,
            remap = false
    )
    private void maidIntelligence$deferPassiveFollow(
            ServerLevel level,
            EntityMaid maid,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (MovementCoordinationBridge.isExactImplementation(
                this,
                MaidFollowOwnerTask.class
        ) && PassiveFollowBridge.shouldDefer(maid)) {
            callback.setReturnValue(false);
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
    private void maidIntelligence$captureFollowTarget(
            ServerLevel level,
            EntityMaid maid,
            long gameTime,
            CallbackInfo callback
    ) {
        if (MovementCoordinationBridge.isExactImplementation(
                this,
                MaidFollowOwnerTask.class
        )) {
            MaidSeatAutonomyBridge.leaveSeatForFollow(maid);
        }
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
    private void maidIntelligence$coordinateFollowTarget(
            ServerLevel level,
            EntityMaid maid,
            long gameTime,
            CallbackInfo callback
    ) {
        MovementCoordinationBridge.finishKnownWrite(
                maid,
                maidIntelligence$previousWalkTarget,
                MovementIntentSource.FOLLOW_OWNER,
                MovementCoordinationBridge.isExactImplementation(
                        this,
                        MaidFollowOwnerTask.class
                )
        );
        maidIntelligence$previousWalkTarget = null;
    }
}
