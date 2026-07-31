package com.laixia.maidintelligence.feature.physics.client.motion;

import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.client.metadata.PhysicsMetadataJsonParser;
import com.laixia.maidintelligence.feature.physics.discovery.PhysicsBoneDiscoverer;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadata;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.TailAnimationContinuityTestFacade.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.TailAnimationContinuityTestFacade.require;

final class TailMotionVerificationSupport {
    static final float TAIL_PHASE_PER_TICK = 0.20F;
    static final float RENDER_FRAMES_PER_TICK = 6.0F;
    static final Vector3f MOTION = new Vector3f(0.35F, 0.0F, 0.20F);
    static final Vector3f NO_ENTITY_ACCELERATION = new Vector3f();
    static final Vector3f ENVIRONMENT_WIND =
            new Vector3f(0.045F, 0.0F, -0.015F);

    private TailMotionVerificationSupport() {
    }

    static float angularDistance(
            Quaternionf first,
            Quaternionf second
    ) {
        Quaternionf delta = new Quaternionf(first).conjugate().mul(second);
        float sine = (float) Math.sqrt(
                delta.x() * delta.x()
                        + delta.y() * delta.y()
                        + delta.z() * delta.z()
        );
        return 2.0F * (float) Math.atan2(sine, Math.abs(delta.w()));
    }

    static void requireNear(
            float actual,
            float expected,
            String channel
    ) {
        require(
                Math.abs(actual - expected) < 1.0E-6F,
                "Animation pose snapshot lost " + channel + ": "
                        + actual + " != " + expected
        );
    }

    static Fixture createFixture() {
        BoneModelSnapshot model = coreModelFromJson("""
                {
                  "format_version":"1.12.0",
                  "minecraft:geometry":[{
                    "description":{
                      "identifier":"geometry.tail_continuity",
                      "texture_width":32,
                      "texture_height":32
                    },
                    "bones":[
                      {"name":"Root","pivot":[0,0,0]},
                      {"name":"Body","parent":"Root","pivot":[0,8,0],
                       "cubes":[{"origin":[-3,0,-2],"size":[6,8,4],"uv":[0,0]}]},
                      {"name":"MTail","parent":"Body","pivot":[0,7,2]},
                      {"name":"Tail","parent":"MTail","pivot":[0,7,2],
                       "cubes":[{"origin":[-.5,1,1.5],"size":[1,6,1],"uv":[0,0]}]}
                    ]
                  }]
                }
                """);
        PhysicsMetadata metadata = PhysicsMetadataJsonParser.parse(
                JsonParser.parseString("""
                        {
                          "mode":"explicit",
                          "chains":[{
                            "id":"hardcoded_tail",
                            "type":"TAIL",
                            "root":"Root/Body/MTail/Tail",
                            "profile":{"gravity_scale":0.7},
                            "constraints":{
                              "simulation_space":"MODEL",
                              "rotation_inertia_scale":0.5,
                              "swing_limits":{
                                "left_degrees":70.0,
                                "right_degrees":70.0,
                                "outward_degrees":70.0,
                                "inward_degrees":70.0
                              },
                              "backstop":false,
                              "head_collision":false
                            }
                          }]
                        }
                        """).getAsJsonObject(),
                "tail animation continuity verification"
        );
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:tail_continuity",
                model,
                metadata
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        BoneModelSnapshot.Bone tail = model.bones().get("Tail");
        boolean driven = false;
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).bone() == tail) {
                driven = layout.node(index).driven();
                break;
            }
        }
        require(driven, "Tail continuity fixture was not driven");
        return new Fixture(tail, new SpringBoneSolver(layout));
    }

    record Fixture(
            BoneModelSnapshot.Bone tail,
            SpringBoneSolver solver
    ) {
    }
}
