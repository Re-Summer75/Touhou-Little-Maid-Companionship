package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProjector;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import org.joml.Vector3f;

/**
 * Selective relative-motion sweep for bounded colliders.
 *
 * <p>Only a pair that was observed on consecutive frames and moved farther
 * than its thinnest dimension pays two to four interpolated checks. Both frame
 * endpoints must be clear; this distinguishes tunnelling through a collider
 * from ordinary contact that is now separating.
 */
final class PreparedCollisionSweep {
    private static final float EPSILON = 1.0E-6F;
    private static final float MIN_THICKNESS = 1.0F / 256.0F;
    private static final float MIN_SWEEP_TRAVEL = 0.25F;
    private static final int MAX_SUBSTEPS = 4;

    private PreparedCollisionSweep() {
    }

    static void beginFrame(PreparedCollisionProxy proxy) {
        if (proxy.sweepFrame == proxy.reserveFrame) {
            return;
        }
        proxy.sweepFrame = proxy.reserveFrame;
        proxy.sweepDirectionAvailable =
                proxy.reserveFrame != PreparedCollisionProxyMotion.NO_FRAME
                        && proxy.lastDirectionFrame
                        == proxy.reserveFrame - 1
                        && proxy.sweepPivotFrame == proxy.reserveFrame;
        if (proxy.sweepDirectionAvailable) {
            proxy.sweepFromDirection.set(proxy.lastDirection);
        }
    }

    static boolean project(
            PreparedCollisionProxy proxy,
            Vector3f direction,
            float arm,
            float radius,
            float hitRadius,
            boolean tip,
            CollisionScratch collisionScratch,
            PreparedCollisionSweepScratch sweep
    ) {
        if (!consume(proxy, tip)
                || !proxy.sweepDirectionAvailable
                || !proxy.shape.sweepAvailable()
                || !bounded(proxy.shape.kind())) {
            return false;
        }
        float shapeMotion = proxy.shape.motionBound();
        if (!Float.isFinite(shapeMotion)) {
            return false;
        }
        float directionX = proxy.sweepFromDirection.x - direction.x;
        float directionY = proxy.sweepFromDirection.y - direction.y;
        float directionZ = proxy.sweepFromDirection.z - direction.z;
        float pivotX = proxy.sweepFromPivot.x - proxy.pivot.x;
        float pivotY = proxy.sweepFromPivot.y - proxy.pivot.y;
        float pivotZ = proxy.sweepFromPivot.z - proxy.pivot.z;
        float upperMotion = shapeMotion + arm * (
                Math.abs(directionX)
                        + Math.abs(directionY)
                        + Math.abs(directionZ)
        ) + Math.abs(pivotX) + Math.abs(pivotY) + Math.abs(pivotZ);
        if (upperMotion <= MIN_SWEEP_TRAVEL) {
            return false;
        }
        float relativeMotion = shapeMotion
                + arm * (float) Math.sqrt(
                directionX * directionX
                        + directionY * directionY
                        + directionZ * directionZ
        )
                + (float) Math.sqrt(
                pivotX * pivotX + pivotY * pivotY + pivotZ * pivotZ
        );
        if (relativeMotion <= MIN_SWEEP_TRAVEL) {
            return false;
        }
        float thickness = Math.max(
                MIN_SWEEP_TRAVEL,
                Math.max(MIN_THICKNESS, sweepThickness(proxy.shape))
        );
        if (relativeMotion <= thickness) {
            return false;
        }
        if (!crossesBoundingVolume(proxy, direction, arm, hitRadius)) {
            return false;
        }
        int estimated = (int) Math.ceil(relativeMotion / thickness);
        /*
         * Three equal intervals omit the midpoint, exactly where a collider
         * crossing from one clear side to the other is usually deepest.
         */
        int steps = estimated <= 2 ? 2 : MAX_SUBSTEPS;
        float previous = clearanceAt(
                proxy, proxy.sweepFromDirection, arm, radius, hitRadius,
                0.0F, collisionScratch, sweep
        );
        float current = PreparedCollisionProxyProjection.clearanceAt(
                proxy, direction, arm, radius, hitRadius, collisionScratch
        );
        if (!(previous > 0.0F) || !(current > 0.0F)) {
            return false;
        }

        float deepest = 0.0F;
        float contactTime = -1.0F;
        for (int step = 1; step < steps; step++) {
            float time = (float) step / steps;
            directionAt(proxy, direction, time, sweep.direction);
            float clearance = clearanceAt(
                    proxy, sweep.direction, arm, radius, hitRadius,
                    time, collisionScratch, sweep
            );
            if (clearance < deepest) {
                deepest = clearance;
                contactTime = time;
            }
        }
        if (contactTime < 0.0F) {
            return false;
        }

        directionAt(proxy, direction, contactTime, sweep.direction);
        sweep.contactStart.set(sweep.direction);
        boolean moved = projectAt(
                proxy, sweep.direction, arm, radius, hitRadius,
                contactTime, collisionScratch, sweep
        );
        if (moved) {
            /*
             * Position-level CCD commits the deepest sampled legal pose. Merely
             * adding its tiny correction to the already-crossed endpoint still
             * leaves that endpoint on the far side.
             */
            direction.set(sweep.direction);
        }
        return moved;
    }

    private static boolean consume(
            PreparedCollisionProxy proxy,
            boolean tip
    ) {
        if (proxy.reserveFrame == PreparedCollisionProxyMotion.NO_FRAME) {
            return false;
        }
        if (tip) {
            if (proxy.tipSweepFrame == proxy.reserveFrame) {
                return false;
            }
            proxy.tipSweepFrame = proxy.reserveFrame;
        } else {
            if (proxy.bodySweepFrame == proxy.reserveFrame) {
                return false;
            }
            proxy.bodySweepFrame = proxy.reserveFrame;
        }
        return true;
    }

    private static float clearanceAt(
            PreparedCollisionProxy proxy,
            Vector3f direction,
            float arm,
            float radius,
            float hitRadius,
            float time,
            CollisionScratch collisionScratch,
            PreparedCollisionSweepScratch sweep
    ) {
        interpolate(proxy.shape, time, sweep);
        interpolatePivot(proxy, time, sweep.pivot);
        return switch (proxy.shape.kind()) {
            case BOX -> CollisionProjector.boxClearance(
                    direction, sweep.pivot, sweep.center,
                    sweep.axisX, sweep.axisY, sweep.axisZ, sweep.half,
                    hitRadius, proxy.shape.openAxis(),
                    proxy.shape.hiddenFaces(), arm, collisionScratch
            );
            case SPHERE -> CollisionProjector.sphereClearance(
                    direction, sweep.pivot, sweep.center,
                    interpolatedRadius(proxy.shape, radius, time),
                    arm, collisionScratch
            );
            default -> Float.POSITIVE_INFINITY;
        };
    }

    private static boolean projectAt(
            PreparedCollisionProxy proxy,
            Vector3f direction,
            float arm,
            float radius,
            float hitRadius,
            float time,
            CollisionScratch collisionScratch,
            PreparedCollisionSweepScratch sweep
    ) {
        interpolate(proxy.shape, time, sweep);
        interpolatePivot(proxy, time, sweep.pivot);
        return switch (proxy.shape.kind()) {
            case BOX -> CollisionProjector.projectBox(
                    direction, sweep.pivot, sweep.center,
                    sweep.axisX, sweep.axisY, sweep.axisZ, sweep.half,
                    hitRadius, proxy.shape.openAxis(),
                    proxy.shape.hiddenFaces(), arm, collisionScratch
            );
            case SPHERE -> CollisionProjector.projectSphere(
                    direction, sweep.pivot, sweep.center,
                    interpolatedRadius(proxy.shape, radius, time),
                    arm, collisionScratch
            );
            default -> false;
        };
    }

    private static void interpolate(
            PreparedCollisionShape shape,
            float time,
            PreparedCollisionSweepScratch sweep
    ) {
        sweep.center.set(shape.sweepCenter()).lerp(shape.pointA(), time);
        if (shape.kind() != CollisionProxyKind.BOX) {
            return;
        }
        interpolateHalfAxis(
                shape.sweepHalfX(), shape.axisX(), shape.halfExtents().x,
                time, sweep.axisX, sweep.half, 0
        );
        interpolateHalfAxis(
                shape.sweepHalfY(), shape.axisY(), shape.halfExtents().y,
                time, sweep.axisY, sweep.half, 1
        );
        interpolateHalfAxis(
                shape.sweepHalfZ(), shape.axisZ(), shape.halfExtents().z,
                time, sweep.axisZ, sweep.half, 2
        );
    }

    private static void interpolateHalfAxis(
            Vector3f previous,
            Vector3f currentAxis,
            float currentExtent,
            float time,
            Vector3f outputAxis,
            Vector3f outputHalf,
            int axis
    ) {
        outputAxis.set(previous)
                .mul(1.0F - time)
                .fma(time * currentExtent, currentAxis);
        float extent = outputAxis.length();
        if (!Float.isFinite(extent) || extent <= EPSILON) {
            outputAxis.set(currentAxis);
            extent = Math.max(EPSILON, currentExtent);
        } else {
            outputAxis.div(extent);
        }
        switch (axis) {
            case 0 -> outputHalf.x = extent;
            case 1 -> outputHalf.y = extent;
            default -> outputHalf.z = extent;
        }
    }

    private static void directionAt(
            PreparedCollisionProxy proxy,
            Vector3f current,
            float time,
            Vector3f output
    ) {
        output.set(proxy.sweepFromDirection).lerp(current, time);
        float lengthSquared = output.lengthSquared();
        if (!Float.isFinite(lengthSquared) || lengthSquared <= EPSILON) {
            output.set(current);
        } else {
            output.div((float) Math.sqrt(lengthSquared));
        }
    }

    private static void interpolatePivot(
            PreparedCollisionProxy proxy,
            float time,
            Vector3f output
    ) {
        output.set(proxy.sweepFromPivot).lerp(proxy.pivot, time);
    }

    private static float interpolatedRadius(
            PreparedCollisionShape shape,
            float currentCombinedRadius,
            float time
    ) {
        float padding = currentCombinedRadius - shape.scaledRadius();
        return Math.max(
                0.0F,
                shape.sweepRadius()
                        + (shape.scaledRadius() - shape.sweepRadius()) * time
                        + padding
        );
    }

    private static float sweepThickness(PreparedCollisionShape shape) {
        if (shape.kind() == CollisionProxyKind.SPHERE) {
            return 2.0F * Math.min(
                    shape.sweepRadius(), shape.scaledRadius()
            );
        }
        float previous = Math.min(
                shape.sweepHalfX().length(),
                Math.min(
                        shape.sweepHalfY().length(),
                        shape.sweepHalfZ().length()
                )
        );
        return Math.min(2.0F * previous, shape.sweepThickness());
    }

    /**
     * Cheap relative segment-vs-sphere sweep. Ordinary near contacts start or
     * end inside this conservative volume and stay on the discrete path; only
     * a pair clear at both endpoints but crossing between them reaches the
     * interpolated narrow phase.
     */
    private static boolean crossesBoundingVolume(
            PreparedCollisionProxy proxy,
            Vector3f currentDirection,
            float arm,
            float hitRadius
    ) {
        PreparedCollisionShape shape = proxy.shape;
        float previousBound = previousBound(shape);
        float currentBound = shape.cullRadius();
        if (!Float.isFinite(previousBound)
                || !Float.isFinite(currentBound)) {
            return false;
        }
        float reach = Math.max(previousBound, currentBound)
                + hitRadius + proxy.meshCullReach;
        float previousX = proxy.sweepFromPivot.x
                + proxy.sweepFromDirection.x * arm
                - shape.sweepCenter().x;
        float previousY = proxy.sweepFromPivot.y
                + proxy.sweepFromDirection.y * arm
                - shape.sweepCenter().y;
        float previousZ = proxy.sweepFromPivot.z
                + proxy.sweepFromDirection.z * arm
                - shape.sweepCenter().z;
        float currentX = proxy.pivot.x + currentDirection.x * arm
                - shape.pointA().x;
        float currentY = proxy.pivot.y + currentDirection.y * arm
                - shape.pointA().y;
        float currentZ = proxy.pivot.z + currentDirection.z * arm
                - shape.pointA().z;
        float reachSquared = reach * reach;
        float previousDistance = previousX * previousX
                + previousY * previousY + previousZ * previousZ;
        float currentDistance = currentX * currentX
                + currentY * currentY + currentZ * currentZ;
        if (previousDistance <= reachSquared
                || currentDistance <= reachSquared) {
            return false;
        }
        float vx = currentX - previousX;
        float vy = currentY - previousY;
        float vz = currentZ - previousZ;
        float travelSquared = vx * vx + vy * vy + vz * vz;
        if (travelSquared <= EPSILON) {
            return false;
        }
        float time = -(previousX * vx + previousY * vy + previousZ * vz)
                / travelSquared;
        time = Math.max(0.0F, Math.min(1.0F, time));
        float closestX = previousX + vx * time;
        float closestY = previousY + vy * time;
        float closestZ = previousZ + vz * time;
        return closestX * closestX + closestY * closestY
                + closestZ * closestZ <= reachSquared;
    }

    private static float previousBound(PreparedCollisionShape shape) {
        if (shape.kind() == CollisionProxyKind.SPHERE) {
            return shape.sweepRadius();
        }
        float x = shape.sweepHalfX().lengthSquared();
        float y = shape.sweepHalfY().lengthSquared();
        float z = shape.sweepHalfZ().lengthSquared();
        return (float) Math.sqrt(x + y + z);
    }

    private static boolean bounded(CollisionProxyKind kind) {
        return kind == CollisionProxyKind.BOX
                || kind == CollisionProxyKind.SPHERE;
    }
}
