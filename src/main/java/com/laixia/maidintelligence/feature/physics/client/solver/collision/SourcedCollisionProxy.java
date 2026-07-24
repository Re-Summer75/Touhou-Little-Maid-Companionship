package com.laixia.maidintelligence.feature.physics.client.solver.collision;

import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.PreparedCollisionProxy;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class SourcedCollisionProxy implements CollisionProxy {
    private final CollisionProxy delegate;
    private final CollisionProxySource source;

    SourcedCollisionProxy(
            CollisionProxy delegate,
            CollisionProxySource source
    ) {
        this.delegate = delegate;
        this.source = source;
    }

    @Override
    public CollisionProxyKind kind() {
        return delegate.kind();
    }

    @Override
    public CollisionProxySource source() {
        return source;
    }

    @Override
    public int referenceNodeIndex() {
        return delegate.referenceNodeIndex();
    }

    @Override
    public Vector3f copyReferenceOrigin(Vector3f output) {
        return delegate.copyReferenceOrigin(output);
    }

    @Override
    public float hitRadius() {
        return delegate.hitRadius();
    }

    @Override
    public float leverArm() {
        return delegate.leverArm();
    }

    @Override
    public void copyStaticShape(
            Quaternionf referenceRestOrientation,
            PreparedCollisionProxy output,
            CollisionScratch scratch
    ) {
        delegate.copyStaticShape(
                referenceRestOrientation,
                output,
                scratch
        );
    }

    @Override
    public boolean project(
            Vector3f direction,
            Quaternionf referenceOrientation,
            CollisionScratch scratch
    ) {
        return delegate.project(direction, referenceOrientation, scratch);
    }

    @Override
    public float clearance(
            Vector3f direction,
            Quaternionf referenceOrientation,
            CollisionScratch scratch
    ) {
        return delegate.clearance(direction, referenceOrientation, scratch);
    }
}
