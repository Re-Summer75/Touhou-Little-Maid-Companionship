package com.laixia.maidintelligence.feature.physics.client.wind;

import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.atmosphere.client.wind.JomlWindVectorPort;
import com.laixia.maidintelligence.feature.atmosphere.port.MutableWindVectorPort;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadataJsonParser;
import com.laixia.maidintelligence.feature.physics.discovery.PhysicsBoneDiscoverer;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadata;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.EnvironmentalWindVerificationFacade.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.EnvironmentalWindVerificationFacade.require;

final class WindScenarioSupport {
    static final Vector3f ZERO = new Vector3f();
    static final MutableWindVectorPort<Vector3f> WIND_VECTORS =
            JomlWindVectorPort.INSTANCE;

    private WindScenarioSupport() {
    }

    static float[] highPass(float[] samples) {
        float[] differences = new float[samples.length - 1];
        for (int index = 0; index < differences.length; index++) {
            differences[index] = samples[index + 1] - samples[index];
        }
        return differences;
    }

    static float correlation(float[] first, float[] second) {
        float firstMean = 0.0F;
        float secondMean = 0.0F;
        for (int index = 0; index < first.length; index++) {
            firstMean += first[index];
            secondMean += second[index];
        }
        firstMean /= first.length;
        secondMean /= second.length;
        float covariance = 0.0F;
        float firstVariance = 0.0F;
        float secondVariance = 0.0F;
        for (int index = 0; index < first.length; index++) {
            float a = first[index] - firstMean;
            float b = second[index] - secondMean;
            covariance += a * b;
            firstVariance += a * a;
            secondVariance += b * b;
        }
        float denominator = (float) Math.sqrt(
                firstVariance * secondVariance
        );
        return denominator <= 1.0E-12F ? 0.0F : covariance / denominator;
    }

    static float angleBetween(Vector3f left, Vector3f right) {
        return (float) Math.acos(
                Math.max(-1.0F, Math.min(1.0F, left.dot(right)))
        );
    }

    static ChainWindFixture createChainFixture() {
        BoneModelSnapshot model = coreModelFromJson("""
                {
                  "format_version":"1.12.0",
                  "minecraft:geometry":[{
                    "description":{
                      "identifier":"geometry.wind_chain",
                      "texture_width":16,
                      "texture_height":16
                    },
                    "bones":[
                      {"name":"Root","pivot":[0,24,0]},
                      {"name":"Tail1","parent":"Root","pivot":[0,24,0],
                       "cubes":[{"origin":[-.5,20,-.5],"size":[1,4,1],
                                 "uv":[0,0]}]},
                      {"name":"Tail2","parent":"Tail1","pivot":[0,20,0],
                       "cubes":[{"origin":[-.5,16,-.5],"size":[1,4,1],
                                 "uv":[0,0]}]},
                      {"name":"Tail3","parent":"Tail2","pivot":[0,16,0],
                       "cubes":[{"origin":[-.5,12,-.5],"size":[1,4,1],
                                 "uv":[0,0]}]},
                      {"name":"Tail4","parent":"Tail3","pivot":[0,12,0],
                       "cubes":[{"origin":[-.5,8,-.5],"size":[1,4,1],
                                 "uv":[0,0]}]}
                    ]
                  }]
                }
                """);
        PhysicsMetadata metadata = PhysicsMetadataJsonParser.parse(
                JsonParser.parseString("""
                        {
                          "schema_version":1,
                          "mode":"explicit",
                          "chains":[{
                            "id":"wind_chain",
                            "type":"TAIL",
                            "root":"Root/Tail1",
                            "profile":{
                              "gravity_scale":0.0,
                              "wind_scale":1.0
                            }
                          }]
                        }
                        """).getAsJsonObject(),
                "environmental wind chain verification"
        );
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:wind_chain",
                model,
                metadata
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        return new ChainWindFixture(
                new SpringBoneSolver(layout),
                new int[]{
                        drivenSlot(layout, model, "Tail1"),
                        drivenSlot(layout, model, "Tail2"),
                        drivenSlot(layout, model, "Tail3"),
                        drivenSlot(layout, model, "Tail4")
                }
        );
    }

    static SiblingWindFixture createSiblingFixture() {
        BoneModelSnapshot model = coreModelFromJson("""
                {
                  "format_version":"1.12.0",
                  "minecraft:geometry":[{
                    "description":{
                      "identifier":"geometry.wind_siblings",
                      "texture_width":16,
                      "texture_height":16
                    },
                    "bones":[
                      {"name":"Root","pivot":[0,0,0]},
                      {"name":"StrandA","parent":"Root","pivot":[-3,6,0],
                       "cubes":[{
                         "origin":[-3.5,0,-.5],
                         "size":[1,6,1],
                         "uv":[0,0]
                       }]},
                      {"name":"StrandB","parent":"Root","pivot":[3,6,0],
                       "cubes":[{
                         "origin":[2.5,0,-.5],
                         "size":[1,6,1],
                         "uv":[0,0]
                       }]}
                    ]
                  }]
                }
                """);
        PhysicsMetadata metadata = PhysicsMetadataJsonParser.parse(
                JsonParser.parseString("""
                        {
                          "schema_version":1,
                          "mode":"explicit",
                          "chains":[
                            {
                              "id":"strand_a",
                              "type":"HAIR",
                              "root":"Root/StrandA",
                              "profile":{
                                "gravity_scale":0.0,
                                "wind_scale":1.0
                              }
                            },
                            {
                              "id":"strand_b",
                              "type":"HAIR",
                              "root":"Root/StrandB",
                              "profile":{
                                "gravity_scale":0.0,
                                "wind_scale":1.0
                              }
                            }
                          ]
                        }
                        """).getAsJsonObject(),
                "environmental wind sibling verification"
        );
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:wind_siblings",
                model,
                metadata
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        return new SiblingWindFixture(
                new SpringBoneSolver(layout),
                drivenSlot(layout, model, "StrandA"),
                drivenSlot(layout, model, "StrandB")
        );
    }

    private static int drivenSlot(
            PhysicsSolverLayout layout,
            BoneModelSnapshot model,
            String boneName
    ) {
        BoneModelSnapshot.Bone bone = model.bones().get(boneName);
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).bone() == bone) {
                int slot = layout.node(index).drivenSlot();
                require(slot >= 0, boneName + " was not driven");
                return slot;
            }
        }
        throw new IllegalStateException(boneName + " is missing");
    }

    static WindFixture createFixture(String suffix) {
        return createFixture(suffix, "TAIL");
    }

    static WindFixture createFixture(String suffix, String type) {
        return createFixture(suffix, type, 1.0F);
    }

    static WindFixture createFixture(
            String suffix,
            String type,
            float massScale
    ) {
        BoneModelSnapshot model = coreModelFromJson("""
                {
                  "format_version":"1.12.0",
                  "minecraft:geometry":[{
                    "description":{
                      "identifier":"geometry.wind_%s",
                      "texture_width":16,
                      "texture_height":16
                    },
                    "bones":[
                      {"name":"Root","pivot":[0,0,0]},
                      {"name":"Tail","parent":"Root","pivot":[0,6,0],
                       "cubes":[{
                         "origin":[-.5,0,-.5],
                         "size":[1,6,1],
                         "uv":[0,0]
                       }]}
                    ]
                  }]
                }
                """.formatted(suffix));
        PhysicsMetadata metadata = PhysicsMetadataJsonParser.parse(
                JsonParser.parseString("""
                        {
                          "schema_version":1,
                          "mode":"explicit",
                          "chains":[{
                            "id":"wind_%s",
                            "type":"%s",
                            "root":"Root/Tail",
                            "profile":{
                              "gravity_scale":0.0,
                              "wind_scale":1.0,
                              "mass_scale":%s
                            }
                          }]
                        }
                        """.formatted(
                                suffix,
                                type,
                                Float.toString(massScale)
                        )).getAsJsonObject(),
                "environmental wind verification"
        );
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:wind_" + suffix,
                model,
                metadata
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        BoneModelSnapshot.Bone tail = model.bones().get("Tail");
        int drivenSlot = -1;
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).bone() == tail) {
                drivenSlot = layout.node(index).drivenSlot();
                break;
            }
        }
        require(drivenSlot >= 0, "Environmental wind fixture was not driven");
        return new WindFixture(
                tail,
                new SpringBoneSolver(layout),
                drivenSlot
        );
    }

    record WindFixture(
            BoneModelSnapshot.Bone tail,
            SpringBoneSolver solver,
            int drivenSlot
    ) {
    }

    record SiblingWindFixture(
            SpringBoneSolver solver,
            int firstSlot,
            int secondSlot
    ) {
    }

    record ChainWindFixture(SpringBoneSolver solver, int[] slots) {
    }
}
