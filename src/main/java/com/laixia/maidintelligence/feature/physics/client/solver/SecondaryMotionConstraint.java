package com.laixia.maidintelligence.feature.physics.client.solver;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySet;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Immutable swing and reference-space data for one driven bone.
 *
 * <p>Collision shapes live in a generic {@link CollisionProxySet}; this class
 * only owns the animation-relative angular constraint.</p>
 */
public final class SecondaryMotionConstraint {
    private static final float EPSILON = 1.0E-6F;

    private final boolean enabled;
    private final PhysicsBoneSelectionPlan.SimulationSpace simulationSpace;
    private final int referenceNodeIndex;
    private final float rotationInertiaScale;
    private final Vector3f axisLocal;
    private final Vector3f rightLocal;
    private final Vector3f outwardLocal;
    private final float tanLeft;
    private final float tanRight;
    private final float tanOutward;
    private final float tanInward;
    private final float cosMinimumSwing;
    private final CollisionProxySet collisionProxies;

    SecondaryMotionConstraint(
            boolean enabled,
            PhysicsBoneSelectionPlan.SimulationSpace simulationSpace,
            int referenceNodeIndex,
            float rotationInertiaScale,
            Vector3f rightLocal,
            Vector3f outwardLocal,
            PhysicsBoneSelectionPlan.SwingLimits limits,
            CollisionProxySet collisionProxies
    ) {
        this.enabled = enabled;
        this.simulationSpace = simulationSpace;
        this.referenceNodeIndex = referenceNodeIndex;
        this.rotationInertiaScale = rotationInertiaScale;
        this.rightLocal = new Vector3f(rightLocal);
        this.outwardLocal = new Vector3f(outwardLocal);
        this.axisLocal = this.rightLocal.cross(
                this.outwardLocal,
                new Vector3f()
        ).normalize();
        this.tanLeft = tangent(limits.left());
        this.tanRight = tangent(limits.right());
        this.tanOutward = tangent(limits.outward());
        this.tanInward = tangent(limits.inward());
        this.cosMinimumSwing = (float) Math.cos(Math.min(
                Math.min(limits.left(), limits.right()),
                Math.min(limits.outward(), limits.inward())
        ));
        this.collisionProxies = collisionProxies == null
                ? CollisionProxySet.EMPTY
                : collisionProxies;
    }

    public PhysicsBoneSelectionPlan.SimulationSpace simulationSpace() {
        return simulationSpace;
    }

    public boolean enabled() {
        return enabled;
    }

    public int referenceNodeIndex() {
        return referenceNodeIndex;
    }

    public float rotationInertiaScale() {
        return rotationInertiaScale;
    }

    public CollisionProxySet collisionProxies() {
        return collisionProxies;
    }

    public Vector3f outwardDirection(
            Quaternionf boneOrientation,
            Vector3f output
    ) {
        return boneOrientation.transform(outwardLocal, output).normalize();
    }

    public boolean projectSwing(
            Vector3f direction,
            Vector3f restDirection,
            Quaternionf boneOrientation,
            Vector3f localDirection
    ) {
        if (!enabled) {
            return false;
        }
        if (direction.dot(restDirection) >= cosMinimumSwing) {
            return false;
        }
        boneOrientation.transformInverse(direction, localDirection);
        float rawAxial = localDirection.dot(axisLocal);
        float rawHorizontal = localDirection.dot(rightLocal);
        float rawVertical = localDirection.dot(outwardLocal);
        if (rawAxial <= EPSILON
                && Math.abs(rawHorizontal) <= EPSILON
                && Math.abs(rawVertical) <= EPSILON) {
            localDirection.set(axisLocal).add(
                    outwardLocal.x * tanOutward,
                    outwardLocal.y * tanOutward,
                    outwardLocal.z * tanOutward
            ).normalize();
            boneOrientation.transform(localDirection, direction).normalize();
            return true;
        }
        float axial = Math.max(rawAxial, EPSILON);
        float horizontal = rawHorizontal / axial;
        float vertical = rawVertical / axial;
        float horizontalLimit = horizontal < 0.0F
                ? tanLeft
                : tanRight;
        float verticalLimit = vertical < 0.0F
                ? tanInward
                : tanOutward;
        float normalizedHorizontal =
                normalizeSlope(horizontal, horizontalLimit);
        float normalizedVertical =
                normalizeSlope(vertical, verticalLimit);
        float ellipse = normalizedHorizontal * normalizedHorizontal
                + normalizedVertical * normalizedVertical;
        if (ellipse <= 1.0F) {
            return false;
        }

        float scale = (float) (1.0D / Math.sqrt(ellipse));
        horizontal *= scale;
        vertical *= scale;
        localDirection.set(axisLocal)
                .add(
                        rightLocal.x * horizontal,
                        rightLocal.y * horizontal,
                        rightLocal.z * horizontal
                )
                .add(
                        outwardLocal.x * vertical,
                        outwardLocal.y * vertical,
                        outwardLocal.z * vertical
                )
                .normalize();
        boneOrientation.transform(localDirection, direction).normalize();
        return true;
    }

    private static float tangent(float radians) {
        return (float) Math.tan(Math.max(0.0F, radians));
    }

    private static float normalizeSlope(float slope, float limit) {
        if (limit <= EPSILON) {
            return Math.abs(slope) <= EPSILON
                    ? 0.0F
                    : Math.copySign(Float.MAX_VALUE, slope);
        }
        return slope / limit;
    }
}
