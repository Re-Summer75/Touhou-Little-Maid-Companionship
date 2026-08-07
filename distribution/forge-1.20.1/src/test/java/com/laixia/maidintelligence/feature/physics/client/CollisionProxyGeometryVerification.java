package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadataJsonParser;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.layout.SecondaryMotionConstraint;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySource;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class CollisionProxyGeometryVerification {
    private CollisionProxyGeometryVerification() {
    }

    static void run() {
        BoneModelSnapshot model = coreModelFromJson("""
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
        PhysicsMetadata metadata = PhysicsMetadataJsonParser.parse(
                JsonParser.parseString("""
                        {"schema_version":3,"mode":"explicit","chains":[{
                          "id":"detached","type":"HAIR",
                          "root":"Root/Body/Head/HairTip",
                          "constraints":{"simulation_space":"HEAD_LOCAL",
                            "collision":{"auto":false,"proxies":[{
                              "kind":"sphere","reference":"Head",
                              "center":[0,20,0],"radius":4,"hit_radius":0
                            }]}}
                        }]}
                        """).getAsJsonObject(),
                "detached pivot verification"
        );
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:detached_pivot",
                model,
                metadata
        );
        BoneModelSnapshot.Bone hair = model.topLevelBones().get(0)
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
        require(constraint != null
                        && constraint.collisionProxies().hasKind(
                        CollisionProxyKind.SPHERE
                )
                        && constraint.collisionProxies().proxy(0).source()
                        == CollisionProxySource.EXPLICIT,
                "Detached-pivot explicit collider was not generated");
        float clearance = constraint.collisionProxies().clearance(
                constraint.collisionProxies().firstIndex(
                        CollisionProxyKind.SPHERE
                ),
                axis,
                new Quaternionf(),
                new CollisionScratch()
        );
        require(
                clearance > 0.03F && clearance < 0.20F,
                "Explicit collider did not use the effective pivot: "
                        + clearance
        );
    }
}
