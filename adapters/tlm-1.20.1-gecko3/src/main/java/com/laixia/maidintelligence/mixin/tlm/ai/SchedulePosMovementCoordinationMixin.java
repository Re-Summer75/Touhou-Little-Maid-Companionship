package com.laixia.maidintelligence.mixin.tlm.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SchedulePos.class, remap = false)
public abstract class SchedulePosMovementCoordinationMixin {
    @Unique
    private WalkTarget maidIntelligence$previousWalkTarget;

    @Inject(
            method = "tick(Lcom/github/tartaricacid/touhoulittlemaid/"
                    + "entity/passive/EntityMaid;)V",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void maidIntelligence$captureScheduleTarget(
            EntityMaid maid,
            CallbackInfo callback
    ) {
        maidIntelligence$previousWalkTarget =
                MovementCoordinationBridge.capture(maid);
    }

    @Inject(
            method = "tick(Lcom/github/tartaricacid/touhoulittlemaid/"
                    + "entity/passive/EntityMaid;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void maidIntelligence$coordinateScheduleTarget(
            EntityMaid maid,
            CallbackInfo callback
    ) {
        MovementCoordinationBridge.finishKnownWrite(
                maid,
                maidIntelligence$previousWalkTarget,
                MovementIntentSource.HOME_RETURN,
                true
        );
        maidIntelligence$previousWalkTarget = null;
    }
}
