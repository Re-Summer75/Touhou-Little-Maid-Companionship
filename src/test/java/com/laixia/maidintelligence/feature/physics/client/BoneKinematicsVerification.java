package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneKinematics;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadWinefoxGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireVectorNear;

final class BoneKinematicsVerification {
    private BoneKinematicsVerification() {
    }

    static void run() throws Exception {
        AnimatedGeoModel model = new AnimatedGeoModel(loadWinefoxGeoModel());
        AnimatedGeoBone baseHair = model.bones().get("BaseHair");
        BoneKinematics.Metrics shell = BoneKinematics.measure(
                baseHair,
                model.bones().get("Hair"),
                PhysicsBoneSelectionPlan.PartType.HEAD_SHELL
        );
        require(
                shell.compensatesPivot(),
                "Edge-pivoted winefox head shell did not receive a virtual pivot"
        );
        require(
                PhysicsBoneSelectionPlan.SpringProfile.defaults(
                        PhysicsBoneSelectionPlan.PartType.HEAD_SHELL
                ).gravityScale() <= 0.03F,
                "Head shell still receives enough gravity to fold on head pitch"
        );

        BoneKinematics.Metrics detached = BoneKinematics.measure(
                model.bones().get("bone5"),
                baseHair,
                PhysicsBoneSelectionPlan.PartType.HAIR
        );
        require(
                detached.compensatesPivot(),
                "Remote winefox bone5 pivot was not corrected"
        );
        require(
                detached.effectivePivot().y
                        > detached.authoredPivot().y + 0.5F,
                "Remote winefox pivot was not moved toward its visible mesh"
        );
        require(
                detached.axis().y < -0.5F,
                "Remote hair pivot did not produce a downward hanging axis: "
                        + detached.axis()
        );

        Quaternionf delta = new Quaternionf().rotateZ(0.2F);
        Vector3f offset = detached.compensationOffset(delta);
        Vector3f aliasedOffset = new Vector3f();
        detached.compensationOffsetInto(
                delta,
                aliasedOffset,
                aliasedOffset
        );
        requireVectorNear(
                aliasedOffset,
                offset,
                1.0E-6F,
                "Pivot compensation out parameters were not alias-safe"
        );
        Vector3f point = new Vector3f(detached.effectivePivot())
                .add(0.1F, -0.2F, 0.05F);
        Vector3f actual = delta.transform(
                new Vector3f(point).sub(detached.authoredPivot())
        ).add(detached.authoredPivot()).add(offset);
        Vector3f expected = delta.transform(
                new Vector3f(point).sub(detached.effectivePivot())
        ).add(detached.effectivePivot());
        require(
                actual.distance(expected) < 1.0E-5F,
                "Virtual-pivot translation did not preserve the intended rotation"
        );

        Quaternionf animation = new Quaternionf().rotateX(0.45F);
        Quaternionf physical = new Quaternionf(animation).rotateZ(0.30F);
        Vector3f poseOffset = detached.poseCompensationOffsetInto(
                animation,
                physical,
                new Vector3f(),
                new Vector3f()
        );
        Vector3f poseActual = physical.transform(
                new Vector3f(point).sub(detached.authoredPivot())
        ).add(detached.authoredPivot()).add(poseOffset);
        Vector3f animatedEffective = animation.transform(
                detached.effectivePivot().sub(detached.authoredPivot())
        ).add(detached.authoredPivot());
        Vector3f poseExpected = physical.transform(
                new Vector3f(point).sub(detached.effectivePivot())
        ).add(animatedEffective);
        requireVectorNear(
                poseActual,
                poseExpected,
                1.0E-5F,
                "Animated virtual-pivot compensation mixed coordinate spaces"
        );

        float scaleX = 1.35F;
        float scaleY = 0.70F;
        float scaleZ = 1.15F;
        Vector3f scaledOffset = detached.poseCompensationOffsetInto(
                animation,
                physical,
                scaleX,
                scaleY,
                scaleZ,
                new Vector3f(),
                new Vector3f()
        );
        Vector3f scaledActual = new Vector3f(point)
                .sub(detached.authoredPivot())
                .mul(scaleX, scaleY, scaleZ);
        physical.transform(scaledActual)
                .add(detached.authoredPivot())
                .add(scaledOffset);
        Vector3f scaledEffective = detached.effectivePivot()
                .sub(detached.authoredPivot())
                .mul(scaleX, scaleY, scaleZ);
        animation.transform(scaledEffective)
                .add(detached.authoredPivot());
        Vector3f scaledExpected = new Vector3f(point)
                .sub(detached.effectivePivot())
                .mul(scaleX, scaleY, scaleZ);
        physical.transform(scaledExpected).add(scaledEffective);
        requireVectorNear(
                scaledActual,
                scaledExpected,
                1.0E-5F,
                "Virtual-pivot compensation ignored animated bone scale"
        );
    }
}
