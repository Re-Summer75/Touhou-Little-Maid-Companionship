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
        return String.format(
                Locale.ROOT,
                " structure=%s segment=%s"
                        + " profile=(stiff=%.2f,drag=%.2f,inertia=%.2f,"
                        + "turn=%.2f,angle=%.2f,tip=%.2f)"
                        + " rotationInertia=%.2f",
                decision.structureRole(),
                segmentText,
                profile.stiffnessScale(),
                profile.dragScale(),
                profile.inertiaScale(),
                profile.turnScale(),
                profile.angleScale(),
                profile.tipDisplacementScale(),
                decision.constraints().rotationInertiaScale()
        );
    }
}
