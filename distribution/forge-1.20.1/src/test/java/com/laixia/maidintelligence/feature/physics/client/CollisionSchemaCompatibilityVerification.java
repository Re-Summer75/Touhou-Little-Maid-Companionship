package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class CollisionSchemaCompatibilityVerification {
    private CollisionSchemaCompatibilityVerification() {
    }

    static void run() {
        PhysicsSolverLayout legacy = layout(2);
        require(proxyCount(legacy, "HairA") == 0
                        && proxyCount(legacy, "HairB") == 0
                        && proxyCount(legacy, "Soft") == 0,
                "Schema 2 unexpectedly gained automatic collision");

        PhysicsSolverLayout current = layout(3);
        require(proxyCount(current, "HairA") > 0,
                "Schema 3 did not derive Head mesh collision for hair");
        require(proxyCount(current, "Soft") > 0,
                "Schema 3 did not enable automatic Body collision");
    }

    private static PhysicsSolverLayout layout(int schema) {
        AnimatedGeoModel model = modelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.schema_collision",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0],
                     "cubes":[{"origin":[-3,8,-2],"size":[6,8,4],"uv":[0,0]}]},
                    {"name":"Head","parent":"Body","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"HairA","parent":"Head","pivot":[8,20,0],
                     "cubes":[{"origin":[8,16,-.5],"size":[1,4,1],"uv":[0,0]}]},
                    {"name":"HairB","parent":"HairA","pivot":[9,16,0],
                     "cubes":[{"origin":[9,12,-.5],"size":[1,4,1],"uv":[0,0]}]},
                    {"name":"Soft","parent":"Body","pivot":[0,8,3],
                     "cubes":[{"origin":[-2,2,2],"size":[4,6,1],"uv":[0,0]}]}
                  ]}]}
                """);
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {"schema_version":%d,"mode":"explicit","chains":[
                          {"id":"hair","type":"HAIR",
                           "root":"Root/Body/Head/HairA",
                           "include_descendants":true,"constraints":{}},
                          {"id":"skirt","type":"SKIRT",
                           "root":"Root/Body/Soft","constraints":{}}
                        ]}
                        """.formatted(schema)).getAsJsonObject(),
                "schema collision compatibility"
        );
        return PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:schema_collision_" + schema,
                        model,
                        metadata
                )
        );
    }

    private static int proxyCount(
            PhysicsSolverLayout layout,
            String boneName
    ) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (boneName.equals(node.bone().getName())) {
                return node.constraint().collisionProxies().proxyCount();
            }
        }
        return -1;
    }
}
