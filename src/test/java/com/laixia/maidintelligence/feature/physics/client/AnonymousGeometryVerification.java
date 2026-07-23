package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireDriven;

final class AnonymousGeometryVerification {
    private AnonymousGeometryVerification() {
    }

    static void run() {
        AnimatedGeoModel model = modelFromJson("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {
                      "identifier": "geometry.physics_verification",
                      "texture_width": 64,
                      "texture_height": 64,
                      "visible_bounds_width": 4,
                      "visible_bounds_height": 4,
                      "visible_bounds_offset": [0, 1, 0]
                    },
                    "bones": [
                      {"name":"Root","pivot":[0,0,0]},
                      {"name":"Body","parent":"Root","pivot":[0,8,0],
                       "cubes":[{"origin":[-3,0,-2],"size":[6,16,4],"uv":[0,0]}]},
                      {"name":"Head","parent":"Body","pivot":[0,16,0],
                       "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                      {"name":"bone90","parent":"Head","pivot":[-4,20,0],
                       "cubes":[{"origin":[-6,19,-1],"size":[2,3,1],"uv":[0,0]}]},
                      {"name":"bone91","parent":"Head","pivot":[4,20,0],
                       "cubes":[{"origin":[4,19,-1],"size":[2,3,1],"uv":[0,0]}]},

                      {"name":"bone1","parent":"Head","pivot":[0,20,0],
                       "cubes":[{"origin":[-4.5,15.5,-4.5],"size":[9,9,9],
                                 "inflate":-0.35,"uv":[0,0]}]},
                      {"name":"bone2","parent":"bone1","pivot":[-2,18,3],
                       "cubes":[{"origin":[-3,8,3],"size":[2,10,1],"uv":[0,0]}]},
                      {"name":"bone3","parent":"bone1","pivot":[2,18,3],
                       "cubes":[{"origin":[1,8,3],"size":[2,10,1],"uv":[0,0]}]},

                      {"name":"bone30","parent":"Body","pivot":[0,8,2],
                       "cubes":[{"origin":[-1,5,2],"size":[2,4,4],"uv":[0,0]}]},
                      {"name":"bone31","parent":"bone30","pivot":[0,6,6],
                       "cubes":[{"origin":[-0.75,4,6],"size":[1.5,2,4],"uv":[0,0]}]},
                      {"name":"bone32","parent":"bone31","pivot":[0,5,10],
                       "cubes":[{"origin":[-0.5,4,10],"size":[1,1,4],"uv":[0,0]}]},

                      {"name":"bone40","parent":"Body","pivot":[0,10,2],
                       "cubes":[{"origin":[-4,1,2],"size":[8,9,1],"uv":[0,0]}]},
                      {"name":"bone41","parent":"bone40","pivot":[0,3,3],
                       "cubes":[{"origin":[-3,0,3],"size":[6,3,1],"uv":[0,0]}]},

                      {"name":"bone50","parent":"Body","pivot":[3,13,0],
                       "cubes":[{"origin":[3,11,-1],"size":[8,1,3],"uv":[0,0]}]},
                      {"name":"bone51","parent":"bone50","pivot":[11,12,0],
                       "cubes":[{"origin":[11,11.5,-0.5],"size":[4,1,1],"uv":[0,0]}]},

                      {"name":"bone60","parent":"Body","pivot":[-3,14,0],
                       "cubes":[{"origin":[-4,5,-1],"size":[2,9,2],"uv":[0,0]}]},
                      {"name":"bone61","parent":"bone60","pivot":[-3,5,0],
                       "cubes":[{"origin":[-4,0,-1],"size":[2,5,2],"uv":[0,0]}]},

                      {"name":"RightHandLocator","parent":"Body","pivot":[-3,12,0]},
                      {"name":"bone100","parent":"RightHandLocator","pivot":[-3,12,0],
                       "cubes":[{"origin":[-12,11,-1],"size":[9,1,3],"uv":[0,0]}]},
                      {"name":"bone101","parent":"bone100","pivot":[-12,12,0],
                       "cubes":[{"origin":[-16,11.5,-0.5],"size":[4,1,1],"uv":[0,0]}]},

                      {"name":"bone70","parent":"Body","pivot":[0,14,2],
                       "cubes":[{"origin":[-3,8,2],"size":[6,6,1],"uv":[0,0]}]},
                      {"name":"bone71","parent":"bone70","pivot":[0,9,3],
                       "cubes":[{"origin":[-2.5,4,3],"size":[5,5,1],"uv":[0,0]}]},
                      {"name":"bone72","parent":"bone71","pivot":[0,5,4],
                       "cubes":[{"origin":[-2,1,4],"size":[4,4,1],"uv":[0,0]}]},

                      {"name":"bone80","parent":"Body","pivot":[1,10,2],
                       "cubes":[{"origin":[0.5,2,2],"size":[1,8,0.25],"uv":[0,0]}]},
                      {"name":"bone81","parent":"bone80","pivot":[1,3,2.25],
                       "cubes":[{"origin":[0.6,-3,2.25],"size":[0.8,6,0.2],"uv":[0,0]}]},
                      {"name":"bone82","parent":"bone81","pivot":[1,-2,2.45],
                       "cubes":[{"origin":[0.7,-6,2.45],"size":[0.6,4,0.15],"uv":[0,0]}]}
                    ]
                  }]
                }
                """);
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:anonymous",
                model,
                PhysicsMetadata.EMPTY
        );
        requirePart(plan, model, "bone1",
                PhysicsBoneSelectionPlan.PartType.HEAD_SHELL,
                "Anonymous head shell was not discovered");
        requirePart(plan, model, "bone2",
                PhysicsBoneSelectionPlan.PartType.HAIR,
                "Anonymous hair strand was not discovered");
        requirePart(plan, model, "bone90",
                PhysicsBoneSelectionPlan.PartType.EAR,
                "Anonymous mirrored ear was not discovered");
        requirePart(plan, model, "bone30",
                PhysicsBoneSelectionPlan.PartType.TAIL,
                "Anonymous rear chain was not discovered as a tail");
        requirePart(plan, model, "bone31",
                PhysicsBoneSelectionPlan.PartType.TAIL,
                "Anonymous tail continuation lost its parent type");
        require(
                plan.decision(model.bones().get("bone30")).chainId().equals(
                        plan.decision(model.bones().get("bone31")).chainId()
                ),
                "Serial tail segments did not share a chain id"
        );
        requirePart(plan, model, "bone40",
                PhysicsBoneSelectionPlan.PartType.SKIRT,
                "Anonymous lower cloth was not discovered as a skirt");
        requirePart(plan, model, "bone50",
                PhysicsBoneSelectionPlan.PartType.WING,
                "Anonymous lateral chain was not discovered as a wing");
        requirePart(plan, model, "bone70",
                PhysicsBoneSelectionPlan.PartType.CAPE,
                "Anonymous rear cloth was not discovered as a cape");
        requirePart(plan, model, "bone80",
                PhysicsBoneSelectionPlan.PartType.RIBBON,
                "Anonymous narrow cloth was not discovered as a ribbon");
        require(!plan.isDriven(model.bones().get("bone60")),
                "Anonymous rigid arm-like chain was selected");
        require(!plan.isDriven(model.bones().get("bone100")),
                "Visible child below a hand locator was selected");
    }

    private static void requirePart(
            PhysicsBoneSelectionPlan plan,
            AnimatedGeoModel model,
            String boneName,
            PhysicsBoneSelectionPlan.PartType type,
            String message
    ) {
        requireDriven(plan, model.bones().get(boneName), type, message);
    }
}
