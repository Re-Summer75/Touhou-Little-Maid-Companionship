package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;

import java.nio.file.Path;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireDriven;

final class RigidAttachmentVerification {
    private RigidAttachmentVerification() {
    }

    static void run() throws Exception {
        verifiesWinefoxFlowerMount();
        verifiesRiceCakeFoxAttachments();
        verifiesCoincidentFlexibleChild();
    }

    private static void verifiesWinefoxFlowerMount() throws Exception {
        Verification fixture = discover("winefox_elf.json");
        requireNotDriven(
                fixture,
                "flower",
                "Winefox elf flower mount was physicalized"
        );
        requireDriven(
                fixture.plan(),
                fixture.model().bones().get("bone5"),
                PhysicsBoneSelectionPlan.PartType.HAIR,
                "Winefox elf flexible hair was lost with the flower mount"
        );
    }

    private static void verifiesRiceCakeFoxAttachments() throws Exception {
        Verification fixture = discover("rice_cake_fox.json");
        requireNotDriven(
                fixture,
                "Balls",
                "Rice cake fox rigid hair balls were physicalized"
        );
        requireDriven(
                fixture.plan(),
                fixture.model().bones().get("LeftPony"),
                PhysicsBoneSelectionPlan.PartType.HAIR,
                "Rice cake fox left ponytail was lost"
        );
        requireDriven(
                fixture.plan(),
                fixture.model().bones().get("RightPony"),
                PhysicsBoneSelectionPlan.PartType.HAIR,
                "Rice cake fox right ponytail was lost"
        );
        requireCompoundPony(fixture, "LeftPony");
        requireCompoundPony(fixture, "RightPony");
    }

    private static void verifiesCoincidentFlexibleChild() {
        AnimatedGeoModel model = modelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.coincident_attachment",
                    "texture_width":32,"texture_height":32},
                  "bones":[
                    {"name":"Head","pivot":[0,8,0],
                     "cubes":[{"origin":[-4,4,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"HairClip","parent":"Head","pivot":[3,8,0],
                     "cubes":[{"origin":[2.5,7.5,-.5],"size":[1,1,1],"uv":[0,0]}]},
                    {"name":"bone17","parent":"HairClip","pivot":[3,8,0],
                     "cubes":[{"origin":[2.5,-2,-.5],"size":[1,10,1],"uv":[0,0]}]}
                  ]}]}
                """);
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:coincident_attachment",
                model,
                PhysicsMetadata.EMPTY
        );
        PhysicsBoneSelectionPlan.Decision mount =
                plan.decision(model.bones().get("HairClip"));
        PhysicsBoneSelectionPlan.Decision tail =
                plan.decision(model.bones().get("bone17"));
        require(
                !mount.driven()
                        && mount.structureRole()
                        == PhysicsBoneSelectionPlan.StructureRole
                        .RIGID_ATTACHMENT_BASE
                        && tail.driven()
                        && tail.structureRole()
                        == PhysicsBoneSelectionPlan.StructureRole
                        .COMPOUND_SINGLE_BONE,
                "A coincident rigid mount swallowed its long flexible child"
        );
    }

    private static Verification discover(String fileName) throws Exception {
        Path path = MODEL_DIRECTORY.resolve(fileName);
        AnimatedGeoModel model = new AnimatedGeoModel(loadGeoModel(path));
        String modelName = fileName.substring(0, fileName.length() - 5);
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "geckolib:" + modelName,
                model,
                PhysicsMetadata.EMPTY
        );
        return new Verification(model, plan);
    }

    private static void requireNotDriven(
            Verification fixture,
            String boneName,
            String message
    ) {
        AnimatedGeoBone bone = fixture.model().bones().get(boneName);
        require(bone != null, message + " (bone missing)");
        require(
                !fixture.plan().isDriven(bone),
                message + ": " + fixture.plan().decision(bone)
        );
    }

    private static void requireCompoundPony(
            Verification fixture,
            String boneName
    ) {
        PhysicsBoneSelectionPlan.Decision decision =
                fixture.plan().decision(fixture.model().bones().get(boneName));
        require(
                decision.structureRole()
                        == PhysicsBoneSelectionPlan.StructureRole
                        .COMPOUND_SINGLE_BONE
                        && decision.chainSegment().count() == 1
                        && decision.profile().angleScale() <= 0.42F
                        && decision.profile().tipDisplacementScale() <= 0.48F,
                "Single-bone ponytail did not receive conservative motion: "
                        + boneName
        );
    }

    private record Verification(
            AnimatedGeoModel model,
            PhysicsBoneSelectionPlan plan
    ) {
    }
}
