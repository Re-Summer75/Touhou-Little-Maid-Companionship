package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireDriven;

final class HairDiscoveryVerification {
    private HairDiscoveryVerification() {
    }

    static void run() {
        BoneModelSnapshot model = coreModelFromJson("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {
                      "identifier": "geometry.hair_fallback_verification",
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

                      {"name":"HeadHair","parent":"Body","pivot":[0,20,3],
                       "cubes":[{"origin":[-3,12,3],"size":[6,10,1],"uv":[0,0]}]},
                      {"name":"MFrontHair","parent":"Body","pivot":[0,21,-4],
                       "cubes":[{"origin":[-2,15,-5],"size":[4,8,1],"uv":[0,0]}]},
                      {"name":"Face_Bangs","parent":"Head","pivot":[0,21,-4],
                       "cubes":[{"origin":[-2,16,-5],"size":[4,6,1],"uv":[0,0]}]},
                      {"name":"头发2","parent":"Body","pivot":[3,20,2],
                       "cubes":[{"origin":[2,12,2],"size":[2,9,1],"uv":[0,0]}]},

                      {"name":"MBangs","parent":"Head","pivot":[0,21,-4]},
                      {"name":"pieceA","parent":"MBangs","pivot":[-1,20,-4],
                       "cubes":[{"origin":[-2,18,-5],"size":[2,2,2],"uv":[0,0]}]},
                      {"name":"pieceB","parent":"MBangs","pivot":[1,20,-4],
                       "cubes":[{"origin":[0,18,-5],"size":[2,2,2],"uv":[0,0]}]},

                      {"name":"frontGroup","parent":"Head","pivot":[0,20,-4]},
                      {"name":"frontPieceA","parent":"frontGroup","pivot":[-1,20,-4],
                       "cubes":[{"origin":[-2,18,-5],"size":[2,2,2],"uv":[0,0]}]},
                      {"name":"frontPieceB","parent":"frontGroup","pivot":[1,20,-4],
                       "cubes":[{"origin":[0,18,-5],"size":[2,2,2],"uv":[0,0]}]},

                      {"name":"Mouth","parent":"Head","pivot":[0,18,-4]},
                      {"name":"mouthPiece","parent":"Mouth","pivot":[0,18,-4],
                       "cubes":[{"origin":[-1,17,-5],"size":[2,2,1],"uv":[0,0]}]},

                      {"name":"HairAliasAnchor","parent":"Head","pivot":[0,20,-4]},
                      {"name":"saihong","parent":"HairAliasAnchor","pivot":[0,19,-4]},
                      {"name":"blushPiece","parent":"saihong","pivot":[0,19,-4],
                       "cubes":[{"origin":[-1,18,-5],"size":[2,1,0.2],"uv":[0,0]}]},
                      {"name":"Hair_Mouth","parent":"HairAliasAnchor","pivot":[0,18,-4],
                       "cubes":[{"origin":[-1,17,-5],"size":[2,1,0.2],"uv":[0,0]}]},
                      {"name":"Left_meimao","parent":"HairAliasAnchor","pivot":[1,21,-4],
                       "cubes":[{"origin":[0,20,-5],"size":[2,0.5,0.2],"uv":[0,0]}]},

                      {"name":"boneLoose","parent":"Root","pivot":[0,20,0],
                       "cubes":[{"origin":[-1,10,-0.5],"size":[2,10,1],"uv":[0,0]}]},
                      {"name":"boneLoose2","parent":"boneLoose","pivot":[0,12,0],
                       "cubes":[{"origin":[-0.75,5,-0.4],"size":[1.5,7,0.8],"uv":[0,0]}]},

                      {"name":"HairGuide","parent":"Head","pivot":[0,20,3]},
                      {"name":"bone17","parent":"HairGuide","pivot":[0,20,3],
                       "cubes":[{"origin":[-1,12,3],"size":[2,8,1],"uv":[0,0]}]},

                      {"name":"boneShell","parent":"Head","pivot":[0,20,0],
                       "cubes":[{"origin":[-4.5,15.5,-4.5],"size":[9,9,9],"uv":[0,0]}]},
                      {"name":"boneShellLeft","parent":"boneShell","pivot":[-3,18,3],
                       "cubes":[{"origin":[-4,8,3],"size":[2,10,1],"uv":[0,0]}]},
                      {"name":"boneShellRight","parent":"boneShell","pivot":[3,18,3],
                       "cubes":[{"origin":[2,8,3],"size":[2,10,1],"uv":[0,0]}]},

                      {"name":"RigidDecoration","parent":"Head","pivot":[0,23,0],
                       "cubes":[{"origin":[-1,22,-1],"size":[2,2,2],"uv":[0,0]}]}
                    ]
                  }]
                }
                """);
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:hair-fallbacks",
                model,
                PhysicsMetadata.EMPTY
        );
        requireHair(plan, model, "HeadHair",
                "Hair outside the Head hierarchy was not discovered");
        requireHair(plan, model, "MFrontHair",
                "Visible M-prefixed hair was mistaken for an empty pivot");
        requireHair(plan, model, "Face_Bangs",
                "Face-prefixed bangs were rejected as facial geometry");
        requireHair(plan, model, "pieceA",
                "Short bangs did not inherit their branching MBangs anchor");
        requireHair(plan, model, "pieceB",
                "Second bangs branch was not selected");
        requireHair(plan, model, "frontPieceA",
                "Anonymous clustered fringe was not discovered");
        require(
                !plan.isDriven(model.bones().get("mouthPiece")),
                "Anonymous facial geometry was mistaken for bangs"
        );
        require(
                !plan.isDriven(model.bones().get("blushPiece"))
                        && !plan.isDriven(model.bones().get("Hair_Mouth"))
                        && !plan.isDriven(model.bones().get("Left_meimao")),
                "Facial aliases below a hair anchor inherited hair physics"
        );
        requireHair(plan, model, "头发2",
                "CJK hair name was rejected as a rigid head bone");
        requireHair(plan, model, "boneLoose",
                "Anonymous hair outside the Head hierarchy was not discovered");
        requireHair(plan, model, "bone17",
                "Anonymous geometry did not inherit its empty hair anchor");
        requireDriven(
                plan,
                model.bones().get("boneShell"),
                PhysicsBoneSelectionPlan.PartType.HEAD_SHELL,
                "Large anonymous head shell was rejected as a rigid core"
        );
        require(
                !plan.isDriven(model.bones().get("HairGuide")),
                "Empty semantic hair anchor was driven directly"
        );
        require(
                !plan.isDriven(model.bones().get("RigidDecoration")),
                "Rigid head decoration became hair physics"
        );
    }

    private static void requireHair(
            PhysicsBoneSelectionPlan plan,
            BoneModelSnapshot model,
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
