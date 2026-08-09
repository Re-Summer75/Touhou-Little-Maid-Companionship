package com.laixia.maidintelligence.mixin.tlm.ai;

import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Reach the host's list of brain extensions, which is package-private.
 *
 * <p>Needed because free mode rebuilds the brain itself and must keep every
 * extension the player installed — including this mod's own orchestrator, which
 * arrives through that same list. Dropping them would make the rebuild a
 * regression for anyone running another maid mod, and would silently remove the
 * one behaviour free mode exists to run.
 *
 * <p>An accessor rather than reflection so a rename fails the build instead of
 * failing at runtime in someone's world. It is one field name — a smaller
 * version surface than any of the alternatives, all of which needed several
 * method descriptors.
 */
@Mixin(value = ExtraMaidBrainManager.class, remap = false)
public interface ExtraMaidBrainAccessor {
    @Accessor("EXTRA_MAID_BRAINS")
    static List<IExtraMaidBrain> maidIntelligence$extensions() {
        throw new AssertionError("Replaced by the mixin processor");
    }
}
