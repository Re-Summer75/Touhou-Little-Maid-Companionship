package com.laixia.maidintelligence.feature.physics.engine.collision.bake;


import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;

/**
 * Selects the automatic collision sources. Mesh boxes cover the rigid body
 * wherever they are reachable; the fitted torso capsule remains the fallback
 * for models that expose no usable rigid mesh.
 */
final class AutomaticCollisionPolicy {
    private AutomaticCollisionPolicy() {
    }

    /**
     * Mesh-derived boxes follow the same opt-out switches as the legacy
     * automatic proxies, so {@code collision.auto=false} still disables all
     * generated collision.
     */
    static boolean allowsMesh(
            PhysicsBoneSelectionPlan.Decision decision
    ) {
        PhysicsBoneSelectionPlan.ConstraintProfile profile =
                decision.constraints();
        return profile.enabled()
                && profile.collision().auto()
                && profile.collision().segmented()
                && decision.type()
                != PhysicsBoneSelectionPlan.PartType.HEAD_SHELL;
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
