package com.laixia.maidintelligence.mixin.tlm.ai.arbitration;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidBoardGameTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MaidBoardGameTask.class, remap = false)
public abstract class MaidBoardGameTaskMovementCoordinationMixin {
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
    private void maidIntelligence$captureBoardGameTarget(
            ServerLevel level,
            EntityMaid maid,
            CallbackInfoReturnable<Boolean> callback
    ) {
        maidIntelligence$previousWalkTarget =
                MovementCoordinationBridge.capture(maid);
    }

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
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void maidIntelligence$coordinateBoardGameTarget(
            ServerLevel level,
            EntityMaid maid,
            CallbackInfoReturnable<Boolean> callback
    ) {
        MovementCoordinationBridge.finishKnownWrite(
                maid,
                maidIntelligence$previousWalkTarget,
                MovementIntentSource.BUILT_IN_WORK,
                MovementCoordinationBridge.isExactImplementation(
                        this,
                        MaidBoardGameTask.class
                )
        );
        maidIntelligence$previousWalkTarget = null;
    }
}
