package com.laixia.maidintelligence.feature.physics.discovery.metadata;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadata;

import java.util.Set;

/**
 * Narrow stage boundary for metadata binding and rejection policies.
 */
public final class MetadataPlanProcessor {
    private MetadataPlanProcessor() {
    }

    public static Set<BoneModelSnapshot.Bone> apply(
            String modelId,
            PhysicsBoneSelectionPlan.Builder plan,
            PhysicsBoneGeometry.Analysis geometry,
            PhysicsMetadata metadata
    ) {
        return MetadataPlanApplier.apply(modelId, plan, geometry, metadata);
    }

    public static void rejectExcluded(
            PhysicsBoneSelectionPlan.Builder plan,
            PhysicsBoneGeometry.Analysis geometry,
            Set<BoneModelSnapshot.Bone> excluded,
            String origin
    ) {
        MetadataRejections.rejectExcluded(plan, geometry, excluded, origin);
    }

    public static void rejectUnlistedExplicit(
            PhysicsBoneSelectionPlan.Builder plan,
            PhysicsBoneGeometry.Analysis geometry,
            Set<BoneModelSnapshot.Bone> excluded,
            String origin
    ) {
        MetadataRejections.rejectUnlistedExplicit(
                plan,
                geometry,
                excluded,
                origin
        );
    }
}
