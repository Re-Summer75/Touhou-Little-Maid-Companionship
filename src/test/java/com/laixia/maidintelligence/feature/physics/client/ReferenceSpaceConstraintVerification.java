package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.client.SecondaryMotionFixture.Fixture;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireVectorNear;

final class ReferenceSpaceConstraintVerification {
    private static final float EPSILON = 1.0E-4F;

    private ReferenceSpaceConstraintVerification() {
    }

    static void run() {
        verifiesReferenceSpaceTransportAndReset();
        verifiesSlowReferenceRotationAccumulates();
        verifiesMicroscopicReferenceRotationAccumulates();
        verifiesAsymmetricSwingAndVelocityWriteBack();
        verifiesRenderedRotationMatchesProjectedDirection();
    }

    private static void verifiesReferenceSpaceTransportAndReset() {
        Fixture fixture = SecondaryMotionFixture.create(
                8.0F,
                0.0F,
                false,
                false
        );
        Vector3f initial = new Vector3f();
        Vector3f actual = new Vector3f();
        Vector3f expected = new Vector3f();

        solvePaused(fixture, 0.0F);
        require(
                fixture.solver().copyCurrentDirection(
                        fixture.hairNode().drivenSlot(),
                        initial
                ),
                "Reference transport fixture did not initialize"
        );

        float thirty = (float) Math.toRadians(30.0D);
        solvePaused(fixture, thirty);
        new Quaternionf().rotateX(thirty).transform(initial, expected);
        copyCurrent(fixture, actual);
        requireVectorNear(
                actual,
                expected,
                EPSILON,
                "Head-local direction did not follow the reference rotation"
        );

        float oneFifty = (float) Math.toRadians(150.0D);
        solvePaused(fixture, oneFifty);
        new Quaternionf().rotateX(oneFifty).transform(initial, expected);
        copyCurrent(fixture, actual);
        requireVectorNear(
                actual,
                expected,
                EPSILON,
                "Reference discontinuity did not reset to the animation pose"
        );
    }

    private static void verifiesSlowReferenceRotationAccumulates() {
        Fixture fixture = SecondaryMotionFixture.create(
                8.0F, 0.0F, false, false
        );
        Vector3f initial = new Vector3f();
        Vector3f actual = new Vector3f();
        solvePaused(fixture, 0.0F);
        copyCurrent(fixture, initial);
        for (int frame = 1; frame <= 120; frame++) {
            solvePaused(
                    fixture,
                    (float) Math.toRadians(frame * 0.25D)
            );
        }
        copyCurrent(fixture, actual);
        Vector3f expected = new Quaternionf()
                .rotateX((float) Math.toRadians(30.0D))
                .transform(initial, new Vector3f());
        requireVectorNear(
                actual,
                expected,
                EPSILON,
                "Sub-degree reference rotations were discarded"
        );
    }

    private static void verifiesMicroscopicReferenceRotationAccumulates() {
        Fixture fixture = SecondaryMotionFixture.create(
                8.0F, 0.0F, false, false
        );
        Vector3f initial = new Vector3f();
        Vector3f actual = new Vector3f();
        Vector3f expected = new Vector3f();
        solvePaused(fixture, 0.0F);
        copyCurrent(fixture, initial);

        float step = (float) Math.toRadians(0.00005D);
        int frames = 1_000;
        for (int frame = 1; frame <= frames; frame++) {
            solvePaused(fixture, step * frame);
        }
        new Quaternionf()
                .rotateX(step * frames)
                .transform(initial, expected);
        copyCurrent(fixture, actual);
        requireVectorNear(
                actual,
                expected,
                1.0E-5F,
                "Microscopic reference rotations were discarded"
        );
    }

    private static void verifiesAsymmetricSwingAndVelocityWriteBack() {
        Fixture fixture = SecondaryMotionFixture.create(
                8.0F,
                0.0F,
                false,
                false
        );
        solvePaused(fixture, 0.0F);
        Vector3f rest = new Vector3f();
        copyCurrent(fixture, rest);
        Vector3f outward = fixture.hairNode().constraint()
                .outwardDirection(new Quaternionf(), new Vector3f());
        Vector3f acceleration = new Vector3f(outward).mul(3.0F);
        Vector3f current = new Vector3f();
        Vector3f previous = new Vector3f();
        boolean projected = false;
        for (int frame = 0; frame < 180; frame++) {
            SecondaryMotionFixture.resetPose(fixture, 0.0F);
            fixture.solver().solve(
                    acceleration,
                    0.0F,
                    1.0F / 60.0F,
                    false
            );
            if (fixture.solver().lastConstraintProjectionCount() <= 0) {
                continue;
            }
            projected = true;
            copyCurrent(fixture, current);
            fixture.solver().copyPreviousDirection(
                    fixture.hairNode().drivenSlot(),
                    previous
            );
            requireVectorNear(
                    previous,
                    current,
                    EPSILON,
                    "Projected direction retained illegal hidden velocity"
            );
        }
        copyCurrent(fixture, current);
        float inwardAngle = (float) Math.atan2(
                Math.max(0.0F, -current.dot(outward)),
                Math.max(EPSILON, current.dot(rest))
        );
        require(projected, "Swing projection never ran");
        require(
                inwardAngle <= Math.toRadians(8.1D),
                "Asymmetric inward swing limit was exceeded: "
                        + Math.toDegrees(inwardAngle) + " degrees, "
                        + current + ", outward=" + outward
        );
    }

    private static void verifiesRenderedRotationMatchesProjectedDirection() {
        Fixture fixture = SecondaryMotionFixture.create(
                45.0F,
                0.0F,
                false,
                false
        );
        Vector3f outward = fixture.hairNode().constraint()
                .outwardDirection(new Quaternionf(), new Vector3f());
        Vector3f acceleration = new Vector3f(outward).mul(2.0F);
        float animationX = (float) Math.toRadians(45.0D);
        for (int frame = 0; frame < 90; frame++) {
            SecondaryMotionFixture.resetPose(fixture, 0.0F);
            fixture.hair().setRotationX(animationX);
            fixture.solver().solve(
                    acceleration,
                    0.0F,
                    1.0F / 60.0F,
                    false
            );
        }
        Vector3f current = new Vector3f();
        copyCurrent(fixture, current);
        Vector3f axis = fixture.hairNode().axisInto(new Vector3f());
        Vector3f rendered = new Quaternionf().rotateZYX(
                fixture.hair().getRotationZ(),
                fixture.hair().getRotationY(),
                fixture.hair().getRotationX()
        ).transform(axis, new Vector3f());
        requireVectorNear(
                rendered,
                current,
                2.0E-4F,
                "Rendered quaternion did not match the projected direction"
        );
    }

    private static void solvePaused(Fixture fixture, float headRotationX) {
        SecondaryMotionFixture.resetPose(fixture, headRotationX, 0.0F);
        fixture.solver().solve(new Vector3f(), 0.0F, 0.0F, true);
    }

    private static void copyCurrent(Fixture fixture, Vector3f output) {
        require(
                fixture.solver().copyCurrentDirection(
                        fixture.hairNode().drivenSlot(),
                        output
                ),
                "Constraint fixture direction was unavailable"
        );
    }
}
