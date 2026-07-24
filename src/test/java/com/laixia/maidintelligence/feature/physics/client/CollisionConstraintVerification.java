package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.client.SecondaryMotionFixture.Fixture;
import com.laixia.maidintelligence.feature.physics.client.solver.SecondaryMotionConstraint;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionScratch;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireNear;

final class CollisionConstraintVerification {
    private static final float EPSILON = 1.0E-4F;

    private CollisionConstraintVerification() {
    }

    static void run() {
        CollisionProxyFrameworkVerification.run();
        verifiesBackstopProjection();
        verifiesHeadSphereProjection();
        verifiesConstraintInvariantsAcrossFrameRates();
        CollisionProxyGeometryVerification.run();
    }

    private static void verifiesBackstopProjection() {
        Fixture fixture = SecondaryMotionFixture.create(
                60.0F,
                0.0F,
                true,
                false
        );
        SecondaryMotionConstraint constraint =
                fixture.hairNode().constraint();
        require(
                constraint.collisionProxies().hasKind(
                        CollisionProxyKind.PLANE
                ),
                "Backstop Plane proxy was not generated"
        );
        int projections = driveInward(fixture);
        float clearance = constraint.collisionProxies().clearance(
                constraint.collisionProxies().firstIndex(
                        CollisionProxyKind.PLANE
                ),
                currentDirection(fixture),
                new Quaternionf(),
                new CollisionScratch()
        );
        require(projections > 0, "Backstop projection never ran");
        require(clearance >= -EPSILON, "Backstop was penetrated");
    }

    private static void verifiesHeadSphereProjection() {
        Fixture fixture = SecondaryMotionFixture.create(
                60.0F,
                0.0F,
                false,
                true
        );
        SecondaryMotionConstraint constraint =
                fixture.hairNode().constraint();
        require(
                constraint.collisionProxies().hasKind(
                        CollisionProxyKind.SPHERE
                ),
                "Head sphere proxy was not generated"
        );
        int projections = driveInward(fixture);
        float clearance = constraint.collisionProxies().clearance(
                constraint.collisionProxies().firstIndex(
                        CollisionProxyKind.SPHERE
                ),
                currentDirection(fixture),
                new Quaternionf(),
                new CollisionScratch()
        );
        require(projections > 0, "Head sphere projection never ran");
        require(clearance >= -EPSILON, "Head sphere was penetrated");
    }

    private static void verifiesConstraintInvariantsAcrossFrameRates() {
        int[] frameRates = {20, 30, 60, 120};
        for (int fps : frameRates) {
            Fixture fixture = SecondaryMotionFixture.create(
                    12.0F,
                    0.20F,
                    true,
                    true
            );
            Vector3f acceleration = new Vector3f();
            float finalHeadRotation = 0.0F;
            for (int frame = 0; frame < fps * 2; frame++) {
                float phase = frame / (float) fps;
                float headRotation = (float) Math.sin(phase * Math.PI)
                        * (float) Math.toRadians(45.0D);
                finalHeadRotation = headRotation;
                acceleration.set(
                        phase < 1.0F ? 2.5F : 0.0F,
                        phase > 1.2F && phase < 1.35F ? -1.5F : 0.0F,
                        0.0F
                );
                SecondaryMotionFixture.resetPose(fixture, headRotation);
                fixture.solver().solve(
                        acceleration,
                        0.0F,
                        1.0F / fps,
                        false
                );
            }
            Vector3f direction = currentDirection(fixture);
            requireNear(
                    direction.length(),
                    1.0F,
                    2.0E-4F,
                    "Constraint direction lost unit length at " + fps + " FPS"
            );
            require(
                    Float.isFinite(direction.x)
                            && Float.isFinite(direction.y)
                            && Float.isFinite(direction.z),
                    "Constraint direction became non-finite at " + fps + " FPS"
            );
            requireCombinedConstraints(
                    fixture,
                    direction,
                    finalHeadRotation,
                    fps
            );
        }
    }

    private static void requireCombinedConstraints(
            Fixture fixture,
            Vector3f direction,
            float headRotation,
            int fps
    ) {
        SecondaryMotionConstraint constraint =
                fixture.hairNode().constraint();
        Quaternionf reference = new Quaternionf().rotateZ(headRotation);
        require(
                constraint.collisionProxies().clearance(
                        constraint.collisionProxies().firstIndex(
                                CollisionProxyKind.PLANE
                        ),
                        direction,
                        reference,
                        new CollisionScratch()
                ) >= -EPSILON,
                "Combined Backstop was penetrated at " + fps + " FPS"
        );
        require(
                constraint.collisionProxies().clearance(
                        constraint.collisionProxies().firstIndex(
                                CollisionProxyKind.SPHERE
                        ),
                        direction,
                        reference,
                        new CollisionScratch()
                ) >= -EPSILON,
                "Combined head sphere was penetrated at " + fps + " FPS"
        );
        Vector3f rest = reference.transform(
                new Vector3f(0.0F, -1.0F, 0.0F)
        );
        Vector3f projected = new Vector3f(direction);
        constraint.projectSwing(
                projected,
                rest,
                reference,
                new Vector3f()
        );
        require(
                projected.distance(direction) <= 2.0E-4F,
                "Combined swing limit was violated at " + fps + " FPS"
        );
    }

    private static int driveInward(Fixture fixture) {
        Vector3f acceleration = fixture.hairNode().constraint()
                .outwardDirection(new Quaternionf(), new Vector3f())
                .mul(4.0F);
        int projections = 0;
        for (int frame = 0; frame < 180; frame++) {
            SecondaryMotionFixture.resetPose(fixture, 0.0F);
            fixture.solver().solve(
                    acceleration,
                    0.0F,
                    1.0F / 60.0F,
                    false
            );
            projections += fixture.solver().lastCollisionProjectionCount();
        }
        return projections;
    }

    private static Vector3f currentDirection(Fixture fixture) {
        Vector3f output = new Vector3f();
        require(
                fixture.solver().copyCurrentDirection(
                        fixture.hairNode().drivenSlot(),
                        output
                ),
                "Constraint fixture direction was unavailable"
        );
        return output;
    }
}
