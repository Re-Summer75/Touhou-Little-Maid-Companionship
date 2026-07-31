package com.laixia.maidintelligence.feature.physics.api;

final class PhysicsProfileDefaults {
    private PhysicsProfileDefaults() {
    }

    static PhysicsBoneSelectionPlan.SwingLimits swingLimits(
            PhysicsBoneSelectionPlan.PartType type
    ) {
        if (type == PhysicsBoneSelectionPlan.PartType.SKIRT) {
            return new PhysicsBoneSelectionPlan.SwingLimits(
                    0.45F, 0.45F, 0.55F, 0.14F
            );
        }
        float inward = switch (type) {
            case HEAD_SHELL -> 0.08F;
            case HAIR -> 0.28F;
            case EAR -> 0.22F;
            case RIBBON -> 0.45F;
            default -> 0.80F;
        };
        return new PhysicsBoneSelectionPlan.SwingLimits(
                0.80F, 0.80F, 0.80F, inward
        );
    }

    static PhysicsBoneSelectionPlan.ConstraintProfile constraints(
            PhysicsBoneSelectionPlan.PartType type
    ) {
        float rotationInertia = switch (type) {
            case HEAD_SHELL -> 0.05F;
            case HAIR -> 0.35F;
            case EAR -> 0.15F;
            case RIBBON -> 0.45F;
            case SKIRT, CAPE -> 0.65F;
            default -> 1.0F;
        };
        boolean collideWithHead = type == PhysicsBoneSelectionPlan.PartType.HAIR
                || type == PhysicsBoneSelectionPlan.PartType.EAR
                || type == PhysicsBoneSelectionPlan.PartType.RIBBON;
        return new PhysicsBoneSelectionPlan.ConstraintProfile(
                PhysicsBoneSelectionPlan.SimulationSpace.AUTO,
                rotationInertia,
                swingLimits(type),
                collideWithHead,
                collideWithHead,
                1.0F,
                PhysicsBoneSelectionPlan.CollisionProfile.defaults(),
                true
        );
    }

    static PhysicsBoneSelectionPlan.ConstraintProfile legacyConstraints() {
        return new PhysicsBoneSelectionPlan.ConstraintProfile(
                PhysicsBoneSelectionPlan.SimulationSpace.MODEL,
                1.0F,
                new PhysicsBoneSelectionPlan.SwingLimits(
                        1.55F, 1.55F, 1.55F, 1.55F
                ),
                false,
                false,
                1.0F,
                PhysicsBoneSelectionPlan.CollisionProfile.defaults(),
                false
        );
    }

    static PhysicsBoneSelectionPlan.SpringProfile spring(
            PhysicsBoneSelectionPlan.PartType type
    ) {
        return switch (type) {
            case HEAD_SHELL -> spring(
                    2.20F, 0.0F, 0.0F, 1.50F, 1.50F,
                    0.25F, 0.25F, 0.28F, 0.45F
            );
            case HAIR -> spring(
                    1.00F, 1.00F, 1.45F, 0.80F, 1.00F,
                    1.00F, 1.00F, 1.00F, 1.00F
            );
            case TAIL -> spring(
                    0.85F, 0.70F, 1.65F, 1.00F, 0.85F,
                    1.20F, 1.20F, 1.10F, 1.20F
            );
            case EAR -> spring(
                    1.40F, 0.35F, 0.75F, 0.90F, 1.25F,
                    0.60F, 0.70F, 0.55F, 0.65F
            );
            case SKIRT -> spring(
                    1.20F, 1.15F, 0.90F, 1.35F, 1.10F,
                    0.70F, 0.80F, 0.70F, 1.00F
            );
            case RIBBON -> spring(
                    0.90F, 0.45F, 1.70F, 0.65F, 0.90F,
                    1.20F, 1.30F, 1.00F, 0.80F
            );
            case CAPE -> spring(
                    0.85F, 0.80F, 1.55F, 1.30F, 0.95F,
                    1.00F, 1.00F, 0.85F, 1.10F
            );
            case WING -> spring(
                    1.60F, 0.15F, 0.45F, 1.80F, 1.30F,
                    0.45F, 0.80F, 0.40F, 0.70F
            );
            case GENERIC -> spring(
                    1.00F, 0.70F, 0.55F, 1.00F, 1.00F,
                    0.85F, 0.85F, 0.75F, 0.90F
            );
        };
    }

    private static PhysicsBoneSelectionPlan.SpringProfile spring(
            float stiffness,
            float gravity,
            float wind,
            float mass,
            float drag,
            float inertia,
            float turn,
            float angle,
            float tip
    ) {
        return new PhysicsBoneSelectionPlan.SpringProfile(
                stiffness, gravity, wind, mass, drag,
                inertia, turn, angle, tip
        );
    }
}
