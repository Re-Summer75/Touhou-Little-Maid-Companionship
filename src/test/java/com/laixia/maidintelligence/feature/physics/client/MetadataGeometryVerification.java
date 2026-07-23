package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.google.gson.JsonParser;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class MetadataGeometryVerification {
    private MetadataGeometryVerification() {
    }

    static void run() {
        verifiesMetadataParsing();
        verifiesSchemaOneConstraintCompatibility();
        verifiesEmptyAnchorFallback();
    }

    private static void verifiesMetadataParsing() {
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {
                          "schema_version": 1,
                          "mode": "explicit",
                          "exclude": ["Head/Mask"],
                          "chains": [{
                            "id": "anonymous_hair",
                            "type": "HAIR",
                            "root": "Head/bone17",
                            "include_descendants": true,
                            "profile": {
                              "stiffness_scale": 0.75,
                              "angle_scale": 0.5
                            },
                            "constraints": {
                              "simulation_space": "head_local",
                              "rotation_inertia_scale": 0.2,
                              "swing_limits": {
                                "inward_degrees": 12.0
                              },
                              "backstop": true,
                              "head_collision": false,
                              "hit_radius_scale": 1.25
                            }
                          }]
                        }
                        """).getAsJsonObject(),
                "verification"
        );
        require(
                metadata.mode() == PhysicsMetadata.Mode.EXPLICIT,
                "Explicit mode was lost"
        );
        require(
                metadata.excludes().contains("Head/Mask"),
                "Metadata exclude was lost"
        );
        require(metadata.chains().size() == 1, "Metadata chain was not parsed");
        PhysicsMetadata.Chain chain = metadata.chains().get(0);
        require(
                chain.type() == PhysicsBoneSelectionPlan.PartType.HAIR,
                "Metadata part type was not parsed"
        );
        require(
                Math.abs(chain.profile().stiffnessScale() - 0.75F) < 1.0E-5F,
                "Metadata stiffness scale was not parsed"
        );
        require(
                Math.abs(chain.profile().angleScale() - 0.5F) < 1.0E-5F,
                "Metadata angle scale was not parsed"
        );
        require(
                chain.constraints().simulationSpace()
                        == PhysicsBoneSelectionPlan.SimulationSpace.HEAD_LOCAL,
                "Metadata simulation space was not parsed"
        );
        require(
                Math.abs(chain.constraints().rotationInertiaScale() - 0.2F)
                        < 1.0E-5F,
                "Metadata rotation inertia was not parsed"
        );
        require(
                Math.abs(
                        chain.constraints().swingLimits().inward()
                                - Math.toRadians(12.0D)
                ) < 1.0E-5D,
                "Metadata asymmetric swing limit was not parsed"
        );
        require(
                chain.constraints().backstop()
                        && !chain.constraints().headCollision(),
                "Metadata collision switches were not parsed"
        );
    }

    private static void verifiesSchemaOneConstraintCompatibility() {
        PhysicsMetadata legacy = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {
                          "schema_version":1,
                          "mode":"explicit",
                          "chains":[{"id":"old","type":"HAIR","root":"Hair"}]
                        }
                        """).getAsJsonObject(),
                "legacy verification"
        );
        PhysicsMetadata current = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {
                          "schema_version":2,
                          "mode":"explicit",
                          "chains":[{"id":"new","type":"HAIR","root":"Hair"}]
                        }
                        """).getAsJsonObject(),
                "current verification"
        );
        require(
                !legacy.chains().get(0).constraints().enabled(),
                "Schema 1 sidecar silently enabled new constraints"
        );
        require(
                current.chains().get(0).constraints().enabled(),
                "Schema 2 sidecar did not receive constraint defaults"
        );
    }

    private static void verifiesEmptyAnchorFallback() {
        AnimatedGeoModel model = modelFromJson("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {
                      "identifier": "geometry.empty_anchor_verification",
                      "texture_width": 64,
                      "texture_height": 64,
                      "visible_bounds_width": 4,
                      "visible_bounds_height": 4,
                      "visible_bounds_offset": [0, 1, 0]
                    },
                    "bones": [
                      {"name":"Root","pivot":[0,0,0]},
                      {"name":"Body","parent":"Root","pivot":[0,8,0]},
                      {"name":"TorsoCore","parent":"Body","pivot":[0,8,0],
                       "cubes":[{"origin":[-3,0,-2],"size":[6,16,4],"uv":[0,0]}]},
                      {"name":"Head","parent":"Body","pivot":[0,16,0]},
                      {"name":"HeadCore","parent":"Head","pivot":[0,20,0],
                       "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                      {"name":"HairContainer","parent":"Head","pivot":[0,20,0]},
                      {"name":"HairShell","parent":"HairContainer","pivot":[0,20,0],
                       "cubes":[{"origin":[-10,14,-10],"size":[20,12,20],"uv":[0,0]}]},
                      {"name":"TailContainer","parent":"Body","pivot":[0,6,2]},
                      {"name":"TailMesh","parent":"TailContainer","pivot":[0,6,2],
                       "cubes":[{"origin":[-1,0,2],"size":[2,6,16],"uv":[0,0]}]}
                    ]
                  }]
                }
                """);
        PhysicsBoneGeometry.Analysis geometry = PhysicsBoneGeometry.analyze(model);
        require(
                geometry.headBounds().size().x < 0.75F,
                "Empty Head anchor absorbed the hair subtree"
        );
        require(
                geometry.bodyBounds().size().z < 0.50F,
                "Empty Body anchor absorbed the tail subtree"
        );
    }
}
