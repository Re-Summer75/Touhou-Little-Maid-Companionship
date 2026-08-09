package com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception;

import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Turning a scan into the handful of figures each decision actually asks for.
 *
 * <p>None of this is a decision. It is the arithmetic that sits between "here
 * is everything she can see and everything she is carrying" and the policies,
 * which want one number at a time: who is about to hit her, how hard she can
 * hit back, how far that reaches.
 *
 * <p>Gathered here because the action that used to hold it had grown past the
 * point where the fight itself was readable inside it — and because every one
 * of these is a question about perception rather than about what to do next.
 */
public final class CombatSurvey {
    private CombatSurvey() {
    }

    /**
     * Whoever is closest to hurting her.
     *
     * <p>Measured by how far she is from their reach rather than from their
     * body, so a skeleton eight blocks out that can already shoot outranks a
     * zombie at four that still has to walk. "Nearest" is only shorthand for
     * the question spacing actually asks, which is who lands the next blow.
     */
    public static ScannedThreat nearest(List<ScannedThreat> scanned) {
        ScannedThreat closest = null;
        double least = Double.POSITIVE_INFINITY;
        for (ScannedThreat threat : scanned) {
            double slack =
                    threat.sample().distance() - threat.sample().reach();
            if (slack < least) {
                least = slack;
                closest = threat;
            }
        }
        return closest;
    }

    /** The crowd as bare positions, which is all a retreat needs of it. */
    public static List<Vec3> crowdOf(List<ScannedThreat> scanned) {
        List<Vec3> positions = new ArrayList<>(scanned.size());
        for (ScannedThreat threat : scanned) {
            positions.add(threat.entity().position());
        }
        return positions;
    }

    /**
     * The scanned threat carrying this sample, or {@code null}.
     *
     * <p>The policies answer in samples because they never learn what an entity
     * is; acting needs the entity back. Compared by identity rather than by
     * value: two hostiles standing together can measure identically, and
     * swinging at whichever one matched first is a bug that only appears in a
     * crowd.
     */
    public static ScannedThreat locate(
            List<ScannedThreat> scanned,
            ThreatSample sample
    ) {
        if (sample == null) {
            return null;
        }
        for (ScannedThreat threat : scanned) {
            if (threat.sample() == sample) {
                return threat;
            }
        }
        return null;
    }
}
