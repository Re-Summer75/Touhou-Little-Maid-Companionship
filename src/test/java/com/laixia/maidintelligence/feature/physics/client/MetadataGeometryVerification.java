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
        verifiesWindScaleBounds();
        verifiesMassScaleBounds();
        verifiesSchemaOneConstraintCompatibility();
        verifiesSchemaThreeCollisionParsing();
        verifiesOlderSchemasIgnoreCollision();
        verifiesEmptyAnchorFallback();
        verifiesExplicitAttachmentOverride();
    }

    private static void verifiesExplicitAttachmentOverride() {
        AnimatedGeoModel model = modelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.attachment_override",
                    "texture_width":32,"texture_height":32},
                  "bones":[
                    {"name":"Head","pivot":[0,8,0],
                     "cubes":[{"origin":[-4,4,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"Mount","parent":"Head","pivot":[3,8,0],
                     "cubes":[{"origin":[2.5,7.5,-.5],"size":[1,1,1],"uv":[0,0]}]},
                    {"name":"Tail","parent":"Mount","pivot":[3,7,0],
                     "cubes":[{"origin":[2.5,0,-.5],"size":[1,7,1],"uv":[0,0]}]}
                  ]}]}
                """);
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {"schema_version":3,"mode":"auto","chains":[{
                          "id":"author_mount","type":"HAIR","root":"Head/Mount",
                          "include_descendants":false,
                          "profile":{"stiffness_scale":0.5}
                        }]}
                        """).getAsJsonObject(),
                "attachment override verification"
        );
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:attachment_override",
                model,
                metadata
        );
        PhysicsBoneSelectionPlan.Decision mount =
                plan.decision(model.bones().get("Mount"));
        require(
                mount.driven()
                        && mount.source()
                        == PhysicsBoneSelectionPlan.Source.METADATA
                        && Math.abs(mount.profile().stiffnessScale() - 0.5F)
                        < 1.0E-5F,
                "Explicit metadata did not override rigid attachment inference"
        );
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
                              "wind_scale": 2.25,
                              "mass_scale": 1.6,
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
                Math.abs(chain.profile().windScale() - 2.25F) < 1.0E-5F,
                "Metadata wind scale was not parsed"
        );
        require(
                Math.abs(chain.profile().massScale() - 1.6F) < 1.0E-5F,
                "Metadata mass scale was not parsed"
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

    private static void verifiesWindScaleBounds() {
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {
                          "schema_version":3,
                          "chains":[
                            {
                              "id":"strong",
                              "root":"Hair",
                              "profile":{"wind_scale":9.0}
                            },
                            {
                              "id":"disabled",
                              "root":"Tail",
                              "profile":{"wind_scale":-2.0}
                            }
                          ]
                        }
                        """).getAsJsonObject(),
                "wind scale bounds verification"
        );
        require(
                metadata.chains().get(0).profile().windScale() == 4.0F
                        && metadata.chains().get(1).profile().windScale()
                        == 0.0F,
                "Metadata wind_scale was not clamped to 0-4"
        );
    }

    private static void verifiesMassScaleBounds() {
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {
                          "schema_version":3,
                          "chains":[
                            {
                              "id":"heavy",
                              "root":"Cape",
                              "profile":{"mass_scale":9.0}
                            },
                            {
                              "id":"light",
                              "root":"Ribbon",
                              "profile":{"mass_scale":0.0}
                            }
                          ]
                        }
                        """).getAsJsonObject(),
                "mass scale bounds verification"
        );
        require(
                metadata.chains().get(0).profile().massScale() == 4.0F
                        && metadata.chains().get(1).profile().massScale()
                        == 0.25F,
                "Metadata mass_scale was not clamped to 0.25-4"
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

    private static void verifiesSchemaThreeCollisionParsing() {
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {
                          "schema_version": 3,
                          "mode": "explicit",
                          "chains": [{
                            "id": "collision_shapes",
                            "type": "HAIR",
                            "root": "Hair",
                            "constraints": {
                              "collision": {
                                "auto": false,
                                "proxies": [
                                  {
                                    "kind": "Plane",
                                    "reference": "Head",
                                    "point": [1, 2, 3],
                                    "normal": [0, 1, 0],
                                    "hit_radius": 0.5
                                  },
                                  {
                                    "kind": "sphere",
                                    "reference": "Head",
                                    "center": [-4, 5, 6],
                                    "radius": 7
                                  },
                                  {
                                    "kind": "CAPSULE",
                                    "reference": "Body",
                                    "start": [8, 9, 10],
                                    "end": [11, 12, 13],
                                    "radius": 2,
                                    "hit_radius": 0.25
                                  },
                                  {
                                    "kind": "cone",
                                    "reference": "Body"
                                  }
                                ]
                              }
                            }
                          }]
                        }
                        """).getAsJsonObject(),
                "collision verification"
        );

        PhysicsBoneSelectionPlan.CollisionProfile collision =
                metadata.chains().get(0).constraints().collision();
        require(!collision.auto(), "Schema 3 collision auto:false was lost");
        require(
                collision.proxies().size() == 3,
                "Invalid collision proxy was not skipped independently"
        );

        PhysicsBoneSelectionPlan.CollisionProxySpec planeSpec =
                collision.proxies().get(0);
        require(
                "Head".equals(planeSpec.reference()),
                "Plane collision reference was not parsed"
        );
        require(
                planeSpec.shape()
                        instanceof PhysicsBoneSelectionPlan.CollisionShape.Plane,
                "Plane collision shape was not parsed"
        );
        PhysicsBoneSelectionPlan.CollisionShape.Plane plane =
                (PhysicsBoneSelectionPlan.CollisionShape.Plane) planeSpec.shape();
        require(
                plane.point().equals(
                        new PhysicsBoneSelectionPlan.CollisionVector(
                                1.0F,
                                2.0F,
                                3.0F
                        )
                ) && plane.normal().equals(
                        new PhysicsBoneSelectionPlan.CollisionVector(
                                0.0F,
                                1.0F,
                                0.0F
                        )
                ),
                "Plane collision vectors were not retained in Gecko pixels"
        );
        require(
                planeSpec.hitRadius().isPresent()
                        && Math.abs(planeSpec.hitRadius().get() - 0.5F)
                        < 1.0E-5F,
                "Plane hit_radius was not parsed"
        );

        PhysicsBoneSelectionPlan.CollisionProxySpec sphereSpec =
                collision.proxies().get(1);
        require(
                sphereSpec.shape()
                        instanceof PhysicsBoneSelectionPlan.CollisionShape.Sphere,
                "Sphere collision shape was not parsed"
        );
        PhysicsBoneSelectionPlan.CollisionShape.Sphere sphere =
                (PhysicsBoneSelectionPlan.CollisionShape.Sphere) sphereSpec.shape();
        require(
                sphere.center().equals(
                        new PhysicsBoneSelectionPlan.CollisionVector(
                                -4.0F,
                                5.0F,
                                6.0F
                        )
                ) && Math.abs(sphere.radius() - 7.0F) < 1.0E-5F,
                "Sphere collision values were not retained in Gecko pixels"
        );
        require(
                sphereSpec.hitRadius().isEmpty(),
                "Omitted hit_radius did not remain optional"
        );

        PhysicsBoneSelectionPlan.CollisionProxySpec capsuleSpec =
                collision.proxies().get(2);
        require(
                "Body".equals(capsuleSpec.reference())
                        && capsuleSpec.shape()
                        instanceof PhysicsBoneSelectionPlan.CollisionShape.Capsule,
                "Capsule collision shape or reference was not parsed"
        );
        PhysicsBoneSelectionPlan.CollisionShape.Capsule capsule =
                (PhysicsBoneSelectionPlan.CollisionShape.Capsule) capsuleSpec.shape();
        require(
                capsule.start().equals(
                        new PhysicsBoneSelectionPlan.CollisionVector(
                                8.0F,
                                9.0F,
                                10.0F
                        )
                ) && capsule.end().equals(
                        new PhysicsBoneSelectionPlan.CollisionVector(
                                11.0F,
                                12.0F,
                                13.0F
                        )
                ) && Math.abs(capsule.radius() - 2.0F) < 1.0E-5F,
                "Capsule collision values were not retained in Gecko pixels"
        );

        PhysicsMetadata automatic = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {
                          "schema_version": 3,
                          "chains": [{
                            "root": "Hair",
                            "constraints": {"collision": {"proxies": []}}
                          }]
                        }
                        """).getAsJsonObject(),
                "collision auto default verification"
        );
        require(
                automatic.chains().get(0).constraints().collision().auto(),
                "Schema 3 collision auto did not default to true"
        );
    }

    private static void verifiesOlderSchemasIgnoreCollision() {
        for (int schema = 1; schema <= 2; schema++) {
            PhysicsMetadata metadata = PhysicsMetadata.parse(
                    JsonParser.parseString("""
                            {
                              "schema_version": %d,
                              "chains": [{
                                "root": "Hair",
                                "type": "HAIR",
                                "constraints": {
                                  "backstop": false,
                                  "collision": {
                                    "auto": false,
                                    "proxies": [{
                                      "kind": "sphere",
                                      "reference": "Head",
                                      "center": [1, 2, 3],
                                      "radius": 4
                                    }]
                                  }
                                }
                              }]
                            }
                            """.formatted(schema)).getAsJsonObject(),
                    "schema " + schema + " collision compatibility"
            );
            PhysicsBoneSelectionPlan.ConstraintProfile constraints =
                    metadata.chains().get(0).constraints();
            require(
                    constraints.enabled() && !constraints.backstop(),
                    "Schema " + schema + " legacy constraint behavior changed"
            );
            require(
                    constraints.collision().auto()
                            && constraints.collision().proxies().isEmpty(),
                    "Schema " + schema + " consumed schema 3 collision fields"
            );
        }
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
