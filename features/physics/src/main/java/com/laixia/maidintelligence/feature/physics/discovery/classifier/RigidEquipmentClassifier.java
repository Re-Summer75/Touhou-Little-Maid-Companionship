package com.laixia.maidintelligence.feature.physics.discovery.classifier;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Identifies rigid equipment boundaries shared by discovery and collision.
 */
public final class RigidEquipmentClassifier {
    private static final Set<String> HEADWEAR_TOKENS = Set.of(
            "helmet", "hat", "crown", "glasses", "goggle", "goggles",
            "faceplate", "shmask", "earphone", "earphones", "headphone",
            "headphones", "headset"
    );
    private static final Set<String> WEAPON_TOKENS = Set.of(
            "weapon", "sword", "gun", "rifle", "pistol", "spear",
            "blade", "katana", "shield", "scythe", "axe", "hammer",
            "knife", "staff", "wand", "arrow"
    );
    private static final Set<String> WEARABLE_TOKENS = Set.of(
            "armor", "armour", "vest", "chestplate", "chestguard",
            "belt", "strap", "holster", "pouch", "backpack"
    );
    private static final List<String> HEADWEAR_MARKERS = List.of(
            "头盔", "頭盔", "帽", "王冠", "ヘルメット", "帽子",
            "耳机", "耳機", "투구", "헬멧", "모자"
    );
    private static final List<String> WEAPON_MARKERS = List.of(
            "武器", "枪", "槍", "剑", "劍", "刀", "矛", "杖", "枪械",
            "法阵", "法陣", "銃", "剣", "槍", "魔法陣",
            "무기", "총", "검", "창", "지팡이", "마법진"
    );
    private static final List<String> WEARABLE_MARKERS = List.of(
            "胸挂", "胸掛", "腰带", "腰帶", "腿带", "腿帶",
            "枪套", "槍套", "背包"
    );
    private static final List<String> ROMANIZED_MAGIC_MARKERS = List.of(
            "mofazhang", "fazhang", "mofazhen", "fazhen"
    );

    private RigidEquipmentClassifier() {
    }

    /**
     * Treats a named equipment root and its authored descendants as one object.
     * A semantic hair scope below headwear remains independently discoverable.
     */
    public static Result classify(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            BoneModelSnapshot model
    ) {
        Result locator = classifyLocatorScope(node, geometry, model);
        if (locator.kind() != Kind.NONE) {
            return locator;
        }
        Result handAttachment = classifyHandAttachment(node, geometry, model);
        if (handAttachment.kind() != Kind.NONE) {
            return handAttachment;
        }
        PhysicsBoneGeometry.Node cursor = node;
        while (cursor != null) {
            Kind kind = equipmentKind(cursor.bone().getName());
            if (kind != Kind.NONE) {
                if (kind == Kind.HEADWEAR
                        && cursor != node
                        && hasHairScopeBefore(node, cursor)) {
                    return Result.NONE;
                }
                return new Result(kind, cursor.bone().getName());
            }
            cursor = cursor.parent();
        }
        return Result.NONE;
    }

    private static Result classifyLocatorScope(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            BoneModelSnapshot model
    ) {
        List<List<BoneModelSnapshot.Bone>> hierarchies = List.of(
                model.leftHandBones(),
                model.rightHandBones(),
                model.leftWaistBones(),
                model.rightWaistBones(),
                model.backpackBones(),
                model.tacPistolBones(),
                model.tacRifleBones()
        );
        for (List<BoneModelSnapshot.Bone> hierarchy : hierarchies) {
            if (hierarchy.isEmpty()) {
                continue;
            }
            PhysicsBoneGeometry.Node locator = geometry.node(
                    hierarchy.get(hierarchy.size() - 1)
            );
            if (locator != null && node.isDescendantOf(locator)) {
                return new Result(Kind.LOCATOR, locator.bone().getName());
            }
        }
        return Result.NONE;
    }

    private static Result classifyHandAttachment(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            BoneModelSnapshot model
    ) {
        PhysicsBoneGeometry.Node root =
                handAttachmentRoot(node, geometry, model);
        return root == node
                ? new Result(Kind.HAND_ATTACHMENT, root.bone().getName())
                : Result.NONE;
    }

    /**
     * A hand locator describes the canonical transform path. A sibling branch
     * directly under the hand is an authored held object, even without a name.
     */
    public static boolean isHandAttachmentScope(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            BoneModelSnapshot model
    ) {
        return handAttachmentRoot(node, geometry, model) != null;
    }

    private static PhysicsBoneGeometry.Node handAttachmentRoot(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            BoneModelSnapshot model
    ) {
        PhysicsBoneGeometry.Node left = handAttachmentRoot(
                node, geometry, model.leftHandBones(), model.leftArm()
        );
        return left != null ? left : handAttachmentRoot(
                node, geometry, model.rightHandBones(), model.rightArm()
        );
    }

    private static PhysicsBoneGeometry.Node handAttachmentRoot(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            List<BoneModelSnapshot.Bone> locatorHierarchy,
            BoneModelSnapshot.Bone arm
    ) {
        if (arm == null) {
            return null;
        }
        int armIndex = locatorHierarchy.indexOf(arm);
        int handIndex = locatorHierarchy.size() - 2;
        if (armIndex < 0 || armIndex >= handIndex) {
            return null;
        }
        PhysicsBoneGeometry.Node hand = geometry.node(
                locatorHierarchy.get(handIndex)
        );
        if (hand == null) {
            return null;
        }
        PhysicsBoneGeometry.Node branch = node;
        while (branch.parent() != null && branch.parent() != hand) {
            branch = branch.parent();
        }
        if (branch.parent() != hand
                || locatorHierarchy.contains(branch.bone())) {
            return null;
        }
        return branch;
    }

    private static boolean hasHairScopeBefore(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Node equipmentRoot
    ) {
        PhysicsBoneGeometry.Node cursor = node;
        while (cursor != null && cursor != equipmentRoot) {
            String name = cursor.bone().getName();
            PhysicsBoneClassifier.Classification classification =
                    PhysicsBoneClassifier.classifyVisibleGeometry(name);
            if (PhysicsBoneClassifier.isFringeHint(name)
                    || classification.type()
                    == PhysicsBoneClassifier.ChainType.HAIR) {
                return true;
            }
            if (cursor != node && cursor.hasGeometry()) {
                return false;
            }
            cursor = cursor.parent();
        }
        return false;
    }

    private static Kind equipmentKind(String name) {
        List<String> tokens = tokens(name);
        if (tokens.contains("locator")) {
            return Kind.LOCATOR;
        }
        if (tokens.stream().anyMatch(HEADWEAR_TOKENS::contains)) {
            return Kind.HEADWEAR;
        }
        if (tokens.stream().anyMatch(WEAPON_TOKENS::contains)) {
            return Kind.WEAPON;
        }
        if (tokens.stream().anyMatch(WEARABLE_TOKENS::contains)) {
            return Kind.RIGID_WEARABLE;
        }
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (HEADWEAR_MARKERS.stream().anyMatch(lower::contains)) {
            return Kind.HEADWEAR;
        }
        if (WEAPON_MARKERS.stream().anyMatch(lower::contains)) {
            return Kind.WEAPON;
        }
        if (WEARABLE_MARKERS.stream().anyMatch(lower::contains)) {
            return Kind.RIGID_WEARABLE;
        }
        String compact = lower.replaceAll("[^a-z0-9]+", "");
        if (isCompoundHeadwear(compact)) {
            return Kind.HEADWEAR;
        }
        if (isCompoundWearable(compact)) {
            return Kind.RIGID_WEARABLE;
        }
        return isCompoundWeapon(compact) ? Kind.WEAPON : Kind.NONE;
    }

    private static List<String> tokens(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String separated = name
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_");
        return Arrays.stream(separated.split("_+"))
                .filter(token -> !token.isBlank())
                .map(token -> token.replaceFirst("\\d+$", ""))
                .toList();
    }

    private static boolean isCompoundHeadwear(String compact) {
        return compact.startsWith("hat")
                || compact.startsWith("witchcap")
                || compact.startsWith("helmet")
                || compact.startsWith("crown")
                || compact.startsWith("faceplate")
                || compact.startsWith("shmask")
                || compact.startsWith("earphone")
                || compact.startsWith("headphone")
                || compact.startsWith("headset")
                || compact.startsWith("erji");
    }

    private static boolean isCompoundWearable(String compact) {
        return compact.startsWith("xiongmgua")
                || compact.startsWith("xionggua")
                || compact.startsWith("yaodai")
                || compact.startsWith("tuidai")
                || compact.startsWith("beibao");
    }

    private static boolean isCompoundWeapon(String compact) {
        return ROMANIZED_MAGIC_MARKERS.stream().anyMatch(compact::contains)
                || compact.startsWith("weapon")
                || compact.startsWith("sword")
                || compact.startsWith("gun")
                || compact.startsWith("rifle")
                || compact.startsWith("pistol")
                || compact.startsWith("spear")
                || compact.startsWith("blade")
                || compact.startsWith("katana")
                || compact.startsWith("shield")
                || compact.startsWith("scythe")
                || compact.startsWith("wand")
                || compact.startsWith("magicwand")
                || compact.startsWith("magicstaff")
                || compact.startsWith("magicstick");
    }

    public enum Kind {
        NONE,
        HEADWEAR,
        WEAPON,
        RIGID_WEARABLE,
        HAND_ATTACHMENT,
        LOCATOR
    }

    public record Result(Kind kind, String rootName) {
        private static final Result NONE = new Result(Kind.NONE, "");

        public String reason() {
            return switch (kind) {
                case HEADWEAR ->
                        "fixed headwear subtree rooted at " + rootName;
                case WEAPON ->
                        "rigid weapon or effect subtree rooted at " + rootName;
                case RIGID_WEARABLE ->
                        "rigid wearable subtree rooted at " + rootName;
                case HAND_ATTACHMENT ->
                        "rigid branch attached to hand at " + rootName;
                case LOCATOR ->
                        "equipment locator subtree rooted at " + rootName;
                case NONE -> "";
            };
        }
    }
}
