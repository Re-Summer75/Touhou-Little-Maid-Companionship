package com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionAlertness;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Reads her situation into a {@link CompanionAlertness}.
 *
 * <p>Kept cheap on purpose. This is asked whenever a host behaviour wants to
 * know whether it may start, which is far more often than a fight is evaluated,
 * so it reuses the visible-entity memory the sensors already fill and does no
 * scanning, no pathfinding and no chunk loading of its own.
 */
public final class TlmAlertness {
    /**
     * How much warning she needs before something arriving counts as urgent.
     *
     * <p>About the time it takes to notice, turn and get a weapon out. Anything
     * arriving sooner than this cannot be prepared for once it lands, so the
     * preparation has to start now — which is the whole point of asking about
     * arrival rather than about distance.
     */
    private static final double REACTION_SECONDS = 2.0D;

    /**
     * Slack beyond a hostile's reach that still counts as on top of her.
     *
     * <p>Arrival time alone is not enough, and the way it fails is instructive:
     * something standing a hand's breadth outside its own reach is not closing,
     * so it "never arrives" and she was cleared to wander off — with a zombie
     * touching her. The measurement was right and the judgement was absurd.
     *
     * <p>One stride, because anything this close does not need to travel; it
     * needs to take a step, and it will. {@code ThreatSample.threatensNow}
     * refuses a margin for the same reason and says so in as many words.
     */
    private static final double STRIDE = 1.0D;

    private TlmAlertness() {
    }

    /** What she should be allowed to be doing right now. */
    public static CompanionAlertness of(EntityMaid maid) {
        boolean committed = maid.getBrain()
                .hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || maid.getBrain().isActive(Activity.PANIC);
        int seen = 0;
        double soonest = Double.POSITIVE_INFINITY;
        var visible = maid.getBrain()
                .getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        if (visible.isPresent()) {
            for (LivingEntity hostile : visible.get().findAll(
                    candidate -> ThreatProfile.isHostileTo(maid, candidate)
            )) {
                seen++;
                // Already within a step counts as here, whatever its current
                // velocity says. Motion answers "when will it arrive"; it
                // cannot answer "is it already on me", and both have to hold.
                boolean withinAStep = maid.distanceTo(hostile)
                        <= ThreatProfile.reach(hostile, maid) + STRIDE;
                soonest = Math.min(
                        soonest,
                        withinAStep
                                ? 0.0D
                                : ThreatProfile.secondsToContact(maid, hostile)
                );
            }
        }
        return CompanionAlertness.of(
                committed, seen, soonest, REACTION_SECONDS
        );
    }

    /** Whether she may leave what she is doing to run an errand. */
    public static boolean allowsErrands(EntityMaid maid) {
        return of(maid).allowsErrands();
    }
}
