package com.laixia.maidintelligence.feature.physics.layout.pivot;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;

/**
 * Computes geometry-aware angular caps behind the stable layout facade.
 */
public final class PivotSwingRange {
    private static final float MAX_ANGLE = 1.05F;
    private static final float MAX_TIP_DISPLACEMENT = 4.5F;

    private PivotSwingRange() {
    }

    public static float maximum(
            PhysicsSolverLayout.Node node,
            float runtimeSafetyScale
    ) {
        float scale = Float.isFinite(runtimeSafetyScale)
                ? Math.max(1.0E-6F, runtimeSafetyScale)
                : 1.0F;
        PhysicsBoneSelectionPlan.SpringProfile profile =
                node.decision().profile();
        float geometric = Math.min(
                MAX_ANGLE,
                node.kinematics().safeAngle()
        ) * profile.angleScale();
        float displacement = MAX_TIP_DISPLACEMENT
                * profile.tipDisplacementScale()
                / (node.kinematics().leverArm() * scale);
        return Math.max(0.0F, Math.min(geometric, displacement));
    }
}
