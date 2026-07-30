package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.laixia.maidintelligence.feature.physics.layout.BoneKinematics;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Ensures support stability is independent of cube count and absolute size.
 */
final class SmallSupportStabilityVerification {
    private SmallSupportStabilityVerification() {
    }

    static void run() {
        verifiesTinySingleCubeCorrection();
        verifiesTinyUnsupportedMountRemainsRigid();
        verifiesUpwardHairKeepsItsCantileverRoot();
    }

    private static void verifiesTinySingleCubeCorrection() {
        BoneModelSnapshot model = tinyModel(0.0F, "TinyRibbon");
        BoneModelSnapshot.Bone attachment = model.bones().get("TinyRibbon");
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:tiny_supported_mount",
                model,
                PhysicsMetadata.EMPTY
        );
        BoneKinematics.Metrics metrics = plan.kinematics(attachment);
        PhysicsBoneSelectionPlan.Decision decision =
                plan.decision(attachment);
        require(
                decision.driven()
                        && metrics != null
                        && metrics.supportStabilityPivotCorrected()
                        && metrics.contactConfidence() >= 0.15F
                        && metrics.authoredPivot().y * 16.0F <= 13.75F
                        && metrics.effectivePivot().y * 16.0F >= 14.625F
                        && decision.structureRole()
                        == PhysicsBoneSelectionPlan.StructureRole
                        .COMPOUND_SINGLE_BONE
                        && decision.profile().gravityScale() == 0.0F,
                "Tiny one-cube attachment did not use upper support: "
                        + decision + " / " + metrics
        );
    }

    private static void verifiesTinyUnsupportedMountRemainsRigid() {
        BoneModelSnapshot model = tinyUnsupportedDanglingModel();
        BoneModelSnapshot.Bone attachment = model.bones().get("RibbonMount");
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:tiny_unsupported_mount",
                model,
                PhysicsMetadata.EMPTY
        );
        PhysicsBoneSelectionPlan.Decision decision =
                plan.decision(attachment);
        require(
                !decision.driven()
                        && decision.structureRole()
                        == PhysicsBoneSelectionPlan.StructureRole
                        .RIGID_ATTACHMENT_BASE,
                "Tiny unsupported mount stayed physical: " + decision
        );
    }

    private static void verifiesUpwardHairKeepsItsCantileverRoot() {
        BoneModelSnapshot model = coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.tiny_cantilever",
                    "texture_width":16,"texture_height":16},
                  "bones":[
                    {"name":"Head","pivot":[0,16,0],
                     "cubes":[{"origin":[-2,16,-2],"size":[4,4,4],"uv":[0,0]}]},
                    {"name":"Ahoge","parent":"Head","pivot":[0,20,0],
                     "cubes":[{"origin":[-.5,20,-.5],"size":[1,2,1],"uv":[0,0]}]}
                  ]}]}
                """);
        BoneModelSnapshot.Bone head = model.bones().get("Head");
        BoneKinematics.Metrics metrics = BoneKinematics.measure(
                model.bones().get("Ahoge"),
                head,
                head,
                PhysicsBoneSelectionPlan.PartType.HAIR
        );
        require(
                !metrics.supportStabilityPivotCorrected()
                        && !metrics.supportStabilityPivotPreserved()
                        && !metrics.supportStabilityUnsupported(),
                "Upward hair root was mistaken for suspended attachment"
        );
    }

    private static BoneModelSnapshot tinyModel(
            float depthOffset,
            String name
    ) {
        return coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.tiny_support",
                    "texture_width":16,"texture_height":16},
                  "bones":[
                    {"name":"Head","pivot":[0,16,0],
                     "cubes":[{"origin":[-2,16,-2],"size":[4,4,4],"uv":[0,0]}]},
                    {"name":"%s","parent":"Head","pivot":[0,13.75,%s],
                     "cubes":[{"origin":[-.5,13.5,%s],"size":[1,2,1],"uv":[0,0]}]}
                  ]}]}
                """.formatted(name, depthOffset, depthOffset));
    }

    private static BoneModelSnapshot tinyUnsupportedDanglingModel() {
        return coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.tiny_unsupported",
                    "texture_width":16,"texture_height":16},
                  "bones":[
                    {"name":"Head","pivot":[0,16,0],
                     "cubes":[{"origin":[-2,16,-2],"size":[4,4,4],"uv":[0,0]}]},
                    {"name":"RibbonMount","parent":"Head",
                     "pivot":[2.75,14.25,6],
                     "cubes":[{"origin":[2.25,13.5,6],
                               "size":[1,2,1],"uv":[0,0]}]}
                  ]}]}
                """);
    }
}
