package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;

/**
 * Selects automatic torso collision only. Head and leg proxies are disabled;
 * authors can still opt into either area with schema-v3 explicit proxies.
 */
final class AutomaticCollisionPolicy {
    private AutomaticCollisionPolicy() {
    }

    static CollisionProxyPlan.Automatic select(
            PhysicsBoneSelectionPlan.Decision decision,
            PhysicsBoneSelectionPlan.SimulationSpace space
    ) {
        PhysicsBoneSelectionPlan.ConstraintProfile profile =
                decision.constraints();
        if (!profile.enabled()
                || !profile.collision().auto()
                || decision.type()
                == PhysicsBoneSelectionPlan.PartType.HEAD_SHELL) {
            return CollisionProxyPlan.Automatic.NONE;
        }
        if (!profile.collision().segmented()) {
            return CollisionProxyPlan.Automatic.NONE;
        }
        return switch (decision.type()) {
            case SKIRT -> CollisionProxyPlan.Automatic.BODY;
            case CAPE -> CollisionProxyPlan.Automatic.CAPE;
            case RIBBON -> space
                    == PhysicsBoneSelectionPlan.SimulationSpace.BODY_LOCAL
                    ? CollisionProxyPlan.Automatic.BODY
                    : CollisionProxyPlan.Automatic.NONE;
            default -> CollisionProxyPlan.Automatic.NONE;
        };
    }
}
