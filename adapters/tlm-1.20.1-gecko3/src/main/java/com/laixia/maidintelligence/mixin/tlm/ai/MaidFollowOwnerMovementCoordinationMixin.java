package com.laixia.maidintelligence.mixin.tlm.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import com.laixia.maidintelligence.feature.ai.tlm.OwnerFollowBridge;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes owner-following away from the native task.
 *
 * <p>Deciding to walk to her owner now happens where every other decision she
 * makes happens. Leaving a second follow behaviour in place did not make her
 * follow better, it made two systems write the same walk target from different
 * premises: native follow only knows a distance, so it pulled her off a snack
 * cabinet ten blocks away every time she crossed the threshold on the way
 * there, and the arbitration built to stop that had to keep growing exceptions
 * for cases the orchestrator already understood.
 *
 * <p>Two things are kept, because neither can be expressed as an intent. The
 * drowning rescue is left entirely to TLM — it is a correctness fix for a maid
 * about to die and it teleports deliberately, however badly that reads. And the
 * distance backstop stays, for when she is too far behind for any amount of
 * walking to recover her.
 *
 * <p>Subclasses are untouched. A third-party task extending this one has its
 * own reasons for following and is none of this mod's business.
 */
@Mixin(value = MaidFollowOwnerTask.class, remap = false)
public abstract class MaidFollowOwnerMovementCoordinationMixin {
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
            cancellable = true,
            require = 0,
            remap = false
    )
    private void maidIntelligence$replaceNativeFollow(
            ServerLevel level,
            EntityMaid maid,
            long gameTime,
            CallbackInfo callback
    ) {
        if (!MovementCoordinationBridge.isExactImplementation(
                this,
                MaidFollowOwnerTask.class
        )) {
            return;
        }
        if (maid.getSwimManager().isGoingToBreath()) {
            return;
        }
        OwnerFollowBridge.teleportIfStranded(maid);
        callback.cancel();
    }
}
