package com.laixia.maidintelligence.feature.physics.client;

import java.util.Locale;

/**
 * Formats baked structure and segment dynamics outside the skeleton walker.
 */
final class PhysicsDecisionDebugText {
    private PhysicsDecisionDebugText() {
    }

    static String describe(PhysicsBoneSelectionPlan.Decision decision) {
        PhysicsBoneSelectionPlan.ChainSegment segment =
                decision.chainSegment();
        String segmentText = segment.present()
                ? String.format(
                Locale.ROOT,
                "%d/%d@%s",
                segment.index() + 1,
                segment.count(),
                segment.rootPath()
        )
                : "none";
        PhysicsBoneSelectionPlan.SpringProfile profile = decision.profile();
        PhysicsBoneSelectionPlan.ConstraintProfile constraints =
                decision.constraints();
        PhysicsBoneSelectionPlan.SwingLimits swing =
                constraints.swingLimits();
        return String.format(
                Locale.ROOT,
                " structure=%s segment=%s"
                        + " profile=(stiff=%.2f,gravity=%.3f,drag=%.2f,"
                        + "inertia=%.2f,"
                        + "turn=%.2f,angle=%.2f,tip=%.2f)"
                        + " constraints=(space=%s,rotationInertia=%.2f,"
                        + "swing=%.1f/%.1f/%.1f/%.1fdeg,"
                        + "backstop=%s,head=%s,collision=%s/%s)",
                decision.structureRole(),
                segmentText,
                profile.stiffnessScale(),
                profile.gravityScale(),
                profile.dragScale(),
                profile.inertiaScale(),
                profile.turnScale(),
                profile.angleScale(),
                profile.tipDisplacementScale(),
                constraints.simulationSpace(),
                constraints.rotationInertiaScale(),
                Math.toDegrees(swing.left()),
                Math.toDegrees(swing.right()),
                Math.toDegrees(swing.outward()),
                Math.toDegrees(swing.inward()),
                constraints.backstop(),
                constraints.headCollision(),
                constraints.collision().auto(),
                constraints.collision().segmented()
        );
    }
}
