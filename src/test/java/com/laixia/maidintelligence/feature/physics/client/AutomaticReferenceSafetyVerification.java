package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class AutomaticReferenceSafetyVerification {
    private AutomaticReferenceSafetyVerification() {
    }

    static void run() {
        verifiesDirectSelfReference();
        verifiesDrivenBoundsContributor();
    }

    private static void verifiesDirectSelfReference() {
        AnimatedGeoModel model = modelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.self_reference",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Head","parent":"Root","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]}
                  ]}]}
                """);
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {"schema_version":3,"mode":"explicit","chains":[{
                          "type":"HAIR","root":"Root/Head",
                          "constraints":{"simulation_space":"HEAD_LOCAL"}
                        }]}
                        """).getAsJsonObject(),
                "automatic self-reference safety"
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:self_reference",
                        model,
                        metadata
                )
        );
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if ("Head".equals(node.bone().getName())) {
                require(
                        node.constraint().collisionProxies().proxyCount() == 0,
                        "Automatic collider referenced its driven bone"
                );
                return;
            }
        }
        throw new AssertionError("Self-reference fixture lost driven Head");
    }

    private static void verifiesDrivenBoundsContributor() {
        AnimatedGeoModel model = modelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.bounds_reference",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Head","parent":"Root","pivot":[0,16,0]},
                    {"name":"Hair","parent":"Head","pivot":[0,20,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]}
                  ]}]}
                """);
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {"schema_version":3,"mode":"explicit","chains":[{
                          "type":"HAIR","root":"Root/Head/Hair",
                          "constraints":{"simulation_space":"HEAD_LOCAL"}
                        }]}
                        """).getAsJsonObject(),
                "automatic contributor safety"
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:bounds_reference",
                        model,
                        metadata
                )
        );
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if ("Hair".equals(node.bone().getName())) {
                require(
                        node.constraint().collisionProxies().proxyCount() == 0,
                        "Driven Head-bounds contributor became its own collider"
                );
                return;
            }
        }
        throw new AssertionError("Contributor fixture lost driven Hair");
    }

}
