package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;

/**
 * Selects the full schema-v3 policy or legacy Head-root compatibility path.
 */
final class AutomaticCollisionPolicy {
    private AutomaticCollisionPolicy() {
    }

    static CollisionProxyPlan.Automatic select(
            PhysicsBoneSelectionPlan.Decision decision,
            PhysicsBoneSelectionPlan.SimulationSpace space,
            PhysicsBoneGeometry.Node drivenNode,
            PhysicsBoneSelectionPlan selectionPlan
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
            return legacy(decision, space, drivenNode, selectionPlan);
        }
        return switch (decision.type()) {
            case HAIR, EAR -> head(profile);
            case SKIRT -> CollisionProxyPlan.Automatic.SKIRT;
            case CAPE -> CollisionProxyPlan.Automatic.CAPE;
            case RIBBON -> space
                    == PhysicsBoneSelectionPlan.SimulationSpace.HEAD_LOCAL
                    ? head(profile)
                    : space
                    == PhysicsBoneSelectionPlan.SimulationSpace.BODY_LOCAL
                    ? CollisionProxyPlan.Automatic.BODY
                    : CollisionProxyPlan.Automatic.NONE;
            default -> CollisionProxyPlan.Automatic.NONE;
        };
    }

    private static CollisionProxyPlan.Automatic legacy(
            PhysicsBoneSelectionPlan.Decision decision,
            PhysicsBoneSelectionPlan.SimulationSpace space,
            PhysicsBoneGeometry.Node drivenNode,
            PhysicsBoneSelectionPlan selectionPlan
    ) {
        if (drivenNode != null
                && drivenNode.parent() != null
                && selectionPlan.isDriven(drivenNode.parent().bone())) {
            return CollisionProxyPlan.Automatic.NONE;
        }
        return switch (decision.type()) {
            case HAIR, EAR -> head(decision.constraints());
            case RIBBON -> space
                    == PhysicsBoneSelectionPlan.SimulationSpace.HEAD_LOCAL
                    ? head(decision.constraints())
                    : CollisionProxyPlan.Automatic.NONE;
            default -> CollisionProxyPlan.Automatic.NONE;
        };
    }

    private static CollisionProxyPlan.Automatic head(
            PhysicsBoneSelectionPlan.ConstraintProfile profile
    ) {
        return profile.backstop() || profile.headCollision()
                ? CollisionProxyPlan.Automatic.HEAD
                : CollisionProxyPlan.Automatic.NONE;
    }
}
