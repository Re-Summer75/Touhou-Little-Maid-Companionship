package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import org.joml.Matrix4f;

/**
 * Narrow read-only view of transforms produced by the spring pose pass.
 */
public interface CollisionFrameSource {
    boolean hasRenderedTransform(int nodeIndex);

    Matrix4f renderedTransform(int nodeIndex);
}
