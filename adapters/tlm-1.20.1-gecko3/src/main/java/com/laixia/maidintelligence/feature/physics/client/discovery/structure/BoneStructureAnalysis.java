package com.laixia.maidintelligence.feature.physics.client.discovery.structure;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Immutable identity-keyed structure lookup for one animated model instance.
 */
public final class BoneStructureAnalysis {
    private final Map<AnimatedGeoBone, BoneStructureMetrics> metrics;
    private final Map<AnimatedGeoBone, ClothAccessoryMetrics> clothAccessories;

    BoneStructureAnalysis(
            IdentityHashMap<AnimatedGeoBone, BoneStructureMetrics> metrics,
            IdentityHashMap<AnimatedGeoBone, ClothAccessoryMetrics>
                    clothAccessories
    ) {
        this.metrics = Collections.unmodifiableMap(
                new IdentityHashMap<>(metrics)
        );
        this.clothAccessories = Collections.unmodifiableMap(
                new IdentityHashMap<>(clothAccessories)
        );
    }

    public BoneStructureMetrics metrics(AnimatedGeoBone bone) {
        return metrics.getOrDefault(bone, BoneStructureMetrics.NONE);
    }

    public ClothAccessoryMetrics clothAccessory(AnimatedGeoBone bone) {
        return clothAccessories.getOrDefault(
                bone,
                ClothAccessoryMetrics.NONE
        );
    }
}
