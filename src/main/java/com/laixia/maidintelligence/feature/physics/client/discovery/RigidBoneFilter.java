package com.laixia.maidintelligence.feature.physics.client.discovery;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneClassifier;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;

import java.util.List;
import java.util.Set;

final class RigidBoneFilter {
    private static final Set<String> RIGID_TOKENS = Set.of(
            "body", "upperbody", "upbody", "lowerbody", "torso", "chest",
            "head", "face", "neck",
            "arm", "leftarm", "rightarm", "forearm", "hand",
            "leg", "leftleg", "rightleg", "thigh", "knee", "foot",
            "eye", "eyes", "eyelid", "eyebrow", "mouth", "lip", "teeth",
            "expression", "emoji", "weapon", "sword", "gun", "rifle", "pistol",
            "backpack", "locator"
    );
    private static final Set<String> FIXED_WEARABLE_TOKENS = Set.of(
            "helmet", "hat", "crown", "mask", "glasses", "goggle"
    );

    private RigidBoneFilter() {
    }

    static boolean isCoreBone(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            AnimatedGeoModel model
    ) {
        AnimatedGeoBone bone = node.bone();
        if (bone == model.head() || bone == model.leftArm() || bone == model.rightArm()) {
            return true;
        }
        if (isInOrUnderLocator(node, geometry, model.leftHandBones())
                || isInOrUnderLocator(node, geometry, model.rightHandBones())
                || isInOrUnderLocator(node, geometry, model.leftWaistBones())
                || isInOrUnderLocator(node, geometry, model.rightWaistBones())
                || isInOrUnderLocator(node, geometry, model.backpackBones())
                || isInOrUnderLocator(node, geometry, model.tacPistolBones())
                || isInOrUnderLocator(node, geometry, model.tacRifleBones())) {
            return true;
        }
        if (PhysicsBoneClassifier.isBreaker(bone.getName())) {
            return true;
        }
        List<String> tokens = DiscoveryMath.tokens(bone.getName());
        if (tokens.stream().anyMatch(RIGID_TOKENS::contains)) {
            return true;
        }
        if (tokens.stream().anyMatch(FIXED_WEARABLE_TOKENS::contains)
                && PhysicsBoneClassifier.classify(bone.getName()).type()
                == PhysicsBoneClassifier.ChainType.NONE) {
            return true;
        }
        double modelVolume = Math.max(
                geometry.modelBounds().volume(),
                DiscoveryMath.EPSILON
        );
        return node.bounds().volume() > modelVolume * 0.16D
                && node.thinRatio() > 0.32D
                && PhysicsBoneClassifier.classify(bone.getName()).type()
                == PhysicsBoneClassifier.ChainType.NONE;
    }

    private static boolean isInOrUnderLocator(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            List<AnimatedGeoBone> hierarchy
    ) {
        if (hierarchy.contains(node.bone())) {
            return true;
        }
        if (hierarchy.isEmpty()) {
            return false;
        }
        PhysicsBoneGeometry.Node locator =
                geometry.node(hierarchy.get(hierarchy.size() - 1));
        return locator != null && node.isDescendantOf(locator);
    }
}
