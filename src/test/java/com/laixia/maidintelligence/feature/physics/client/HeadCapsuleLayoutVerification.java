package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySet;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class HeadCapsuleLayoutVerification {
    private HeadCapsuleLayoutVerification() {
    }

    static void run() {
        AnimatedGeoModel model = modelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.tall_head",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0]},
                    {"name":"Head","parent":"Body","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,14,-4],"size":[8,12,8],"uv":[0,0]}]},
                    {"name":"Hair","parent":"Head","pivot":[9,20,0],
                     "cubes":[{"origin":[9,16,-0.5],"size":[1,4,1],"uv":[0,0]}]}
                  ]}]}
                """);
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {"schema_version":3,"mode":"explicit","chains":[{
                          "type":"HAIR","root":"Root/Body/Head/Hair",
                          "constraints":{"simulation_space":"HEAD_LOCAL"}
                        }]}
                        """).getAsJsonObject(),
                "tall head capsule verification"
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:tall_head",
                        model,
                        metadata
                )
        );
        CollisionProxySet proxies = null;
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if ("Hair".equals(layout.node(index).bone().getName())) {
                proxies = layout.node(index).constraint().collisionProxies();
                break;
            }
        }
        require(
                proxies != null
                        && proxies.hasKind(CollisionProxyKind.CAPSULE)
                        && !proxies.hasKind(CollisionProxyKind.SPHERE),
                "Elongated Head did not replace Sphere with a short Capsule"
        );
    }
}
