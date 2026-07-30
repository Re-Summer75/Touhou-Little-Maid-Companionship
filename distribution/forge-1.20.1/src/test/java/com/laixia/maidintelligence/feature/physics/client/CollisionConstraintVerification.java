package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.laixia.maidintelligence.feature.physics.client.SecondaryMotionFixture.Fixture;
import com.laixia.maidintelligence.feature.physics.layout.SecondaryMotionConstraint;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireNear;

final class CollisionConstraintVerification {
    private CollisionConstraintVerification() {
    }

    static void run() {
        CollisionProxyFrameworkVerification.run();
        verifiesAutomaticHeadCollisionDisabled();
        verifiesConstraintInvariantsAcrossFrameRates();
        CollisionProxyGeometryVerification.run();
    }

    private static void verifiesAutomaticHeadCollisionDisabled() {
        for (boolean[] flags : new boolean[][]{
                {true, false},
                {false, true},
                {true, true}
        }) {
            Fixture fixture = SecondaryMotionFixture.create(
                    60.0F,
                    0.0F,
                    flags[0],
                    flags[1]
            );
            require(
                    fixture.hairNode().constraint()
                            .collisionProxies().proxyCount() == 0,
                    "Legacy Head collision flags generated a proxy"
            );
        }
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
            requireSwingConstraint(
                    fixture,
                    direction,
                    finalHeadRotation,
                    fps
            );
        }
    }

    private static void requireSwingConstraint(
            Fixture fixture,
            Vector3f direction,
            float headRotation,
            int fps
    ) {
        SecondaryMotionConstraint constraint =
                fixture.hairNode().constraint();
        require(
                fixture.solver().lastCollisionProjectionCount() == 0,
                "Disabled Head collision projected at " + fps + " FPS"
        );
        Quaternionf reference = new Quaternionf().rotateZ(headRotation);
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
