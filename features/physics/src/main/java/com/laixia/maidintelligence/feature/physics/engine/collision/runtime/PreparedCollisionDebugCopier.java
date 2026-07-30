package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;


import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import org.joml.Vector3f;

/**
 * Keeps debug snapshot formatting outside the runtime projection record.
 */
final class PreparedCollisionDebugCopier {
    private PreparedCollisionDebugCopier() {
    }

    static void copy(
            PreparedCollisionProxy source,
            Vector3f currentDirection,
            CollisionProxyDebugData output,
            CollisionScratch scratch
    ) {
        output.reset();
        output.kind = source.kind();
        output.source = source.source();
        output.referenceNodeIndex = source.referenceNodeIndex();
        output.referenceOrigin.set(source.referenceOrigin());
        output.runtimePivot.set(source.pivot()).add(source.referenceOrigin());
        output.scaledHitRadius = source.preparedHitRadius();
        output.leverArm = source.preparedLeverArm();
        float colliderRadius = Math.max(
                0.0F,
                source.preparedRadius() - source.preparedHitRadius()
        );
        switch (source.kind()) {
            case PLANE -> {
                output.planePoint.set(source.pointA())
                        .add(source.referenceOrigin());
                output.planeNormal.set(source.normal());
            }
            case SPHERE -> {
                output.sphereCenter.set(source.pointA())
                        .add(source.referenceOrigin());
                output.sphereRadius = colliderRadius;
            }
            case CAPSULE -> {
                output.capsuleStart.set(source.pointA())
                        .add(source.referenceOrigin());
                output.capsuleEnd.set(source.pointB())
                        .add(source.referenceOrigin());
                output.capsuleRadius = colliderRadius;
            }
            case BOX -> {
                output.boxCenter.set(source.pointA())
                        .add(source.referenceOrigin());
                output.boxHalfExtents.set(source.halfExtents());
                output.boxAxisX.set(source.axisX());
                output.boxAxisY.set(source.axisY());
                output.boxAxisZ.set(source.axisZ());
                output.boxOpenAxis = source.openAxis();
            }
        }
        output.clearance = source.clearance(currentDirection, scratch);
        output.penetrating = output.clearance < 0.0F;
    }
}
