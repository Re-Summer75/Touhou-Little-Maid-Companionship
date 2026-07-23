package com.laixia.maidintelligence.feature.physics.client.solver;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Immutable, layout-local constraint data for one driven bone.
 *
 * <p>All vectors are stored in either bone-local or reference-bone-local
 * space. The solver transforms them into model space using reusable scratch
 * objects, so the projection path does not allocate per frame.</p>
 */
public final class SecondaryMotionConstraint {
    private static final float EPSILON = 1.0E-6F;

    private final boolean enabled;
    private final PhysicsBoneSelectionPlan.SimulationSpace simulationSpace;
    private final int referenceNodeIndex;
    private final int collisionReferenceNodeIndex;
    private final float rotationInertiaScale;
    private final Vector3f axisLocal;
    private final Vector3f rightLocal;
    private final Vector3f outwardLocal;
    private final float tanLeft;
    private final float tanRight;
    private final float tanOutward;
    private final float tanInward;
    private final float cosMinimumSwing;
    private final boolean backstop;
    private final boolean headCollision;
    private final Vector3f pivotFromReference;
    private final Vector3f backstopPointFromReference;
    private final Vector3f backstopNormalFromReference;
    private final float headRadius;
    private final float hitRadius;
    private final float leverArm;

    SecondaryMotionConstraint(
            boolean enabled,
            PhysicsBoneSelectionPlan.SimulationSpace simulationSpace,
            int referenceNodeIndex,
            int collisionReferenceNodeIndex,
            float rotationInertiaScale,
            Vector3f rightLocal,
            Vector3f outwardLocal,
            PhysicsBoneSelectionPlan.SwingLimits limits,
            boolean backstop,
            boolean headCollision,
            Vector3f pivotFromReference,
            Vector3f backstopPointFromReference,
            Vector3f backstopNormalFromReference,
            float headRadius,
            float hitRadius,
            float leverArm
    ) {
        this.enabled = enabled;
        this.simulationSpace = simulationSpace;
        this.referenceNodeIndex = referenceNodeIndex;
        this.collisionReferenceNodeIndex = collisionReferenceNodeIndex;
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
        this.backstop = backstop;
        this.headCollision = headCollision;
        this.pivotFromReference = new Vector3f(pivotFromReference);
        this.backstopPointFromReference =
                new Vector3f(backstopPointFromReference);
        this.backstopNormalFromReference =
                new Vector3f(backstopNormalFromReference);
        this.headRadius = headRadius;
        this.hitRadius = hitRadius;
        this.leverArm = leverArm;
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

    public int collisionReferenceNodeIndex() {
        return collisionReferenceNodeIndex;
    }

    public boolean hasBackstop() {
        return backstop;
    }

    public boolean hasHeadCollision() {
        return headCollision;
    }

    public float hitRadius() {
        return hitRadius;
    }

    public Vector3f outwardDirection(
            Quaternionf boneOrientation,
            Vector3f output
    ) {
        return boneOrientation.transform(outwardLocal, output).normalize();
    }

    public float headClearance(
            Vector3f direction,
            Quaternionf referenceOrientation,
            Vector3f pivot,
            Vector3f tip
    ) {
        if (!headCollision) {
            return Float.POSITIVE_INFINITY;
        }
        referenceOrientation.transform(pivotFromReference, pivot);
        tip.set(direction).mul(leverArm).add(pivot);
        return tip.length() - headRadius - hitRadius;
    }

    public float backstopClearance(
            Vector3f direction,
            Quaternionf referenceOrientation,
            Vector3f pivot,
            Vector3f tip,
            Vector3f planePoint,
            Vector3f normal
    ) {
        if (!backstop) {
            return Float.POSITIVE_INFINITY;
        }
        referenceOrientation.transform(pivotFromReference, pivot);
        tip.set(direction).mul(leverArm).add(pivot);
        referenceOrientation.transform(
                backstopPointFromReference,
                planePoint
        );
        referenceOrientation.transform(
                backstopNormalFromReference,
                normal
        ).normalize();
        return (tip.x - planePoint.x) * normal.x
                + (tip.y - planePoint.y) * normal.y
                + (tip.z - planePoint.z) * normal.z
                - hitRadius;
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
        float horizontalLimit = horizontal < 0.0F ? tanLeft : tanRight;
        float verticalLimit = vertical < 0.0F ? tanInward : tanOutward;
        float normalizedHorizontal = normalizeSlope(
                horizontal,
                horizontalLimit
        );
        float normalizedVertical = normalizeSlope(vertical, verticalLimit);
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

    boolean projectCollision(
            Vector3f direction,
            Quaternionf referenceOrientation,
            Vector3f pivot,
            Vector3f tip,
            Vector3f planePoint,
            Vector3f normal
    ) {
        if (!enabled || (!backstop && !headCollision)) {
            return false;
        }
        referenceOrientation.transform(pivotFromReference, pivot);
        float backstopMinimumDot = -2.0F;
        if (backstop) {
            referenceOrientation.transform(
                    backstopPointFromReference,
                    planePoint
            );
            referenceOrientation.transform(
                    backstopNormalFromReference,
                    normal
            ).normalize();
            float pivotDistance =
                    (pivot.x - planePoint.x) * normal.x
                            + (pivot.y - planePoint.y) * normal.y
                            + (pivot.z - planePoint.z) * normal.z;
            backstopMinimumDot =
                    (hitRadius - pivotDistance) / leverArm;
            planePoint.set(normal);
        }

        float sphereMinimumDot = -2.0F;
        if (headCollision) {
            float minimumRadius = headRadius + hitRadius;
            float pivotLength = pivot.length();
            if (pivotLength > EPSILON) {
                normal.set(pivot).div(pivotLength);
                sphereMinimumDot = (
                        minimumRadius * minimumRadius
                                - pivotLength * pivotLength
                                - leverArm * leverArm
                ) / (2.0F * pivotLength * leverArm);
            }
        }
        boolean corrected = false;
        int iterations = backstop && headCollision ? 2 : 1;
        for (int iteration = 0; iteration < iterations; iteration++) {
            if (backstop) {
                corrected |= projectMinimumDot(
                        direction,
                        planePoint,
                        backstopMinimumDot,
                        tip
                );
            }
            if (headCollision && sphereMinimumDot > -2.0F) {
                corrected |= projectMinimumDot(
                        direction,
                        normal,
                        sphereMinimumDot,
                        tip
                );
            }
        }
        return corrected;
    }

    private static boolean projectMinimumDot(
            Vector3f direction,
            Vector3f normal,
            float minimumDot,
            Vector3f tangent
    ) {
        if (minimumDot <= -1.0F) {
            return false;
        }
        minimumDot = Math.min(minimumDot, 1.0F);
        float currentDot = direction.dot(normal);
        if (currentDot + EPSILON >= minimumDot) {
            return false;
        }
        tangent.set(direction).add(
                -normal.x * currentDot,
                -normal.y * currentDot,
                -normal.z * currentDot
        );
        if (tangent.lengthSquared() < EPSILON) {
            if (Math.abs(normal.y) < 0.90F) {
                tangent.set(0.0F, 1.0F, 0.0F).cross(normal);
            } else {
                tangent.set(1.0F, 0.0F, 0.0F).cross(normal);
            }
        }
        tangent.normalize().mul(
                (float) Math.sqrt(
                        Math.max(0.0F, 1.0F - minimumDot * minimumDot)
                )
        );
        direction.set(normal).mul(minimumDot).add(tangent).normalize();
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
