package com.laixia.maidintelligence.feature.physics.discovery.structure;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;



import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Immutable identity-keyed structure lookup for one animated model instance.
 */
public final class BoneStructureAnalysis {
    private final Map<BoneModelSnapshot.Bone, BoneStructureMetrics> metrics;
    private final Map<BoneModelSnapshot.Bone, ClothAccessoryMetrics> clothAccessories;

    BoneStructureAnalysis(
            IdentityHashMap<BoneModelSnapshot.Bone, BoneStructureMetrics> metrics,
            IdentityHashMap<BoneModelSnapshot.Bone, ClothAccessoryMetrics>
                    clothAccessories
    ) {
        this.metrics = Collections.unmodifiableMap(
                new IdentityHashMap<>(metrics)
        );
        this.clothAccessories = Collections.unmodifiableMap(
                new IdentityHashMap<>(clothAccessories)
        );
    }

    public BoneStructureMetrics metrics(BoneModelSnapshot.Bone bone) {
        return metrics.getOrDefault(bone, BoneStructureMetrics.NONE);
    }

    public ClothAccessoryMetrics clothAccessory(BoneModelSnapshot.Bone bone) {
        return clothAccessories.getOrDefault(
                bone,
                ClothAccessoryMetrics.NONE
        );
    }
}
