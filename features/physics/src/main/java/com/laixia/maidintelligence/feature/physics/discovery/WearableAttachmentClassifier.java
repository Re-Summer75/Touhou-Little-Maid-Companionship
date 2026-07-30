package com.laixia.maidintelligence.feature.physics.discovery;


import com.laixia.maidintelligence.feature.physics.discovery.PhysicsBoneClassifier;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.discovery.structure.BoneStructureAnalysis;
import com.laixia.maidintelligence.feature.physics.discovery.structure.ClothAccessoryMetrics;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Separates rigid head wearables from side-hanging articulated ornaments.
 */
final class WearableAttachmentClassifier {
    private static final Set<String> MASK_TOKENS = Set.of(
            "mask", "masks", "facemask"
    );
    private static final Set<String> ALWAYS_RIGID_TOKENS = Set.of(
            "helmet", "hat", "crown", "glasses", "goggle", "goggles",
            "faceplate", "shmask"
    );
    private static final Set<String> RIGID_ORNAMENT_TOKENS = Set.of(
            "flower", "clip", "hairclip", "hairpin", "pin",
            "ornament", "decoration", "accessory", "ball", "balls"
    );
    private static final List<String> MASK_MARKERS = List.of(
            "mask", "面具", "面罩", "仮面", "가면"
    );
    private static final List<String> RIGID_MARKERS = List.of(
            "shmask", "faceplate", "helmet", "头盔", "頭盔",
            "ヘルメット", "헬멧"
    );

    private WearableAttachmentClassifier() {
    }

    static Result classify(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            BoneStructureAnalysis structures
    ) {
        if (!node.hasGeometry() || !geometry.isInHeadSubtree(node)) {
            return Result.NONE;
        }
        ClothAccessoryMetrics metrics =
                structures.clothAccessory(node.bone());
        List<String> tokens = DiscoveryMath.tokens(node.bone().getName());
        String lower = node.bone().getName().toLowerCase(Locale.ROOT);
        boolean mask = tokens.stream().anyMatch(MASK_TOKENS::contains)
                || MASK_MARKERS.stream().anyMatch(lower::contains);
        boolean alwaysRigid =
                tokens.stream().anyMatch(ALWAYS_RIGID_TOKENS::contains)
                        || RIGID_MARKERS.stream().anyMatch(lower::contains);
        if (alwaysRigid) {
            return Result.rigid("fixed faceplate, helmet, or head wearable");
        }
        if (tokens.stream().anyMatch(RIGID_ORNAMENT_TOKENS::contains)
                || HeadAttachmentClassifier.isNamed(
                        node.bone().getName()
                )) {
            return Result.rigid("named rigid head ornament");
        }
        if (mask) {
            if (metrics.hangingHeadAccessory()
                    && !metrics.headEnclosingWearable()
                    && !metrics.facialDescendants()) {
                return Result.dangling("side-hanging mask geometry");
            }
            return Result.rigid(
                    metrics.facialDescendants()
                            ? "mask owns facial expression subtree"
                            : "face-covering mask geometry"
            );
        }
        if (metrics.dominantAttachmentBody()) {
            return Result.rigid(
                    "dominant rigid ornament body with hanging descendant"
            );
        }
        if (metrics.hangingHeadAccessory()
                && (hasDominantParent(node, structures)
                || PhysicsBoneClassifier
                .classifyVisibleGeometry(node.bone().getName())
                .type() == PhysicsBoneClassifier.ChainType.RIBBON)) {
            return Result.dangling("small hanging ornament descendant");
        }
        return Result.NONE;
    }

    private static boolean hasDominantParent(
            PhysicsBoneGeometry.Node node,
            BoneStructureAnalysis structures
    ) {
        return node.parent() != null
                && structures.clothAccessory(node.parent().bone())
                .dominantAttachmentBody();
    }

    enum Kind {
        NONE,
        RIGID,
        DANGLING
    }

    record Result(Kind kind, String reason) {
        private static final Result NONE = new Result(Kind.NONE, "");

        private static Result rigid(String reason) {
            return new Result(Kind.RIGID, reason);
        }

        private static Result dangling(String reason) {
            return new Result(Kind.DANGLING, reason);
        }
    }
}
