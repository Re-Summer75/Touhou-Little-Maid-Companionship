package com.laixia.maidintelligence.feature.physics.port;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;

/**
 * Writes the final core pose back to the adapter-owned animated skeleton.
 */
@FunctionalInterface
public interface PoseWriterPort {
    void writePose(BoneModelSnapshot model);
}
