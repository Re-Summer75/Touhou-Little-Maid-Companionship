package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.RuntimeCollisionCache;

/**
 * Aggregates one solver instance's preallocated runtime modules.
 */
final class SpringBoneContext {
    final PhysicsSolverLayout layout;
    final boolean constraintsEnabled;
    final SpringBoneState state;
    final SpringBoneScratch scratch;
    final SpringBoneMetrics metrics;
    final RuntimeBoneEndpoints endpoints;
    final RuntimeCollisionFrames collisionFrames;
    final RuntimeCollisionCache collisionCache;

    SpringBoneContext(
            PhysicsSolverLayout layout,
            boolean constraintsEnabled
    ) {
        this.layout = layout;
        this.constraintsEnabled = constraintsEnabled;
        this.state = new SpringBoneState(layout);
        this.scratch = new SpringBoneScratch();
        this.metrics = new SpringBoneMetrics();
        this.endpoints = new RuntimeBoneEndpoints(layout);
        this.collisionFrames = new RuntimeCollisionFrames(layout);
        this.collisionCache = new RuntimeCollisionCache(
                layout,
                collisionFrames
        );
    }
}
