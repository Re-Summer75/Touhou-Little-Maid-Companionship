package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneKinematics;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class PivotRolePolicyVerification {
    private PivotRolePolicyVerification() {
    }

    static void run() {
        AnimatedGeoModel model = modelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.pivot_role_policy",
                    "texture_width":32,"texture_height":32},
                  "bones":[
                    {"name":"Head","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"Moderate","parent":"Head","pivot":[5,24,0],
                     "cubes":[{"origin":[1,23,-.5],"size":[2,4,1],"uv":[0,0]}]},
                    {"name":"GrossChain","parent":"Head","pivot":[20,24,0],
                     "cubes":[{"origin":[1,23,-.5],"size":[2,4,1],"uv":[0,0]}]}
                  ]}]}
                """);
        AnimatedGeoBone head = model.bones().get("Head");
        BoneKinematics.Metrics moderate = BoneKinematics.measure(
                model.bones().get("Moderate"),
                head,
                head,
                PhysicsBoneSelectionPlan.PartType.RIBBON,
                PhysicsBoneSelectionPlan.StructureRole.DANGLING_ACCESSORY,
                new PhysicsBoneSelectionPlan.ChainSegment("moderate", 0, 1),
                null
        );
        require(
                moderate.compensatesPivot(),
                "Single-bone structure role suppressed a valid pivot correction"
        );
        BoneKinematics.Metrics grossChain = BoneKinematics.measure(
                model.bones().get("GrossChain"),
                head,
                head,
                PhysicsBoneSelectionPlan.PartType.HAIR,
                PhysicsBoneSelectionPlan.StructureRole.FLEXIBLE_CHAIN_SEGMENT,
                new PhysicsBoneSelectionPlan.ChainSegment("gross", 0, 3),
                null
        );
        require(
                grossChain.compensatesPivot(),
                "A grossly detached authored chain joint escaped correction"
        );
    }
}
