package com.laixia.maidintelligence.feature.physics.client.motion;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.TailAnimationContinuityTestFacade.require;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.NO_ENTITY_ACCELERATION;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.angularDistance;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.createFixture;
import static com.laixia.maidintelligence.feature.physics.client.motion.TailMotionVerificationSupport.requireNear;

/**
 * Verifies animation-pose snapshot restoration and sub-degree continuity.
 */
public final class TailSnapshotContinuityScenario {
    private TailSnapshotContinuityScenario() {
    }

    public static void run() {
        verifiesSnapshotRestoresEveryLocalChannel();
        verifiesSmallPoseErrorsRemainContinuous();
        verifiesMicroscopicPoseErrorsRemainContinuous();
    }

    private static void verifiesSnapshotRestoresEveryLocalChannel() {
        TailMotionVerificationSupport.Fixture fixture = createFixture();
        BoneModelSnapshot.Bone tail = fixture.tail();
        tail.setRotationX(0.13F);
        tail.setRotationY(-0.17F);
        tail.setRotationZ(0.21F);
        tail.setPositionX(1.25F);
        tail.setPositionY(-2.50F);
        tail.setPositionZ(3.75F);
        fixture.solver().solve(new Vector3f(), 0.0F, 0.0F, true);

        tail.setRotationX(10.0F);
        tail.setRotationY(11.0F);
        tail.setRotationZ(12.0F);
        tail.setPositionX(13.0F);
        tail.setPositionY(14.0F);
        tail.setPositionZ(15.0F);
        fixture.solver().restoreAnimationPose();

        requireNear(tail.getRotationX(), 0.13F, "rotation X");
        requireNear(tail.getRotationY(), -0.17F, "rotation Y");
        requireNear(tail.getRotationZ(), 0.21F, "rotation Z");
        requireNear(tail.getPositionX(), 1.25F, "position X");
        requireNear(tail.getPositionY(), -2.50F, "position Y");
        requireNear(tail.getPositionZ(), 3.75F, "position Z");
    }

    private static void verifiesSmallPoseErrorsRemainContinuous() {
        TailMotionVerificationSupport.Fixture fixture = createFixture();
        BoneModelSnapshot.Bone tail = fixture.tail();
        Quaternionf animation = new Quaternionf().rotateZYX(
                tail.getRotationZ(),
                tail.getRotationY(),
                tail.getRotationX()
        );
        fixture.solver().solve(
                NO_ENTITY_ACCELERATION,
                0.0F,
                0.0F,
                false
        );
        fixture.solver().restoreAnimationPose();
        fixture.solver().solve(
                new Vector3f(0.001F, 0.0F, 0.0F),
                0.0F,
                1.0F / 120.0F,
                false
        );
        Quaternionf rendered = new Quaternionf().rotateZYX(
                tail.getRotationZ(),
                tail.getRotationY(),
                tail.getRotationX()
        );
        float physicalAngle = angularDistance(animation, rendered);
        require(
                physicalAngle > 1.0E-5F && physicalAngle < 2.0E-4F,
                "Sub-degree pose error was quantized away: "
                        + physicalAngle
        );
    }

    private static void verifiesMicroscopicPoseErrorsRemainContinuous() {
        TailMotionVerificationSupport.Fixture fixture = createFixture();
        BoneModelSnapshot.Bone tail = fixture.tail();
        Quaternionf animation = new Quaternionf().rotateZYX(
                tail.getRotationZ(),
                tail.getRotationY(),
                tail.getRotationX()
        );
        fixture.solver().solve(
                NO_ENTITY_ACCELERATION,
                0.0F,
                0.0F,
                false
        );
        fixture.solver().restoreAnimationPose();
        fixture.solver().solve(
                new Vector3f(0.0001F, 0.0F, 0.0F),
                0.0F,
                1.0F / 120.0F,
                false
        );
        Quaternionf rendered = new Quaternionf().rotateZYX(
                tail.getRotationZ(),
                tail.getRotationY(),
                tail.getRotationX()
        );
        float physicalAngle = angularDistance(animation, rendered);
        require(
                physicalAngle > 5.0E-7F && physicalAngle < 1.0E-5F,
                "Microscopic pose error was quantized away: "
                        + physicalAngle
        );
    }
}
