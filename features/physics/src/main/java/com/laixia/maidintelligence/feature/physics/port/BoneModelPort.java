package com.laixia.maidintelligence.feature.physics.port;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;

/**
 * Reads a stable model snapshot and refreshes its animation pose in place.
 */
public interface BoneModelPort {
    BoneModelSnapshot model();

    /**
     * Copies the current animation-only local pose into {@link #model()}.
     */
    void readAnimationPose();
}
