package com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.perception.BearingField;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Where to step so that only one of them can follow.
 *
 * <p>The other retreat asks a different question and gets a different answer.
 * {@link RetreatSpace#escapeTo} maximises how far she gets before meeting a
 * wall or a body — "get away from everyone" — and that is right when the fight
 * is lost and she is leaving. It is the wrong objective in the middle of one
 * she can win: backing straight away from a crowd keeps the crowd abreast, and
 * she arrives somewhere safer with exactly the same three axes still lined up
 * in front of her.
 *
 * <p>What the melee tactic needs is separation, not distance. She is going to
 * turn and fight the nearest one; what has to change is where the <em>second
 * one</em> is when she does. So the destinations are scored by the second
 * nearest hostile rather than the nearest — the closest one following her is
 * the point, and the one behind it falling away is the win.
 *
 * <p>Measured, this is the half of "six-on-one becomes six one-on-ones" that
 * was still being left to chance. Breaking contact a beat earlier made her win
 * two vindicators four times in six instead of once; three of them still killed
 * her four times in six, and every one of those was the same picture — pressed
 * from three sides, with every direction equally bad because none of them was
 * being asked to be good at anything in particular.
 */
public final class IsolationStep {
    private IsolationStep() {
    }

    /**
     * A destination that leaves the second nearest as far behind as possible.
     *
     * @param threats  where they are, all of them
     * @param distance how much ground she is trying to open
     * @return where to go, or {@code null} when nothing is worth walking to —
     *         including the case of a single hostile, where there is no second
     *         one to leave behind and the ordinary retreat is the right answer
     */
    public static Vec3 toward(
            EntityMaid maid,
            List<Vec3> threats,
            double distance
    ) {
        if (threats.size() < 2) {
            return null;
        }
        Vec3 from = maid.position();
        double needed = Math.max(
                1.0D,
                Math.min(RetreatSpace.MINIMUM_RETREAT, Math.floor(distance))
        );
        Vec3 best = null;
        double bestSecond = Double.NEGATIVE_INFINITY;
        for (int sector = 0; sector < BearingField.SECTORS; sector++) {
            double radians = BearingField.bearingOf(sector);
            Vec3 direction =
                    new Vec3(Math.cos(radians), 0.0D, Math.sin(radians));
            double reach =
                    RetreatSpace.reachAlong(maid, from, direction, distance);
            if (reach < needed) {
                continue;
            }
            Vec3 to = from.add(direction.scale(reach));
            double second = secondNearest(to, threats);
            if (second > bestSecond) {
                bestSecond = second;
                best = to;
            }
        }
        return best;
    }

    /**
     * Distance from a point to the second nearest of them.
     *
     * <p>The nearest is deliberately ignored. She is not trying to lose it —
     * she is trying to fight it alone, and a step that shakes off the leader
     * while the other two arrive together has solved nothing.
     */
    private static double secondNearest(Vec3 at, List<Vec3> threats) {
        double nearest = Double.POSITIVE_INFINITY;
        double second = Double.POSITIVE_INFINITY;
        for (Vec3 threat : threats) {
            double distance = at.distanceTo(threat);
            if (distance < nearest) {
                second = nearest;
                nearest = distance;
            } else if (distance < second) {
                second = distance;
            }
        }
        return second;
    }
}
