package com.laixia.maidintelligence.feature.orchestration.tlm.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatRelation;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures what is coming at her, without naming any of it.
 *
 * <p>Reads the same visible-entity memory the host's own targeting uses. The
 * owner's ignore list still has the final say on who is off limits, but it is
 * not by itself a hostility test — see {@link ThreatProfile#isHostileTo}. What this adds is that every one of them is
 * measured, not just the nearest — a crowd cannot be judged from its closest
 * member.
 *
 * <p>Reach, damage and cadence come from {@link ThreatProfile}, which asks each
 * creature about itself. They were briefly three constants here, which quietly
 * turned every hostile in the game into the same one.
 */
public final class TlmThreatScanner {
    /** Everything she can currently see and is allowed to fight. */
    public List<ScannedThreat> scan(EntityMaid maid) {
        List<ScannedThreat> threats = new ArrayList<>();
        maid.getBrain()
                .getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .ifPresent(visible -> visible.findAll(
                        candidate -> ThreatProfile.isHostileTo(maid, candidate)
                ).forEach(hostile -> threats.add(
                        new ScannedThreat(hostile, sample(maid, hostile))
                )));
        return threats;
    }

    /** The samples alone, for the parts of the decision that need no entities. */
    public static List<ThreatSample> samplesOf(List<ScannedThreat> threats) {
        List<ThreatSample> samples = new ArrayList<>(threats.size());
        for (ScannedThreat threat : threats) {
            samples.add(threat.sample());
        }
        return samples;
    }

    private ThreatSample sample(EntityMaid maid, LivingEntity hostile) {
        return new ThreatSample(
                maid.distanceTo(hostile),
                ThreatProfile.strikeDamage(hostile),
                ThreatProfile.reach(hostile, maid),
                ThreatProfile.attackPeriod(hostile),
                hostile.getHealth(),
                airborne(hostile),
                ThreatProfile.closingSpeed(maid, hostile),
                relation(maid, hostile)
        );
    }

    /**
     * Who this one is currently bothering.
     *
     * <p>Read from its own target rather than from damage events, so something
     * that has closed on her owner but not yet landed a hit already counts —
     * waiting for the first hit means she answers it one hit late.
     */
    private ThreatRelation relation(EntityMaid maid, LivingEntity hostile) {
        LivingEntity owner = maid.getOwner();
        LivingEntity itsTarget =
                hostile instanceof Mob mob ? mob.getTarget() : null;
        if (owner != null && itsTarget == owner) {
            return ThreatRelation.ATTACKING_OWNER;
        }
        if (itsTarget == maid) {
            return ThreatRelation.ATTACKING_MAID;
        }
        if (owner != null && owner.getLastHurtMob() == hostile) {
            return ThreatRelation.OWNER_TARGET;
        }
        return ThreatRelation.UNENGAGED;
    }

    /**
     * Whether a swung weapon would miss it entirely.
     *
     * <p>Asked of how it moves rather than of what it is, so a modded flier is
     * covered and a phantom resting on the ground is not treated as unreachable.
     */
    private boolean airborne(LivingEntity hostile) {
        if (hostile.onGround() || hostile.isInWater()) {
            return false;
        }
        if (hostile.isNoGravity()) {
            return true;
        }
        return hostile instanceof Mob mob
                && mob.getNavigation() instanceof
                net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
    }
}
