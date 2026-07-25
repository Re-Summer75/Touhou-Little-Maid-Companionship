package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireDriven;

final class BundledGeckoModelVerification {
    private BundledGeckoModelVerification() {
    }

    static void run() throws Exception {
        require(
                Files.isDirectory(MODEL_DIRECTORY),
                "Bundled Gecko model reference directory is missing"
        );
        List<Path> modelPaths;
        try (var paths = Files.list(MODEL_DIRECTORY)) {
            modelPaths = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".json"))
                    .sorted()
                    .toList();
        }
        require(
                modelPaths.size() == 27,
                "Expected all 27 bundled Gecko models, found "
                        + modelPaths.size()
        );

        int visibleFringeBones = 0;
        int visibleFacialBones = 0;
        boolean verifiedSaint = false;
        boolean verifiedZhiban = false;
        for (Path modelPath : modelPaths) {
            String fileName = modelPath.getFileName().toString();
            String modelName = fileName.substring(0, fileName.length() - 5);
            AnimatedGeoModel model = new AnimatedGeoModel(
                    loadGeoModel(modelPath)
            );
            PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                    "geckolib:" + modelName,
                    model,
                    PhysicsMetadata.EMPTY
            );
            PhysicsBoneGeometry.Analysis geometry =
                    PhysicsBoneGeometry.analyze(model);
            for (PhysicsBoneGeometry.Node node : geometry.nodes()) {
                if (plan.isDriven(node.bone())) {
                    verifyKinematics(plan, node, fileName);
                }
                if (node.hasGeometry() && isInFacialFeatureSubtree(node)) {
                    visibleFacialBones++;
                    require(
                            !plan.isDriven(node.bone()),
                            fileName + " incorrectly drove facial bone "
                                    + node.path()
                    );
                    continue;
                }
                if (!node.hasGeometry()
                        || Boolean.TRUE.equals(
                        node.bone().geoBone().dontRender()
                )
                        || !PhysicsBoneClassifier.isFringeHint(
                        node.bone().getName()
                )) {
                    continue;
                }
                visibleFringeBones++;
                requireDriven(
                        plan,
                        node.bone(),
                        PhysicsBoneSelectionPlan.PartType.HAIR,
                        fileName + " lost visible fringe bone "
                                + node.bone().getName()
                );
            }

            if ("winefox_saint.json".equals(fileName)) {
                verifiedSaint = true;
                requireHair(plan, model, "bone5",
                        "Saint Winefox anonymous front hair was not discovered");
                requireHair(plan, model, "Bangs",
                        "Saint Winefox Bangs were not discovered");
                requireHair(plan, model, "RightSideHair",
                        "Saint Winefox right front-side hair was not discovered");
                requireHair(plan, model, "LeftSideHair",
                        "Saint Winefox left front-side hair was not discovered");
            }
            if ("zhiban.json".equals(fileName)) {
                verifiedZhiban = true;
                verifyZhiban(plan, model);
            }
            if ("rice_cake_fox.json".equals(fileName)) {
                verifyRiceCakeHeadShell(plan, model);
            }
        }
        require(verifiedSaint, "Saint Winefox fixture was not verified");
        require(verifiedZhiban, "Zhiban fixture was not verified");
        require(
                visibleFringeBones > 0,
                "Bundled Gecko models exposed no visible fringe fixtures"
        );
        require(
                visibleFacialBones > 0,
                "Bundled Gecko models exposed no facial exclusion fixtures"
        );
    }

    private static void verifyZhiban(
            PhysicsBoneSelectionPlan plan,
            AnimatedGeoModel model
    ) {
        require(
                !plan.isDriven(model.bones().get("bone53")),
                "Zhiban anonymous blush overlay was physicalized"
        );
        requireRigidAttachment(plan, model, "bone29");
        requireRigidAttachment(plan, model, "bone30");
        requireRigidAttachment(plan, model, "bone27");
        requireRigidAttachment(plan, model, "bone31");
        requireHair(plan, model, "bone3",
                "Zhiban left long ponytail was not discovered");
        requireHair(plan, model, "bone9",
                "Zhiban right long ponytail was not discovered");
        require(
                plan.decision(model.bones().get("bone3")).structureRole()
                        == PhysicsBoneSelectionPlan.StructureRole
                        .COMPOUND_SINGLE_BONE
                        && plan.decision(model.bones().get("bone9"))
                        .structureRole()
                        == PhysicsBoneSelectionPlan.StructureRole
                        .COMPOUND_SINGLE_BONE,
                "Zhiban long ponytails were not marked as single-bone compounds"
        );
        requireHair(plan, model, "bone34",
                "Zhiban anonymous ahoge was not discovered");
        var ahoge = plan.kinematics(model.bones().get("bone34"));
        require(
                ahoge != null
                        && ahoge.axis().y > 0.25F
                        && ahoge.contactConfidence() >= 0.20F,
                "Zhiban ahoge attachment frame was reversed: "
                        + (ahoge == null ? "missing"
                        : ahoge.effectivePivot() + " / " + ahoge.axis()
                        + " / contact=" + ahoge.contactConfidence()
                        + " / support=" + ahoge.supportConfidence())
        );
    }

    private static void verifyRiceCakeHeadShell(
            PhysicsBoneSelectionPlan plan,
            AnimatedGeoModel model
    ) {
        AnimatedGeoBone shell = model.bones().get("HairFemaleK_Matching");
        requireDriven(
                plan,
                shell,
                PhysicsBoneSelectionPlan.PartType.HEAD_SHELL,
                "Rice Cake Fox enclosing single-bone hair shell was treated "
                        + "as a freely hanging strand: " + plan.decision(shell)
        );
        PhysicsBoneSelectionPlan.Decision decision = plan.decision(shell);
        require(
                decision.profile().gravityScale() == 0.0F
                        && decision.profile().angleScale() <= 0.30F
                        && decision.constraints().rotationInertiaScale()
                        <= 0.05F,
                "Rice Cake Fox head shell retained strand-scale inertia"
        );
        requireHair(plan, model, "HairFront",
                "Rice Cake Fox separate front fringe lost flexible physics");
        requireHair(plan, model, "LeftPony",
                "Rice Cake Fox left ponytail lost flexible physics");
        requireHair(plan, model, "RightPony",
                "Rice Cake Fox right ponytail lost flexible physics");
    }

    private static void requireRigidAttachment(
            PhysicsBoneSelectionPlan plan,
            AnimatedGeoModel model,
            String boneName
    ) {
        AnimatedGeoBone bone = model.bones().get(boneName);
        require(
                bone != null
                        && !plan.isDriven(bone)
                        && plan.decision(bone).structureRole()
                        == PhysicsBoneSelectionPlan.StructureRole
                        .RIGID_ATTACHMENT_BASE,
                "Zhiban compact attachment remained physical: "
                        + boneName + " " + plan.decision(bone)
        );
    }

    private static void verifyKinematics(
            PhysicsBoneSelectionPlan plan,
            PhysicsBoneGeometry.Node node,
            String fileName
    ) {
        var metrics = plan.kinematics(node.bone());
        require(metrics != null, fileName + " lost kinematics for " + node.path());
        var axis = metrics.axis();
        var pivot = metrics.effectivePivot();
        require(
                Float.isFinite(axis.x) && Float.isFinite(axis.y)
                        && Float.isFinite(axis.z)
                        && Math.abs(axis.length() - 1.0F) < 1.0E-4F
                        && Float.isFinite(pivot.x) && Float.isFinite(pivot.y)
                        && Float.isFinite(pivot.z)
                        && Float.isFinite(metrics.leverArm())
                        && metrics.leverArm() > 0.0F,
                fileName + " produced invalid attachment frame "
                        + node.path()
        );
    }

    private static boolean isInFacialFeatureSubtree(
            PhysicsBoneGeometry.Node node
    ) {
        PhysicsBoneGeometry.Node cursor = node;
        while (cursor != null) {
            if (PhysicsBoneClassifier.isFacialFeature(
                    cursor.bone().getName()
            )) {
                return true;
            }
            cursor = cursor.parent();
        }
        return false;
    }

    private static void requireHair(
            PhysicsBoneSelectionPlan plan,
            AnimatedGeoModel model,
            String boneName,
            String message
    ) {
        requireDriven(
                plan,
                model.bones().get(boneName),
                PhysicsBoneSelectionPlan.PartType.HAIR,
                message
        );
    }
}
