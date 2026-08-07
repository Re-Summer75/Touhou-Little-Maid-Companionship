package com.laixia.maidintelligence.mixin.tlm.ai.arbitration;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidJoyTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.behavior.tlm.FreedomMaidTask;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import com.laixia.maidintelligence.feature.ai.tlm.NativeBehaviorArbitrationBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MaidJoyTask.class, remap = false)
public abstract class MaidJoyTaskMovementCoordinationMixin {
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
    private void maidIntelligence$captureLeisureTarget(
            ServerLevel level,
            EntityMaid maid,
            CallbackInfoReturnable<Boolean> callback
    ) {
        maidIntelligence$previousWalkTarget =
                MovementCoordinationBridge.capture(maid);
        if (NativeBehaviorArbitrationBridge.ownerCommandActive(
                maid,
                level.getGameTime()
        )
                || maidIntelligence$pastimeBelongsToIntents(maid)) {
            callback.setReturnValue(false);
        }
    }

    /**
     * Whether deciding to go and read has moved to the orchestrator.
     *
     * <p>Under the freedom task it has. Going to a bookshelf or a chessboard is
     * worth keeping — it is most of what she does when nothing is asked of her
     * — but this behaviour takes her the moment one is in range, and the seat
     * it produces reads as soft occupancy, which eleven of the thirteen
     * companion intents refuse to start against. She would settle in to read
     * and no longer be able to go and eat.
     *
     * <p>{@code tlm_companionship:enjoy_pastime} makes the same decision where
     * it can be ranked against being hungry. Every other work mode keeps TLM's
     * behaviour untouched: a maid told to farm is not being asked to weigh
     * reading against her instructions.
     */
    @Unique
    private static boolean maidIntelligence$pastimeBelongsToIntents(
            EntityMaid maid
    ) {
        return FreedomMaidTask.UID.equals(maid.getTask().getUid());
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
    private void maidIntelligence$coordinateLeisureTarget(
            ServerLevel level,
            EntityMaid maid,
            CallbackInfoReturnable<Boolean> callback
    ) {
        MovementCoordinationBridge.finishKnownWrite(
                maid,
                maidIntelligence$previousWalkTarget,
                MovementIntentSource.LEISURE,
                MovementCoordinationBridge.isExactImplementation(
                        this,
                        MaidJoyTask.class
                )
        );
        maidIntelligence$previousWalkTarget = null;
    }
}
