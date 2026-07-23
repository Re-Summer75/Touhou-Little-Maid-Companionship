package com.laixia.maidintelligence.feature.physics.client.discovery;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneClassifier;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;
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
    private static final Set<String> FIXED_ATTACHMENT_TOKENS = Set.of(
            "flower", "clip", "hairclip", "hairpin", "pin",
            "ornament", "decoration", "accessory", "ball", "balls"
    );
    private static final List<String> CJK_RIGID_MARKERS = List.of(
            "身体", "身體", "躯干", "軀幹", "头", "頭", "脸", "臉",
            "脖", "颈", "頸", "手", "臂", "胳膊", "腿", "脚", "腳",
            "眼", "眉", "嘴", "鼻", "牙", "武器", "枪", "槍",
            "剑", "劍", "背包"
    );
    private static final List<String> FIXED_ATTACHMENT_MARKERS = List.of(
            "hairclip", "hairpin", "ornament", "decoration",
            "发夹", "髮夾", "发饰", "髮飾", "髪飾り", "ヘアピン",
            "머리핀", "머리장식"
    );

    private RigidBoneFilter() {
    }

    static boolean isCoreBone(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            AnimatedGeoModel model,
            PhysicsBoneClassifier.Classification semantic
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
        if (PhysicsBoneClassifier.isFacialFeature(bone.getName())) {
            return true;
        }
        if (!semantic.isPhysical()
                && isAnonymousFacialOverlay(node, geometry)) {
            return true;
        }
        if (PhysicsBoneClassifier.isBreaker(bone.getName())
                && !semantic.isPhysical()) {
            return true;
        }
        if (!semantic.isPhysical() && hasBreakerAncestor(node)) {
            return true;
        }
        List<String> tokens = DiscoveryMath.tokens(bone.getName());
        boolean cjkRigid = CJK_RIGID_MARKERS.stream()
                .anyMatch(bone.getName()::contains);
        if ((tokens.stream().anyMatch(RIGID_TOKENS::contains)
                || cjkRigid)
                && !semantic.isPhysical()) {
            return true;
        }
        if (tokens.stream().anyMatch(FIXED_WEARABLE_TOKENS::contains)
                && !semantic.isPhysical()) {
            return true;
        }
        if (isRigidHeadAttachment(node, geometry, tokens)) {
            return true;
        }
        double modelVolume = Math.max(
                geometry.modelBounds().volume(),
                DiscoveryMath.EPSILON
        );
        return node.bounds().volume() > modelVolume * 0.16D
                && node.thinRatio() > 0.32D
                && !semantic.isPhysical()
                && !(geometry.isInHeadSubtree(node)
                && !bone.children().isEmpty());
    }

    private static boolean isRigidHeadAttachment(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            List<String> tokens
    ) {
        String name = node.bone().getName();
        String lowerName = name.toLowerCase(Locale.ROOT);
        boolean namedAttachment = tokens.stream()
                .anyMatch(FIXED_ATTACHMENT_TOKENS::contains)
                || FIXED_ATTACHMENT_MARKERS.stream().anyMatch(
                marker -> lowerName.contains(
                        marker.toLowerCase(Locale.ROOT)
                )
        );
        if (!namedAttachment
                || !node.hasGeometry()
                || !geometry.isInHeadSubtree(node)
                || geometry.headBounds().isEmpty()) {
            return false;
        }
        Vector3f size = node.size();
        Vector3f headSize = geometry.headBounds().size();
        double headWidth = Math.max(
                Math.max(headSize.x, headSize.z),
                DiscoveryMath.EPSILON
        );
        double maximum = Math.max(size.x, Math.max(size.y, size.z));
        boolean compact = maximum <= headWidth * 0.60D
                && node.bounds().volume()
                <= geometry.headBounds().volume() * 0.20D;
        boolean pairedBall = tokens.contains("ball")
                || tokens.contains("balls");
        if (node.bone().children().isEmpty()) {
            return compact || pairedBall;
        }
        if (!compact) {
            return false;
        }
        for (AnimatedGeoBone childBone : node.bone().children()) {
            PhysicsBoneGeometry.Node child = geometry.node(childBone);
            if (child != null && (PhysicsBoneClassifier
                    .classifyVisibleGeometry(childBone.getName())
                    .isPhysical() || child.maxChainDepth() > 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAnonymousFacialOverlay(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry
    ) {
        PhysicsBoneGeometry.Bounds headBounds = geometry.headBounds();
        if (!geometry.isInHeadSubtree(node)
                || !node.bone().children().isEmpty()
                || headBounds.isEmpty()) {
            return false;
        }
        Vector3f headCenter = headBounds.center();
        Vector3f center = node.center();
        Vector3f size = node.size();
        Vector3f headSize = headBounds.size();
        double headWidth = Math.max(
                Math.max(headSize.x, headSize.z),
                DiscoveryMath.EPSILON
        );
        double headHeight = Math.max(
                headSize.y,
                DiscoveryMath.EPSILON
        );
        double forward = (headCenter.z - center.z) / headWidth;
        double below = (headCenter.y - center.y) / headHeight;
        double lateral = Math.abs(center.x - headCenter.x) / headWidth;
        if (forward < 0.30D || forward > 0.75D
                || below < 0.20D || below > 0.70D
                || size.y > headHeight * 0.25D
                || size.z > headWidth * 0.18D) {
            return false;
        }
        boolean wideSymmetricBand = lateral <= 0.20D
                && size.x >= headWidth * 0.42D;
        boolean mirroredCheekPatch = lateral >= 0.18D
                && lateral <= 0.58D
                && size.x <= headWidth * 0.30D
                && hasMirroredFacialSibling(
                node,
                geometry,
                headCenter,
                headWidth,
                headHeight
        );
        return wideSymmetricBand || mirroredCheekPatch;
    }

    private static boolean hasMirroredFacialSibling(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            Vector3f headCenter,
            double headWidth,
            double headHeight
    ) {
        if (node.parent() == null) {
            return false;
        }
        Vector3f center = node.center();
        Vector3f size = node.size();
        double offsetX = center.x - headCenter.x;
        for (AnimatedGeoBone siblingBone : node.parent().bone().children()) {
            PhysicsBoneGeometry.Node sibling = geometry.node(siblingBone);
            if (siblingBone == node.bone()
                    || sibling == null
                    || !sibling.hasGeometry()) {
                continue;
            }
            Vector3f otherCenter = sibling.center();
            Vector3f otherSize = sibling.size();
            double otherOffsetX = otherCenter.x - headCenter.x;
            if (offsetX * otherOffsetX < 0.0D
                    && Math.abs(Math.abs(offsetX) - Math.abs(otherOffsetX))
                    <= headWidth * 0.12D
                    && Math.abs(center.y - otherCenter.y)
                    <= headHeight * 0.12D
                    && Math.abs(center.z - otherCenter.z)
                    <= headWidth * 0.12D
                    && DiscoveryMath.ratio(size.x, otherSize.x) <= 2.0D
                    && DiscoveryMath.ratio(size.y, otherSize.y) <= 2.0D
                    && DiscoveryMath.ratio(size.z, otherSize.z) <= 2.0D) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBreakerAncestor(
            PhysicsBoneGeometry.Node node
    ) {
        PhysicsBoneGeometry.Node ancestor = node.parent();
        while (ancestor != null) {
            String name = ancestor.bone().getName();
            if (PhysicsBoneClassifier.isFacialFeature(name)) {
                return true;
            }
            if (PhysicsBoneClassifier.classifyVisibleGeometry(name)
                    .isPhysical()) {
                return false;
            }
            if (PhysicsBoneClassifier.isBreaker(name)) {
                return true;
            }
            ancestor = ancestor.parent();
        }
        return false;
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
