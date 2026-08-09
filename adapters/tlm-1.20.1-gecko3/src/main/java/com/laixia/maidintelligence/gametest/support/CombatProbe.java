package com.laixia.maidintelligence.gametest.support;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatCapability;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatStance;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementContext;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementRiskPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponSelectionPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;
import com.laixia.maidintelligence.feature.behavior.domain.perception.BearingField;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.CombatReadiness;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.RetreatSpace;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.ScannedThreat;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmAlertness;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmCombatAction;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmThreatScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmWeaponScanner;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-derives, from outside, everything the fight decided this tick.
 *
 * <p>Split out so the timeline can carry as many columns as it likes without
 * the recorder becoming a wall of extraction. Every value here is recomputed
 * from the same public policies the action runs, so the trace stays a read-only
 * observation — and a disagreement between a column and her behaviour is itself
 * worth knowing about.
 *
 * <p>The bias is deliberately toward printing too much. Three separate wrong
 * diagnoses in this area came from a trace that showed a result without the
 * input that produced it: a retreat with no destination, a destination with no
 * reachability, a movement figure whose units were guessed at. Columns are
 * cheap and a missing one costs an entire debugging round.
 */
public final class CombatProbe {
    private static final TlmThreatScanner SCANNER = new TlmThreatScanner();

    private static final TlmWeaponScanner WEAPONS =
            new TlmWeaponScanner(RangedWeaponRecognizer.NONE);

    /** Read from production rather than restated, so it cannot drift. */
    private static final float COMBAT_SPEED = TlmCombatAction.MOVE_SPEED;

    private static final double WITHDRAW_DISTANCE =
            TlmCombatAction.WITHDRAW_DISTANCE;

    private CombatProbe() {
    }

    /** Everything she can see, measured. */
    public static List<ScannedThreat> scan(EntityMaid maid) {
        return SCANNER.scan(maid);
    }

    /** The crowd as positions, which is what the retreat is computed from. */
    public static List<Vec3> crowd(List<ScannedThreat> scanned) {
        List<Vec3> positions = new ArrayList<>(scanned.size());
        for (ScannedThreat threat : scanned) {
            positions.add(threat.entity().position());
        }
        return positions;
    }

    /** The aggregate the risk policy actually consumes. */
    public static ThreatField field(
            EntityMaid maid,
            List<ScannedThreat> scanned
    ) {
        return ThreatField.of(
                TlmThreatScanner.samplesOf(scanned), reach(maid)
        );
    }

    public static CombatCapability capability(EntityMaid maid) {
        return CombatReadiness.of(maid, WEAPONS.scan(maid));
    }

    public static double reach(EntityMaid maid) {
        return capability(maid).meleeReach();
    }

    /** What she concluded about taking this fight. */
    public static String verdict(
            EntityMaid maid,
            ThreatField field,
            boolean canOpenGround
    ) {
        return EngagementRiskPolicy.instance().assess(
                field,
                capability(maid),
                CombatReadiness.healthFraction(maid),
                canOpenGround
        ).name();
    }

    /** The stance the weapon policy would pick against this target. */
    public static String stance(
            EntityMaid maid,
            ThreatSample target,
            ThreatField field,
            boolean canOpenGround
    ) {
        if (target == null) {
            return "-";
        }
        List<WeaponCandidate> arsenal = WEAPONS.scan(maid);
        CombatStance chosen = WeaponSelectionPolicy.instance().choose(
                arsenal,
                EngagementContext.of(
                        target,
                        field,
                        CombatReadiness.swingsPerSecond(maid),
                        CombatReadiness.SHOTS_PER_SECOND,
                        canOpenGround,
                        false
                )
        );
        if (!chosen.engaged()) {
            return "DISENGAGE";
        }
        return chosen.posture().name()
                + "/" + String.format("%.2f", chosen.weapon().power());
    }

    /** Whether backing off to shooting distance is possible at all. */
    public static boolean canOpenGround(
            EntityMaid maid,
            LivingEntity foe
    ) {
        double wanted = Math.max(
                1.0D,
                WeaponSelectionPolicy.instance().preferredRange()
                        - maid.distanceTo(foe)
        );
        return RetreatSpace.canGiveGround(maid, foe, COMBAT_SPEED, wanted);
    }

    /**
     * How far the retreat she would be given right now actually goes.
     *
     * <p>The single most valuable column for a "she will not back off"
     * complaint: it separates "no escape was produced" from "an escape was
     * produced and something undid it" from "an escape was produced and it was
     * two feet away", which are three different bugs that look identical from
     * the outside.
     */
    public static double escapeReach(EntityMaid maid, List<Vec3> crowd) {
        if (crowd.isEmpty()) {
            return 0.0D;
        }
        Vec3 escape =
                RetreatSpace.escapeTo(maid, crowd, WITHDRAW_DISTANCE);
        return escape == null ? 0.0D : escape.distanceTo(maid.position());
    }

    /**
     * The whole circle as she sees it: which way she would go, and how far.
     *
     * <p>{@code escape} says how much ground a retreat gets; this says why that
     * number and not another. Reading them together is what distinguishes "the
     * only gap is narrow" from "the gap is wide and something else stopped
     * her" — the escape column alone reports the same figure for both.
     *
     * <p>Rendered as {@code sector@bearing/clearance}, with the bearing in
     * degrees anticlockwise from due east so it can be checked against the
     * position column by hand.
     */
    public static String bearing(EntityMaid maid, List<Vec3> crowd) {
        if (crowd.isEmpty()) {
            return "-";
        }
        BearingField field =
                RetreatSpace.survey(maid, crowd, WITHDRAW_DISTANCE);
        int sector = field.safestBearing(RetreatSpace.MINIMUM_RETREAT);
        if (sector < 0) {
            return "boxed";
        }
        return sector
                + "@" + (int) Math.toDegrees(BearingField.bearingOf(sector))
                + "/" + String.format("%.1f", field.clearance(sector));
    }

    /** Her alertness state, which gates errands and pickup. */
    public static String alertness(EntityMaid maid) {
        return TlmAlertness.of(maid).name();
    }

    /** Navigation as a state plus the size of the path it holds. */
    public static String navigation(EntityMaid maid) {
        if (maid.getBrain().hasMemoryValue(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
        )) {
            return "NOPATH";
        }
        var path = maid.getNavigation().getPath();
        if (path == null) {
            return maid.getNavigation().isDone() ? "idle" : "none";
        }
        return (maid.getNavigation().isStuck() ? "STUCK" : "path")
                + ":" + path.getNextNodeIndex() + "/" + path.getNodeCount();
    }

    /** The walk target as "how far, how close counts, at what speed". */
    public static String goal(EntityMaid maid) {
        WalkTarget walk = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        if (walk == null) {
            return "-";
        }
        double away = walk.getTarget().currentPosition()
                .distanceTo(maid.position());
        return String.format(
                "%.1f@%d", away, walk.getCloseEnoughDist()
        );
    }

    /** The active intent and its plan state. */
    public static String intent(EntityMaid maid) {
        @SuppressWarnings("unchecked")
        MaidIntentApi<EntityMaid> intents =
                (MaidIntentApi<EntityMaid>) AdapterRuntime.require(
                        MaidIntentApi.class
                );
        IntentTrace trace = intents.inspect(maid);
        if (trace.activeIntent() == null) {
            return "-";
        }
        return trace.activeIntent().path() + ":" + trace.activeState();
    }
}
