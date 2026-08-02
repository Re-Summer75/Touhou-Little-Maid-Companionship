package com.laixia.maidintelligence.mixin.tlm.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidBreathAirTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MaidBreathAirTask.class, remap = false)
public abstract class MaidBreathAirMovementCoordinationMixin {
    @Unique
    private WalkTarget maidIntelligence$previousWalkTarget;

    @Inject(
            method = "findAirPosition(Lnet/minecraft/server/level/ServerLevel;"
                    + "Lcom/github/tartaricacid/touhoulittlemaid/entity/"
                    + "passive/EntityMaid;)V",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void maidIntelligence$captureBreathTarget(
            ServerLevel level,
            EntityMaid maid,
            CallbackInfo callback
    ) {
        maidIntelligence$previousWalkTarget =
                MovementCoordinationBridge.capture(maid);
    }

    @Inject(
            method = "findAirPosition(Lnet/minecraft/server/level/ServerLevel;"
                    + "Lcom/github/tartaricacid/touhoulittlemaid/entity/"
                    + "passive/EntityMaid;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void maidIntelligence$coordinateBreathTarget(
            ServerLevel level,
            EntityMaid maid,
            CallbackInfo callback
    ) {
        MovementCoordinationBridge.finishKnownWrite(
                maid,
                maidIntelligence$previousWalkTarget,
                MovementIntentSource.BREATH_AIR,
                MovementCoordinationBridge.isExactImplementation(
                        this,
                        MaidBreathAirTask.class
                )
        );
        maidIntelligence$previousWalkTarget = null;
    }
}
