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
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySource;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProxyDebugData;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Covers the mesh-derived collision boxes: which rigid cubes become
 * colliders, that reachable ones are kept in full, and that they actually
 * block a driven endpoint at runtime.
 */
final class MeshCollisionVerification {
    /** Swings the strand sideways until it is buried in the skull. */
    private static final float SWING = 0.9F;
    /** Swings the strand until it just meets the skull surface. */
    private static final float CONTACT = 0.30F;
    /** Frame acceleration that throws the strand toward the skull. */
    private static final Vector3f INWARD = new Vector3f(-2.0F, 0.0F, 0.0F);
    /** Hand-picked shipped models: a plain one and a heavily layered one. */
    private static final String[] AUDITED = {
            "winefox.json",
            "zhiban_hanfu.json"
    };

    private MeshCollisionVerification() {
    }

    static void run() throws Exception {
        verifiesReachablePruning();
        verifiesEveryReachableCubeIsKept();
        verifiesAutoOptOut();
        verifiesRigidReferencesOnly();
        verifiesFlatOverlaysAreIgnored();
        verifiesEndpointStopsAtHeadSurface();
        verifiesAuthoredOverlapIsNotForcedApart();
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
        BoneModelSnapshot model = coreModel(
                BonePhysicsVerificationSupport.loadGeoModel(
                        BonePhysicsVerificationSupport.MODEL_DIRECTORY
                                .resolve(fileName)
                )
        );
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
                Vector3f half = MeshCollisionSupport.restHalfExtents(
                        proxies.proxy(proxy)
                );
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

    /**
     * Rests the strand against the skull and then blows it inward with frame
     * acceleration. Secondary motion is exactly what collision exists to stop,
     * so the mesh box has to hold the endpoint on the surface.
     *
     * <p>Where the surface is, is checked exactly inside {@code driveIntoHead}.
     * This adds the end-to-end half: that the clearance reached the written
     * pose rather than being computed and dropped. The margin is small on
     * purpose — the endpoint radius is a tolerance now, not a thickness, so the
     * box no longer stands a ring off its own cube and the only swing taken
     * back is the part that truly sank in. A collider that stopped working
     * entirely still lands on {@code free} and is caught.
     */
    private static void verifiesEndpointStopsAtHeadSurface() {
        float free = driveIntoHead(false);
        require(
                free > 0.10F,
                "The uncollided strand did not swing into the head: " + free
        );
        float blocked = driveIntoHead(true);
        require(
                blocked < free - 0.01F,
                "Mesh collision did not hold the strand back: " + blocked
                        + " against a free swing of " + free
        );
    }

    /**
     * An animation is free to overlap its own mesh: a seated pose folds legs
     * through a hem, an embrace presses two bodies together, and no authored
     * frame owes the collider anything. Enforcing the surface in absolute
     * terms would fight the animation every frame, so the constraint is
     * relative — the authored depth is kept, and only what physics adds on top
     * is rejected.
     */
    private static void verifiesAuthoredOverlapIsNotForcedApart() {
        BoneModelSnapshot model = model();
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                plan(model, true)
        );
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        BoneModelSnapshot.Bone hair = bone(model, "Hair");
        float minimumSettled = Float.POSITIVE_INFINITY;
        float maximumSettled = Float.NEGATIVE_INFINITY;
        for (int frame = 0; frame < 120; frame++) {
            solver.restoreAnimationPose();
            hair.setRotationZ(SWING);
            solver.solve(new Vector3f(), 0.0F, 1.0F / 60.0F, false);
            if (frame >= 90) {
                minimumSettled = Math.min(
                        minimumSettled,
                        hair.getRotationZ()
                );
                maximumSettled = Math.max(
                        maximumSettled,
                        hair.getRotationZ()
                );
            }
        }
        // Gravity trims the settled angle slightly below the authored swing.
        require(
                hair.getRotationZ() > SWING - 0.15F,
                "Collision forced the authored overlap apart: "
                        + hair.getRotationZ()
        );
        require(
                maximumSettled - minimumSettled < 0.01F,
                "The authored overlap buzzed against the collider: "
                        + minimumSettled + ".." + maximumSettled
        );

        // Physics on top of that overlap must still find a surface.
        for (int frame = 0; frame < 120; frame++) {
            solver.restoreAnimationPose();
            hair.setRotationZ(SWING);
            solver.solve(INWARD, 0.0F, 1.0F / 60.0F, false);
        }
        requireSurfaceContact(solver, indexOf(layout, "Hair"));
    }

    /** Returns the settled swing angle after driving the strand inward. */
    private static float driveIntoHead(boolean auto) {
        BoneModelSnapshot model = model();
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                plan(model, auto)
        );
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        // Calibrate the rest allowance on the untouched authored pose first.
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);

        BoneModelSnapshot.Bone hair = bone(model, "Hair");
        for (int frame = 0; frame < 90; frame++) {
            solver.restoreAnimationPose();
            hair.setRotationZ(CONTACT);
            solver.solve(INWARD, 0.0F, 1.0F / 30.0F, false);
        }
        if (auto) {
            requireSurfaceContact(solver, indexOf(layout, "Hair"));
        }
        return hair.getRotationZ();
    }

    private static void requireSurfaceContact(
            SpringBoneSolver solver,
            int node
    ) {
        int proxyCount = solver.preparedProxyCount(node);
        require(proxyCount > 0, "Hair lost its prepared mesh collision");
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        float nearest = Float.MAX_VALUE;
        for (int proxy = 0; proxy < proxyCount; proxy++) {
            require(
                    solver.copyPreparedCollisionProxy(node, proxy, data),
                    "Prepared mesh collision was unavailable"
            );
            require(
                    data.clearance >= -1.0E-4F,
                    "Driven endpoint sank into the mesh collider: "
                            + data.clearance
            );
            nearest = Math.min(nearest, data.clearance);
        }
        /*
         * Contact is the sheet's surface meeting the face, so the endpoint on its
         * axis stops half a thickness short of it rather than on it. The tolerance
         * is that half thickness: this strand is 2 px through, and requiring the
         * axis itself to land on the face is requiring the visible surface to be
         * halfway inside.
         */
        float halfThickness = 1.0F / 16.0F;
        require(
                nearest <= halfThickness + 1.0E-3F,
                "The strand never reached the mesh collider: " + nearest
        );
    }

    private static PhysicsSolverLayout layout(boolean auto) {
        BoneModelSnapshot model = model();
        return PhysicsSolverLayout.build(model, plan(model, auto));
    }

    private static PhysicsBoneSelectionPlan plan(
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
    private static BoneModelSnapshot model() {
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

    private static CollisionProxySet proxies(
            PhysicsSolverLayout layout,
            String name
    ) {
        int index = indexOf(layout, name);
        require(index >= 0, "Missing active layout node " + name);
        return layout.node(index).constraint().collisionProxies();
    }

    private static Set<String> references(
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

    private static int indexOf(PhysicsSolverLayout layout, String name) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (name.equals(layout.node(index).bone().getName())) {
                return index;
            }
        }
        return -1;
    }

    private static BoneModelSnapshot.Bone bone(
            BoneModelSnapshot model,
            String name
    ) {
        BoneModelSnapshot.Bone bone = model.bones().get(name);
        require(bone != null, "Missing bone " + name);
        return bone;
    }
}
