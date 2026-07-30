package com.laixia.maidintelligence.feature.physics.client.discovery.structure;

/**
 * Model-load-time evidence for lower cloth and articulated ornaments.
 */
public record ClothAccessoryMetrics(
        boolean lowerBodyPanel,
        boolean narrowBodyPendant,
        boolean sideMountedHeadAccessory,
        boolean upperEdgeAttached,
        boolean hangingHeadAccessory,
        boolean headEnclosingWearable,
        boolean facialDescendants,
        boolean dominantAttachmentBody,
        boolean rigidClothMount,
        boolean singleBoneCloth
) {
    public static final ClothAccessoryMetrics NONE =
            new ClothAccessoryMetrics(
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false
            );
}
