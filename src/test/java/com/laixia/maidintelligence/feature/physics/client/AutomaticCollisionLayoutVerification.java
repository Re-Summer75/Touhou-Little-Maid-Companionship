package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySet;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class AutomaticCollisionLayoutVerification {
    private AutomaticCollisionLayoutVerification() {
    }

    static void run() {
        verifiesHeadCollisionDisabled();
        verifiesBodyStrategies();
    }

    private static void verifiesHeadCollisionDisabled() {
        AnimatedGeoModel model = modelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.head_chain",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0]},
                    {"name":"Head","parent":"Body","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"HairA","parent":"Head","pivot":[20,20,0],
                     "cubes":[{"origin":[20,16,-0.5],"size":[1,4,1],"uv":[0,0]}]},
                    {"name":"HairB","parent":"HairA","pivot":[21,16,0],
                     "cubes":[{"origin":[21,12,-0.5],"size":[1,4,1],"uv":[0,0]}]}
                  ]}]}
                """);
        PhysicsSolverLayout layout = layout(
                model,
                "HAIR",
                "Root/Body/Head/HairA",
                "HEAD_LOCAL"
        );
        for (String name : new String[]{"HairA", "HairB"}) {
            CollisionProxySet proxies = node(layout, name)
                    .constraint().collisionProxies();
            require(
                    proxies.proxyCount() == 0,
                    name + " unexpectedly received automatic Head collision"
            );
        }
        require(
                layout.referencesPreordered()
                        && indexOf(layout, "Head") < indexOf(layout, "HairA"),
                "HEAD_LOCAL orientation reference was not retained"
        );
    }

    private static void verifiesBodyStrategies() {
        PhysicsSolverLayout skirt = bodyLayout("SKIRT", "AUTO");
        CollisionProxySet skirtProxies = node(skirt, "Soft")
                .constraint().collisionProxies();
        require(
                skirtProxies.proxyCount() == 1
                        && count(skirtProxies, CollisionProxyKind.CAPSULE) == 1,
                "SKIRT did not retain only its Body Capsule"
        );
        int skirtIndex = indexOf(skirt, "Soft");
        for (int proxy = 0; proxy < skirtProxies.proxyCount(); proxy++) {
            require(
                    skirtProxies.proxy(proxy).referenceNodeIndex() < skirtIndex,
                    "SKIRT reference was not ordered before its driven bone"
            );
        }
        require(
                indexOf(skirt, "Body") >= 0
                        && indexOf(skirt, "LeftLeg") < 0
                        && indexOf(skirt, "RightLeg") < 0
                        && skirt.referencesPreordered(),
                "Disabled Leg references leaked into the active layout"
        );

        CollisionProxySet cape = node(
                bodyLayout("CAPE", "AUTO"),
                "Soft"
        ).constraint().collisionProxies();
        require(
                cape.proxyCount() == 2
                        && count(cape, CollisionProxyKind.CAPSULE) == 1
                        && count(cape, CollisionProxyKind.PLANE) == 1,
                "CAPE did not receive Body Capsule + Back Plane"
        );

        CollisionProxySet ribbon = node(
                bodyLayout("RIBBON", "BODY_LOCAL"),
                "Soft"
        ).constraint().collisionProxies();
        require(
                ribbon.proxyCount() == 1
                        && ribbon.hasKind(CollisionProxyKind.CAPSULE),
                "BODY_LOCAL RIBBON did not receive a Body Capsule"
        );
    }

    private static PhysicsSolverLayout bodyLayout(
            String type,
            String space
    ) {
        AnimatedGeoModel model = modelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.body_layout",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0],
                     "cubes":[{"origin":[-3,8,-2],"size":[6,8,4],"uv":[0,0]}]},
                    {"name":"Head","parent":"Body","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"Soft","parent":"Body","pivot":[0,8,3],
                     "cubes":[{"origin":[-2,2,2],"size":[4,6,1],"uv":[0,0]}]},
                    {"name":"LeftLeg","parent":"Body","pivot":[2,8,0],
                     "cubes":[{"origin":[1,2,-1.5],"size":[2,6,3],"uv":[0,0]}]},
                    {"name":"LeftFoot","parent":"LeftLeg","pivot":[2,2,0],
                     "cubes":[{"origin":[1,0,-2],"size":[2,2,4],"uv":[0,0]}]},
                    {"name":"RightLeg","parent":"Body","pivot":[-2,8,0],
                     "cubes":[{"origin":[-3,2,-1.5],"size":[2,6,3],"uv":[0,0]}]},
                    {"name":"RightFoot","parent":"RightLeg","pivot":[-2,2,0],
                     "cubes":[{"origin":[-3,0,-2],"size":[2,2,4],"uv":[0,0]}]}
                  ]}]}
                """);
        return layout(model, type, "Root/Body/Soft", space);
    }

    private static PhysicsSolverLayout layout(
            AnimatedGeoModel model,
            String type,
            String root,
            String space
    ) {
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {"schema_version":3,"mode":"explicit","chains":[{
                          "id":"collision","type":"%s","root":"%s",
                          "constraints":{"simulation_space":"%s"}}]}
                        """.formatted(type, root, space)).getAsJsonObject(),
                "automatic collision layout verification"
        );
        return PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:auto_collision",
                        model,
                        metadata
                )
        );
    }

    private static PhysicsSolverLayout.Node node(
            PhysicsSolverLayout layout,
            String name
    ) {
        int index = indexOf(layout, name);
        require(index >= 0, "Missing active layout node " + name);
        return layout.node(index);
    }

    private static int indexOf(PhysicsSolverLayout layout, String name) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (name.equals(layout.node(index).bone().getName())) return index;
        }
        return -1;
    }

    private static int count(CollisionProxySet proxies, CollisionProxyKind kind) {
        int count = 0;
        for (int index = 0; index < proxies.proxyCount(); index++) {
            if (proxies.proxy(index).kind() == kind) count++;
        }
        return count;
    }
}
