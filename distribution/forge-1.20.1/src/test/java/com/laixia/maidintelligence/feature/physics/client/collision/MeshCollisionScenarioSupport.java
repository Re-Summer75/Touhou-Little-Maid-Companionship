package com.laixia.maidintelligence.feature.physics.client.collision;

import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadataJsonParser;
import com.laixia.maidintelligence.feature.physics.discovery.PhysicsBoneDiscoverer;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadata;

import java.util.HashSet;
import java.util.Set;

import static com.laixia.maidintelligence.feature.physics.client.MeshCollisionTestFacade.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.MeshCollisionTestFacade.require;

final class MeshCollisionScenarioSupport {
    private MeshCollisionScenarioSupport() {
    }

    static PhysicsSolverLayout layout(boolean auto) {
        BoneModelSnapshot model = model();
        return PhysicsSolverLayout.build(model, plan(model, auto));
    }

    static PhysicsBoneSelectionPlan plan(
            BoneModelSnapshot model,
            boolean auto
    ) {
        PhysicsMetadata metadata = PhysicsMetadataJsonParser.parse(
                JsonParser.parseString("""
                        {"schema_version":3,"mode":"explicit","chains":[{
                          "id":"mesh_collision","type":"HAIR",
                          "root":"Root/Body/Head/Hair",
                          "constraints":{"simulation_space":"HEAD_LOCAL",
                            "collision":{"auto":%b}}}]}
                        """.formatted(auto)).getAsJsonObject(),
                "mesh collision verification"
        );
        return PhysicsBoneDiscoverer.discover(
                "verification:mesh_collision_" + auto,
                model,
                metadata
        );
    }

    /**
     * A skull with a strand hanging beside it, torso and arms in reach, plus
     * a lantern far off to the side that must stay out of the plan.
     */
    static BoneModelSnapshot model() {
        return coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.mesh_collision",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0],
                     "cubes":[{"origin":[-4,8,-2],"size":[8,8,4],"uv":[0,0]}]},
                    {"name":"LeftArm","parent":"Body","pivot":[5,15,0],
                     "cubes":[{"origin":[4,8,-2],"size":[3,8,4],"uv":[0,0]}]},
                    {"name":"Head","parent":"Body","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"Hair","parent":"Head","pivot":[5,23,0],
                     "cubes":[{"origin":[5,19,-1],"size":[1,4,2],"uv":[0,0]}]},
                    {"name":"Lantern","parent":"Root","pivot":[40,8,0],
                     "cubes":[{"origin":[38,6,-2],"size":[4,6,4],"uv":[0,0]}]}
                  ]}]}
                """);
    }

    static CollisionProxySet proxies(
            PhysicsSolverLayout layout,
            String name
    ) {
        int index = indexOf(layout, name);
        require(index >= 0, "Missing active layout node " + name);
        return layout.node(index).constraint().collisionProxies();
    }

    static Set<String> references(
            PhysicsSolverLayout layout,
            CollisionProxySet proxies
    ) {
        Set<String> names = new HashSet<>();
        for (int index = 0; index < proxies.proxyCount(); index++) {
            int reference = proxies.proxy(index).referenceNodeIndex();
            if (reference >= 0 && reference < layout.activeNodeCount()) {
                names.add(layout.node(reference).bone().getName());
            }
        }
        return names;
    }

    static int indexOf(PhysicsSolverLayout layout, String name) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (name.equals(layout.node(index).bone().getName())) {
                return index;
            }
        }
        return -1;
    }

    static BoneModelSnapshot.Bone bone(
            BoneModelSnapshot model,
            String name
    ) {
        BoneModelSnapshot.Bone bone = model.bones().get(name);
        require(bone != null, "Missing bone " + name);
        return bone;
    }
}
