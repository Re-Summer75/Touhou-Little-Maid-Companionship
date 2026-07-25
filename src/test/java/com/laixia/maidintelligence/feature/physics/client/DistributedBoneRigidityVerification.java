package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneKinematics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class DistributedBoneRigidityVerification {
    private DistributedBoneRigidityVerification() {
    }

    static void run() throws Exception {
        verifiesBundledZhiban();
        verifiesGeometryPolicy();
        verifiesAllBundledModels();
    }

    private static void verifiesBundledZhiban() throws Exception {
        requireDistributedRigid("zhiban.json", "bone37");
        requireDistributedRigid("zhiban_hanfu.json", "bone23");
    }

    private static void requireDistributedRigid(
            String fileName,
            String boneName
    ) throws Exception {
        AnimatedGeoModel model = new AnimatedGeoModel(loadGeoModel(
                MODEL_DIRECTORY.resolve(fileName)
        ));
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:distributed_" + fileName,
                model,
                PhysicsMetadata.EMPTY
        );
        AnimatedGeoBone bone = model.bones().get(boneName);
        PhysicsBoneSelectionPlan.Decision decision = plan.decision(bone);
        require(
                BoneKinematics.hasDistributedGeometry(bone)
                        && !decision.driven()
                        && decision.structureRole()
                        == PhysicsBoneSelectionPlan.StructureRole
                        .RIGID_ATTACHMENT_BASE
                        && plan.kinematics(bone) == null,
                fileName + " " + boneName
                        + " still uses one physical pivot: " + decision
        );
    }

    private static void verifiesGeometryPolicy() {
        AnimatedGeoModel model = modelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.distributed_bone",
                    "texture_width":32,"texture_height":32},
                  "bones":[
                    {"name":"Head","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"DistributedHair","parent":"Head","pivot":[0,24,0],
                     "cubes":[
                       {"origin":[-7,25,-1],"size":[2,2,1],"uv":[0,0]},
                       {"origin":[-3,21,-1],"size":[2,2,1],"uv":[0,0]},
                       {"origin":[1,25,-1],"size":[2,2,1],"uv":[0,0]},
                       {"origin":[5,21,-1],"size":[2,2,1],"uv":[0,0]}
                     ]},
                    {"name":"ConnectedHair","parent":"Head","pivot":[0,24,2],
                     "cubes":[
                       {"origin":[-.5,20,2],"size":[1,4,1],"uv":[0,0]},
                       {"origin":[-.5,16,2],"size":[1,4,1],"uv":[0,0]}
                     ]}
                  ]}]}
                """);
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:distributed_policy",
                model,
                PhysicsMetadata.EMPTY
        );
        AnimatedGeoBone distributed = model.bones().get("DistributedHair");
        AnimatedGeoBone connected = model.bones().get("ConnectedHair");
        require(
                BoneKinematics.hasDistributedGeometry(distributed)
                        && !plan.isDriven(distributed),
                "Synthetic distributed geometry was physicalized"
        );
        require(
                !BoneKinematics.hasDistributedGeometry(connected)
                        && plan.isDriven(connected),
                "Connected hair geometry was rejected as distributed"
        );
        PhysicsMetadata explicit = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {"schema_version":3,"mode":"auto","chains":[{
                          "id":"explicit_distributed","type":"HAIR",
                          "root":"Head/DistributedHair",
                          "include_descendants":false
                        }]}
                        """).getAsJsonObject(),
                "explicit distributed geometry verification"
        );
        PhysicsBoneSelectionPlan explicitPlan =
                PhysicsBoneDiscoverer.discover(
                        "verification:explicit_distributed",
                        model,
                        explicit
                );
        require(
                explicitPlan.isDriven(distributed)
                        && explicitPlan.decision(distributed).source()
                        == PhysicsBoneSelectionPlan.Source.METADATA,
                "Explicit metadata could not override distributed auto safety"
        );
    }

    private static void verifiesAllBundledModels() throws Exception {
        List<Path> paths;
        try (var stream = Files.list(MODEL_DIRECTORY)) {
            paths = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".json"))
                    .sorted()
                    .toList();
        }
        for (Path path : paths) {
            AnimatedGeoModel model = new AnimatedGeoModel(loadGeoModel(path));
            PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                    "verification:distributed_" + path.getFileName(),
                    model,
                    PhysicsMetadata.EMPTY
            );
            for (AnimatedGeoBone bone : model.bones().values()) {
                require(
                        !BoneKinematics.hasDistributedGeometry(bone)
                                || !plan.isDriven(bone),
                        path.getFileName() + " " + bone.getName()
                                + " distributed geometry remained automatic"
                );
            }
        }
    }
}
