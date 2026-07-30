package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.client.metadata.PhysicsMetadataJsonParser;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;

import java.util.Locale;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class SecondaryMotionFixture {
    private SecondaryMotionFixture() {
    }

    static Fixture create(
            float inwardDegrees,
            float rotationInertia,
            boolean backstop,
            boolean headCollision
    ) {
        BoneModelSnapshot model = coreModelFromJson("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {
                      "identifier": "geometry.secondary_constraint",
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
                      {"name":"HairTip","parent":"Head","pivot":[4,20,0],
                       "cubes":[{"origin":[3.8,16,-0.2],"size":[0.4,4,0.4],"uv":[0,0]}]}
                    ]
                  }]
                }
                """);
        String metadataJson = String.format(
                Locale.ROOT,
                """
                        {
                          "mode":"explicit",
                          "chains":[{
                            "id":"hair_tip",
                            "type":"HAIR",
                            "root":"Root/Body/Head/HairTip",
                            "profile":{"gravity_scale":0.0},
                            "constraints":{
                              "simulation_space":"HEAD_LOCAL",
                              "rotation_inertia_scale":%.4f,
                              "swing_limits":{
                                "left_degrees":60.0,
                                "right_degrees":60.0,
                                "outward_degrees":60.0,
                                "inward_degrees":%.4f
                              },
                              "backstop":%s,
                              "head_collision":%s
                            }
                          }]
                        }
                        """,
                rotationInertia,
                inwardDegrees,
                backstop,
                headCollision
        );
        PhysicsMetadata metadata = PhysicsMetadataJsonParser.parse(
                JsonParser.parseString(metadataJson).getAsJsonObject(),
                "secondary motion verification"
        );
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:secondary_motion",
                model,
                metadata
        );
        BoneModelSnapshot.Bone head = find(model, "Head");
        BoneModelSnapshot.Bone hair = find(model, "HairTip");
        require(
                hair != null
                        && Math.abs(
                        plan.decision(hair).constraints()
                                .swingLimits().inward()
                                - Math.toRadians(inwardDegrees)
                ) < 1.0E-5D,
                "Fixture constraint metadata was not applied"
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        PhysicsSolverLayout.Node hairNode = null;
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).bone() == hair) {
                hairNode = layout.node(index);
                break;
            }
        }
        require(head != null && hair != null, "Constraint fixture bones missing");
        require(hairNode != null && hairNode.driven(), "HairTip was not driven");
        return new Fixture(
                model,
                head,
                hair,
                layout,
                hairNode,
                new SpringBoneSolver(layout)
        );
    }

    static void resetPose(Fixture fixture, float headRotationZ) {
        resetPose(fixture, 0.0F, headRotationZ);
    }

    static void resetPose(
            Fixture fixture,
            float headRotationX,
            float headRotationZ
    ) {
        for (BoneModelSnapshot.Bone root : fixture.model().topLevelBones()) {
            resetBone(root);
        }
        fixture.head().setRotationX(headRotationX);
        fixture.head().setRotationZ(headRotationZ);
    }

    private static void resetBone(BoneModelSnapshot.Bone bone) {
        BoneModelSnapshot.RestPose initial = bone.getInitialSnapshot();
        bone.setRotationX(initial.rotationValueX);
        bone.setRotationY(initial.rotationValueY);
        bone.setRotationZ(initial.rotationValueZ);
        bone.setPositionX(0.0F);
        bone.setPositionY(0.0F);
        bone.setPositionZ(0.0F);
        for (BoneModelSnapshot.Bone child : bone.children()) {
            resetBone(child);
        }
    }

    private static BoneModelSnapshot.Bone find(
            BoneModelSnapshot model,
            String name
    ) {
        for (BoneModelSnapshot.Bone root : model.topLevelBones()) {
            BoneModelSnapshot.Bone found = find(root, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static BoneModelSnapshot.Bone find(
            BoneModelSnapshot.Bone bone,
            String name
    ) {
        if (bone.getName().equals(name)) {
            return bone;
        }
        for (BoneModelSnapshot.Bone child : bone.children()) {
            BoneModelSnapshot.Bone found = find(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    record Fixture(
            BoneModelSnapshot model,
            BoneModelSnapshot.Bone head,
            BoneModelSnapshot.Bone hair,
            PhysicsSolverLayout layout,
            PhysicsSolverLayout.Node hairNode,
            SpringBoneSolver solver
    ) {
    }
}
