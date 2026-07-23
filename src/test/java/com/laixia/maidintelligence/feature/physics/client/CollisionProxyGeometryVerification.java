package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.SecondaryMotionConstraint;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class CollisionProxyGeometryVerification {
    private CollisionProxyGeometryVerification() {
    }

    static void run() {
        AnimatedGeoModel model = modelFromJson("""
                {
                  "format_version":"1.12.0",
                  "minecraft:geometry":[{
                    "description":{"identifier":"geometry.detached_pivot",
                      "texture_width":64,"texture_height":64},
                    "bones":[
                      {"name":"Root","pivot":[0,0,0]},
                      {"name":"Body","parent":"Root","pivot":[0,8,0]},
                      {"name":"Head","parent":"Body","pivot":[0,16,0],
                       "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                      {"name":"HairTip","parent":"Head","pivot":[20,20,0],
                       "cubes":[{"origin":[3.8,16,-0.2],
                         "size":[0.4,4,0.4],"uv":[0,0]}]}
                    ]
                  }]
                }
                """);
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {"mode":"explicit","chains":[{
                          "id":"detached","type":"HAIR",
                          "root":"Root/Body/Head/HairTip",
                          "constraints":{"simulation_space":"HEAD_LOCAL",
                            "backstop":true,"head_collision":true}
                        }]}
                        """).getAsJsonObject(),
                "detached pivot verification"
        );
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:detached_pivot",
                model,
                metadata
        );
        AnimatedGeoBone hair = model.topLevelBones().get(0)
                .children().get(0).children().get(0).children().get(0);
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        SecondaryMotionConstraint constraint = null;
        Vector3f axis = new Vector3f();
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (node.bone() == hair) {
                constraint = node.constraint();
                node.axisInto(axis);
                break;
            }
        }
        require(constraint != null && constraint.hasHeadCollision(),
                "Detached-pivot head collider was not generated");
        float clearance = constraint.headClearance(
                axis, new Quaternionf(), new Vector3f(), new Vector3f());
        require(
                clearance > 0.03F && clearance < 0.20F,
                "Collider did not use the effective pivot: " + clearance
        );
    }
}
