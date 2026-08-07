package com.laixia.maidintelligence.feature.physics.client.collision;

import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadataJsonParser;
import com.laixia.maidintelligence.feature.physics.discovery.PhysicsBoneDiscoverer;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySource;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadata;
import org.joml.Vector3f;

import java.util.Set;

import static com.laixia.maidintelligence.feature.physics.client.MeshCollisionTestFacade.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.MeshCollisionTestFacade.loadCoreModel;
import static com.laixia.maidintelligence.feature.physics.client.MeshCollisionTestFacade.require;
import static com.laixia.maidintelligence.feature.physics.client.MeshCollisionTestFacade.restHalfExtents;
import static com.laixia.maidintelligence.feature.physics.client.collision.MeshCollisionScenarioSupport.indexOf;
import static com.laixia.maidintelligence.feature.physics.client.collision.MeshCollisionScenarioSupport.layout;
import static com.laixia.maidintelligence.feature.physics.client.collision.MeshCollisionScenarioSupport.proxies;
import static com.laixia.maidintelligence.feature.physics.client.collision.MeshCollisionScenarioSupport.references;

/**
 * Verifies mesh collider reachability, culling, and cube fidelity.
 */
public final class MeshCollisionReachabilityCullingVerification {
    /** Hand-picked shipped models: a plain one and a heavily layered one. */
    private static final String[] AUDITED = {
            "winefox.json",
            "zhiban_hanfu.json"
    };

    private MeshCollisionReachabilityCullingVerification() {
    }

    public static void run() {
        verifiesReachablePruning();
        verifiesEveryReachableCubeIsKept();
        verifiesAutoOptOut();
        verifiesRigidReferencesOnly();
        verifiesFlatOverlaysAreIgnored();
    }

    public static void runCubeAudit() throws Exception {
        for (String model : AUDITED) {
            verifiesEveryBoxIsAnActualCube(model);
        }
    }

    /**
     * The collider must be one cube, never a hull over several of them: a
     * bone-wide hull would block the empty air between scattered decorations.
     */
    private static void verifiesEveryBoxIsAnActualCube(String fileName)
            throws Exception {
        BoneModelSnapshot model = loadCoreModel(fileName);
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:mesh_cube_" + fileName,
                        model,
                        PhysicsMetadata.EMPTY
                )
        );
        PhysicsBoneGeometry.Analysis geometry =
                PhysicsBoneGeometry.analyze(model);
        int checked = 0;
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (!node.driven()) {
                continue;
            }
            CollisionProxySet proxies = node.constraint().collisionProxies();
            for (int proxy = 0; proxy < proxies.proxyCount(); proxy++) {
                if (proxies.proxy(proxy).kind() != CollisionProxyKind.BOX
                        || proxies.proxy(proxy).source()
                        == CollisionProxySource.LAYER) {
                    continue;
                }
                int reference = proxies.proxy(proxy).referenceNodeIndex();
                require(reference >= 0, fileName + " box lost its reference");
                Vector3f half = restHalfExtents(proxies.proxy(proxy));
                require(
                        matchesCube(geometry, layout, reference, half),
                        fileName + " box on "
                                + layout.node(reference).bone().getName()
                                + " does not match any cube: " + half
                );
                checked++;
            }
        }
        require(checked > 0, fileName + " produced no mesh boxes to check");
    }

    private static boolean matchesCube(
            PhysicsBoneGeometry.Analysis geometry,
            PhysicsSolverLayout layout,
            int reference,
            Vector3f half
    ) {
        PhysicsBoneGeometry.Node node =
                geometry.node(layout.node(reference).bone());
        if (node == null) {
            return false;
        }
        for (PhysicsBoneGeometry.CubeBox cube : node.cubeBoxes()) {
            if (cube.half().distance(half) <= 1.0E-4F) {
                return true;
            }
        }
        return false;
    }

    /**
     * Flat overlays such as emissive layers or magic circles are geometry the
     * endpoint would cross in one step, and they are often model-sized.
     */
    private static void verifiesFlatOverlaysAreIgnored() {
        BoneModelSnapshot model = coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.flat_overlay",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0],
                     "cubes":[{"origin":[-4,8,-2],"size":[8,8,4],"uv":[0,0]}]},
                    {"name":"Glow","parent":"Body","pivot":[0,16,0],
                     "cubes":[{"origin":[-16,4,-1],"size":[32,32,0],
                       "uv":[0,0]}]},
                    {"name":"Head","parent":"Body","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"Hair","parent":"Head","pivot":[5,23,0],
                     "cubes":[{"origin":[5,19,-1],"size":[1,4,2],"uv":[0,0]}]}
                  ]}]}
                """);
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:flat_overlay",
                        model,
                        PhysicsMetadataJsonParser.parse(
                                JsonParser.parseString("""
                                        {"schema_version":3,"mode":"explicit",
                                         "chains":[{"type":"HAIR",
                                          "root":"Root/Body/Head/Hair"}]}
                                        """).getAsJsonObject(),
                                "flat overlay verification"
                        )
                )
        );
        CollisionProxySet proxies = proxies(layout, "Hair");
        require(proxies.proxyCount() > 0, "Hair lost its mesh collision");
        require(
                !references(layout, proxies).contains("Glow"),
                "A flat overlay became a collider"
        );
    }

    /**
     * Nearby rigid bones become colliders, distant ones must not: every extra
     * proxy is per-frame work for a contact that can never happen.
     */
    private static void verifiesReachablePruning() {
        PhysicsSolverLayout layout = layout(true);
        CollisionProxySet proxies = proxies(layout, "Hair");
        require(
                proxies.proxyCount() > 0,
                "Hair mesh collision was empty"
        );
        for (int index = 0; index < proxies.proxyCount(); index++) {
            require(
                    proxies.proxy(index).kind() == CollisionProxyKind.BOX,
                    "Mesh collision produced a fitted shape"
            );
        }
        Set<String> references = references(layout, proxies);
        require(
                references.contains("Head"),
                "Hair did not collide with the Head mesh: " + references
        );
        require(
                !references.contains("Lantern"),
                "An unreachable rigid bone became a collider: " + references
        );
    }

    /**
     * Selection is not capped: a bone built from many cubes contributes every
     * cube the strand can reach, so collision follows the mesh instead of an
     * arbitrary subset of it.
     */
    private static void verifiesEveryReachableCubeIsKept() {
        StringBuilder cubes = new StringBuilder();
        for (int index = 0; index < 8; index++) {
            cubes.append(index == 0 ? "" : ",")
                    .append("{\"origin\":[-4,")
                    .append(16 + index)
                    .append(",-4],\"size\":[8,1,8],\"uv\":[0,0]}");
        }
        BoneModelSnapshot model = coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.stacked_head",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0],
                     "cubes":[{"origin":[-4,8,-2],"size":[8,8,4],"uv":[0,0]}]},
                    {"name":"Head","parent":"Body","pivot":[0,16,0],
                     "cubes":[%s]},
                    {"name":"Hair","parent":"Head","pivot":[5,23,0],
                     "cubes":[{"origin":[5,15,-1],"size":[1,8,2],"uv":[0,0]}]}
                  ]}]}
                """.formatted(cubes));
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:stacked_head",
                        model,
                        PhysicsMetadataJsonParser.parse(
                                JsonParser.parseString("""
                                        {"schema_version":3,"mode":"explicit",
                                         "chains":[{"type":"HAIR",
                                          "root":"Root/Body/Head/Hair"}]}
                                        """).getAsJsonObject(),
                                "stacked head verification"
                        )
                )
        );
        CollisionProxySet proxies = proxies(layout, "Hair");
        int fromHead = 0;
        int head = indexOf(layout, "Head");
        for (int index = 0; index < proxies.proxyCount(); index++) {
            fromHead += proxies.proxy(index).referenceNodeIndex() == head
                    ? 1
                    : 0;
        }
        require(
                fromHead == 8,
                "Stacked head cubes were capped instead of fully attached: "
                        + fromHead
        );
    }

    private static void verifiesAutoOptOut() {
        require(
                proxies(layout(false), "Hair").proxyCount() == 0,
                "collision.auto:false still generated mesh collision"
        );
    }

    /**
     * The mesh pass only ever picks rigid bones. A driven bone can still
     * become a collider, but only through the cloth-layer pass, which this
     * single-chain fixture never triggers.
     */
    private static void verifiesRigidReferencesOnly() {
        PhysicsSolverLayout layout = layout(true);
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (!node.driven()) {
                continue;
            }
            CollisionProxySet proxies = node.constraint().collisionProxies();
            for (int proxy = 0; proxy < proxies.proxyCount(); proxy++) {
                int reference = proxies.proxy(proxy).referenceNodeIndex();
                require(
                        reference < 0 || !layout.node(reference).driven(),
                        "A driven bone was used as a collider reference"
                );
            }
        }
    }
}
