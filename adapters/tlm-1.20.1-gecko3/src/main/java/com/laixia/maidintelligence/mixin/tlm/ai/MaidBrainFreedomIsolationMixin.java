package com.laixia.maidintelligence.mixin.tlm.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomBrain;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMode;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The one place free mode parts company with the host.
 *
 * <p>Chosen for how little of the host it has to know. The brain is rebuilt
 * from scratch whenever the player changes work mode — {@code
 * EntityMaid.setTask} calls {@code refreshBrain}, which empties the brain and
 * calls this method again — so a single interception here is both complete and
 * instantaneous, and it needs no per-behaviour knowledge whatsoever.
 *
 * <p>The alternative was to intercept a dozen behaviours' start conditions. It
 * would have meant a dozen version-sensitive method descriptors instead of one,
 * a dozen chances to forget the mode check, and a standing obligation to notice
 * every behaviour the host adds in a future release. This has one descriptor and
 * inverts that last obligation: see {@link FreedomBrain}.
 *
 * <p>Other modes return immediately, before anything of ours has run. That is
 * the strongest form the guarantee can take — not "we checked and allowed it",
 * but "that path does not contain us".
 */
@Mixin(value = MaidBrain.class, remap = false)
public abstract class MaidBrainFreedomIsolationMixin {
    @Inject(
            method = {
                    "registerBrainGoals(Lnet/minecraft/world/entity/ai/Brain;"
                            + "Lcom/github/tartaricacid/touhoulittlemaid/"
                            + "entity/passive/EntityMaid;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            // Required, unlike every other mixin here. The rest add coordination
            // that is worth having and survivable to lose; this one *is* free
            // mode. If the descriptor stops matching, it silently applies
            // nothing, the host registers its full brain, and free mode goes on
            // looking like it works while being exactly what it was built to
            // replace — the one failure nobody would notice from inside the
            // game. Refusing to launch says so at the only moment it is cheap
            // to find out, and this adapter targets one host version anyway.
            require = 1,
            remap = false
    )
    private static void maidIntelligence$buildFreedomBrain(
            Brain<EntityMaid> brain,
            EntityMaid maid,
            CallbackInfo callback
    ) {
        if (FreedomMode.isHostOwned(maid)) {
            return;
        }
        FreedomBrain.register(brain, maid);
        callback.cancel();
    }
}
