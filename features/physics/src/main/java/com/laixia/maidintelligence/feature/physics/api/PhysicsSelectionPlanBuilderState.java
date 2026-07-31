package com.laixia.maidintelligence.feature.physics.api;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.BoneKinematics;

import java.util.IdentityHashMap;

/**
 * Owns the mutable, identity-keyed state behind the public builder facade.
 */
final class PhysicsSelectionPlanBuilderState {
    private final String modelId;
    private final IdentityHashMap<
            BoneModelSnapshot.Bone,
            PhysicsBoneSelectionPlan.Decision
            > decisions = new IdentityHashMap<>();
    private final IdentityHashMap<BoneModelSnapshot.Bone, String> paths =
            new IdentityHashMap<>();
    private final IdentityHashMap<
            BoneModelSnapshot.Bone,
            BoneKinematics.Metrics
            > kinematics = new IdentityHashMap<>();

    PhysicsSelectionPlanBuilderState(String modelId) {
        this.modelId = modelId;
    }

    void path(BoneModelSnapshot.Bone bone, String path) {
        paths.put(bone, path);
    }

    void decide(
            BoneModelSnapshot.Bone bone,
            PhysicsBoneSelectionPlan.Decision decision
    ) {
        decisions.put(bone, decision);
    }

    void kinematics(
            BoneModelSnapshot.Bone bone,
            BoneKinematics.Metrics metrics
    ) {
        if (metrics != null) {
            kinematics.put(bone, metrics);
        }
    }

    PhysicsBoneSelectionPlan.Decision current(BoneModelSnapshot.Bone bone) {
        return decisions.get(bone);
    }

    String pathOf(BoneModelSnapshot.Bone bone) {
        return paths.getOrDefault(bone, bone.getName());
    }

    PhysicsBoneSelectionPlan build() {
        return new PhysicsBoneSelectionPlan(
                modelId,
                decisions,
                paths,
                kinematics
        );
    }
}
