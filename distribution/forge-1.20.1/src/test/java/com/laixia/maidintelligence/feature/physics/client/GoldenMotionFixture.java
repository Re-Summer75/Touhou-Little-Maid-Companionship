package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoBone;
import org.joml.Vector3f;

import java.util.Map;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.bonesByGeoBone;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.forEachBone;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireNear;

final class GoldenMotionFixture {
    private GoldenMotionFixture() {
    }

    static void applyAnimationPose(AnimatedGeoModel model, int frame) {
        int[] ordinal = {0};
        forEachBone(model, bone -> {
            int index = ordinal[0]++;
            Vector3f base = bone.geoBone().rotation();
            float wave = (float) Math.sin(
                    frame * 0.071D + index * 0.193D
            );
            float counterWave = (float) Math.cos(
                    frame * 0.047D - index * 0.137D
            );
            bone.setRotationX(base.x + wave * 0.018F);
            bone.setRotationY(base.y + counterWave * 0.014F);
            bone.setRotationZ(base.z + (wave - counterWave) * 0.011F);
            bone.setPositionX(wave * 0.013F);
            bone.setPositionY(counterWave * 0.009F);
            bone.setPositionZ((wave + counterWave) * 0.007F);
        });
    }

    static void motion(int frame, Vector3f output) {
        if (frame < 24) {
            output.zero();
        } else if (frame < 72) {
            output.set(0.12F, 0.0F, -0.04F);
        } else if (frame < 120) {
            output.set(-0.08F, 0.05F, 0.10F);
        } else if (frame < 180) {
            output.set(
                    (float) Math.sin(frame * 0.12D) * 0.20F,
                    0.0F,
                    (float) Math.cos(frame * 0.08D) * 0.16F
            );
        } else {
            output.zero();
        }
    }

    static float yawRate(int frame) {
        if (frame < 45) {
            return 0.0F;
        }
        if (frame < 135) {
            return (float) Math.toRadians(18.0D);
        }
        if (frame < 195) {
            return (float) Math.toRadians(-27.0D);
        }
        return 0.0F;
    }

    static float deltaSeconds(int frame) {
        return switch (frame % 5) {
            case 0 -> 1.0F / 30.0F;
            case 1 -> 1.0F / 120.0F;
            default -> 1.0F / 60.0F;
        };
    }

    static void compareModelPose(
            String label,
            int frame,
            AnimatedGeoModel expectedModel,
            AnimatedGeoModel actualModel,
            float tolerance
    ) {
        Map<GeoBone, AnimatedGeoBone> actualBones =
                bonesByGeoBone(actualModel);
        forEachBone(expectedModel, expected -> {
            AnimatedGeoBone actual = actualBones.get(expected.geoBone());
            require(actual != null, label + " optimized model lost a bone");
            String prefix = label + " frame " + frame + " bone "
                    + expected.getName();
            requireNear(
                    actual.getRotationX(),
                    expected.getRotationX(),
                    tolerance,
                    prefix + " rotationX"
            );
            requireNear(
                    actual.getRotationY(),
                    expected.getRotationY(),
                    tolerance,
                    prefix + " rotationY"
            );
            requireNear(
                    actual.getRotationZ(),
                    expected.getRotationZ(),
                    tolerance,
                    prefix + " rotationZ"
            );
            requireNear(
                    actual.getPositionX(),
                    expected.getPositionX(),
                    tolerance,
                    prefix + " positionX"
            );
            requireNear(
                    actual.getPositionY(),
                    expected.getPositionY(),
                    tolerance,
                    prefix + " positionY"
            );
            requireNear(
                    actual.getPositionZ(),
                    expected.getPositionZ(),
                    tolerance,
                    prefix + " positionZ"
            );
        });
    }
}
