package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;

import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadataJsonParser;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProxyDebugData;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Covers collision between two physics-driven cloth panels: which stacks are
 * recognised, that the order is one-way, and that the collider follows the
 * inner panel's solved pose rather than its animation.
 */
final class ClothLayerCollisionVerification {
    private static final float SWING = 0.8F;
    /**
     * Layers only ever drift a few degrees apart. Beyond that the covering
     * panel clears the lining entirely instead of pressing into it, which is
     * a crossing no endpoint constraint can undo.
     */
    private static final float LEAN = 0.25F;
    private static final float STEP = 1.0F / 30.0F;
    private static final float CROSSING_SWING = 1.15F;

    private ClothLayerCollisionVerification() {
    }

    static void run() {
        verifiesApronTakesSkirtAsLining();
        verifiesNearestLiningChainWins();
        verifiesCoplanarPanelsAreNotStacked();
        verifiesWrappedPanelsHaveNoOrder();
        verifiesOuterPanelStopsAtInnerPanel();
        verifiesColliderFollowsSolvedPose();
        verifiesCrossedLayersReturnToRest();
    }

    /** The stack is recognised, and only the outer panel is constrained. */
    private static void verifiesApronTakesSkirtAsLining() {
        PhysicsSolverLayout layout = layout(stackedModel());
        Set<String> apron = references(layout, "Apron");
        require(
                apron.contains("Skirt"),
                "The apron did not take the skirt as lining: " + apron
        );
        require(
                !references(layout, "Skirt").contains("Apron"),
                "The lining was pushed by the panel covering it"
        );
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (!node.driven()) {
                continue;
            }
            CollisionProxySet proxies = node.constraint().collisionProxies();
            for (int slot = 0; slot < proxies.proxyCount(); slot++) {
                require(
                        proxies.proxy(slot).kind() == CollisionProxyKind.BOX
                                || proxies.proxy(slot).referenceNodeIndex() < 0
                                || !layout.node(
                                proxies.proxy(slot).referenceNodeIndex()
                        ).driven(),
                        "A driven reference produced a fitted shape"
                );
            }
        }
    }

    /**
     * One broad cover may geometrically overlap several adjacent skirt chains,
     * but only the chain directly behind it may constrain that plate.
     */
    private static void verifiesNearestLiningChainWins() {
        BoneModelSnapshot model = coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.multi_lining",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0],
                     "cubes":[{"origin":[-4,8,-2],"size":[8,8,4],"uv":[0,0]}]},
                    {"name":"LeftSkirt","parent":"Body","pivot":[-4,12,-2],
                     "cubes":[{"origin":[-6,4,-2.5],"size":[4,8,1],
                       "uv":[0,0]}]},
                    {"name":"MiddleSkirt","parent":"Body","pivot":[0,12,-2],
                     "cubes":[{"origin":[-2,4,-2.5],"size":[4,8,1],
                       "uv":[0,0]}]},
                    {"name":"RightSkirt","parent":"Body","pivot":[4,12,-2],
                     "cubes":[{"origin":[2,4,-2.5],"size":[4,8,1],
                       "uv":[0,0]}]},
                    {"name":"Apron","parent":"Body","pivot":[0,12,-3],
                     "cubes":[{"origin":[-6,4,-3.5],"size":[12,8,1],
                       "uv":[0,0]}]}
                  ]}]}
                """);
        PhysicsMetadata metadata = PhysicsMetadataJsonParser.parse(
                JsonParser.parseString("""
                        {"schema_version":3,"mode":"explicit","chains":[
                          {"id":"left","type":"SKIRT",
                           "root":"Root/Body/LeftSkirt"},
                          {"id":"middle","type":"SKIRT",
                           "root":"Root/Body/MiddleSkirt"},
                          {"id":"right","type":"SKIRT",
                           "root":"Root/Body/RightSkirt"},
                          {"id":"cover","type":"SKIRT",
                           "root":"Root/Body/Apron"}]}
                        """).getAsJsonObject(),
                "nearest cloth lining verification"
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:nearest_cloth_lining",
                        model,
                        metadata
                )
        );
        Set<String> references = references(layout, "Apron");
        require(
                references.contains("MiddleSkirt")
                        && !references.contains("LeftSkirt")
                        && !references.contains("RightSkirt"),
                "Broad apron selected competing lining chains: " + references
        );
    }

    /**
     * Flaps sitting side by side in one plane meet along an edge. They are the
     * same sheet split across bones and have no layer order.
     */
    private static void verifiesCoplanarPanelsAreNotStacked() {
        PhysicsSolverLayout layout = layout(coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.coplanar_panels",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0],
                     "cubes":[{"origin":[-4,8,-2],"size":[8,8,4],"uv":[0,0]}]},
                    {"name":"Skirt","parent":"Body","pivot":[-1,12,-2],
                     "cubes":[{"origin":[-5,4,-2.5],"size":[5,8,1],
                       "uv":[0,0]}]},
                    {"name":"Apron","parent":"Body","pivot":[1,12,-2],
                     "cubes":[{"origin":[0,4,-2.5],"size":[5,8,1],
                       "uv":[0,0]}]}
                  ]}]}
                """));
        require(
                !references(layout, "Apron").contains("Skirt")
                        && !references(layout, "Skirt").contains("Apron"),
                "Coplanar flaps were treated as a stack"
        );
    }

    /**
     * A panel that wraps far enough is in front of its neighbour on one cube
     * and behind it on another. Letting either push would leave both trading
     * corrections every frame, so the pair is dropped in both directions.
     */
    private static void verifiesWrappedPanelsHaveNoOrder() {
        PhysicsSolverLayout layout = layout(coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.wrapped_panels",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0],
                     "cubes":[{"origin":[-4,8,-2],"size":[8,8,4],"uv":[0,0]}]},
                    {"name":"Skirt","parent":"Body","pivot":[0,12,-2],
                     "cubes":[{"origin":[-4,4,-2.5],"size":[8,8,1],
                       "uv":[0,0]}]},
                    {"name":"Apron","parent":"Body","pivot":[0,12,-3],
                     "cubes":[{"origin":[-4,4,-3.5],"size":[8,8,1],
                       "uv":[0,0]},
                      {"origin":[-4,4,-1.5],"size":[8,8,1],"uv":[0,0]}]}
                  ]}]}
                """));
        require(
                !references(layout, "Apron").contains("Skirt")
                        && !references(layout, "Skirt").contains("Apron"),
                "A panel wrapping both sides of its neighbour still picked a"
                        + " layer order"
        );
    }

    /**
     * Drives the apron backwards into the skirt. Without the layer collider
     * the animation carries it straight through the panel behind it.
     */
    private static void verifiesOuterPanelStopsAtInnerPanel() {
        float free = driveIntoLining(false);
        require(
                free < -LEAN * 0.5F,
                "The unconstrained apron did not follow the animation into the"
                        + " skirt: " + free
        );
        float blocked = driveIntoLining(true);
        require(
                blocked > free + 0.05F,
                "The layer collider did not hold the apron out: " + blocked
        );
    }

    private static float driveIntoLining(boolean auto) {
        BoneModelSnapshot model = stackedModel();
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                plan(model, auto)
        );
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        BoneModelSnapshot.Bone apron = bone(model, "Apron");
        for (int frame = 0; frame < 60; frame++) {
            solver.restoreAnimationPose();
            apron.setRotationX(-LEAN);
            solver.solve(new Vector3f(), 0.0F, STEP, false);
        }
        if (auto) {
            requireNoPenetration(solver, indexOf(layout, "Apron"));
        }
        return apron.getRotationX();
    }

    private static void requireNoPenetration(
            SpringBoneSolver solver,
            int node
    ) {
        int count = solver.preparedProxyCount(node);
        require(count > 0, "The apron lost its prepared layer collision");
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        for (int proxy = 0; proxy < count; proxy++) {
            require(
                    solver.copyPreparedCollisionProxy(node, proxy, data),
                    "Prepared layer collision was unavailable"
            );
            require(
                    data.clearance >= -1.0E-4F,
                    "The apron sank into its lining: " + data.clearance
            );
        }
    }

    /**
     * The lining moves too, so its collider has to follow the pose the solver
     * wrote and not the animation. Snapping the animation back to rest leaves
     * the two apart for exactly one frame: physics still holds the swing while
     * the bone already reads as unrotated.
     */
    private static void verifiesColliderFollowsSolvedPose() {
        BoneModelSnapshot model = stackedModel();
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                plan(model, true)
        );
        int apron = indexOf(layout, "Apron");
        int skirt = indexOf(layout, "Skirt");
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        Vector3f rest = new Vector3f(liningCollider(solver, apron, skirt));

        BoneModelSnapshot.Bone lining = bone(model, "Skirt");
        for (int frame = 0; frame < 60; frame++) {
            solver.restoreAnimationPose();
            lining.setRotationX(SWING);
            solver.solve(new Vector3f(), 0.0F, STEP, false);
        }
        float swung = liningCollider(solver, apron, skirt).distance(rest);
        require(
                swung > 1.0F / 16.0F,
                "The layer collider ignored the lining's swing: " + swung
        );

        solver.restoreAnimationPose();
        solver.solve(new Vector3f(), 0.0F, STEP, false);
        float held = liningCollider(solver, apron, skirt).distance(rest);
        require(
                held > swung * 0.5F,
                "The layer collider snapped back with the animation instead of"
                        + " following the solved pose: " + held
        );
    }

    /**
     * An animated cover can swing around the finite side of its lining and end
     * up behind it. Releasing that extreme pose must still restore the authored
     * order rather than retain the collision-induced physical offset.
     */
    private static void verifiesCrossedLayersReturnToRest() {
        BoneModelSnapshot model = stackedModel();
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                plan(model, true)
        );
        int apronNode = indexOf(layout, "Apron");
        int apronSlot = layout.node(apronNode).drivenSlot();
        BoneModelSnapshot.Bone cover = bone(model, "Apron");
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);

        for (int frame = 0; frame < 90; frame++) {
            solver.restoreAnimationPose();
            cover.setRotationX(-0.90F);
            cover.setRotationZ(CROSSING_SWING);
            solver.solve(new Vector3f(), 0.0F, STEP, false);
        }
        float displaced = angularDisplacement(solver, apronSlot);
        require(
                displaced > 0.15F,
                "Layer crossing fixture did not displace its cover: "
                        + displaced
        );

        for (int frame = 0; frame < 180; frame++) {
            solver.restoreAnimationPose();
            cover.setRotationX(0.0F);
            cover.setRotationZ(0.0F);
            solver.solve(new Vector3f(), 0.0F, STEP, false);
        }
        float recovered = angularDisplacement(solver, apronSlot);
        require(
                recovered < 0.10F,
                "Crossed cloth layers remained interlocked after release: "
                        + recovered
        );
    }

    private static float angularDisplacement(
            SpringBoneSolver solver,
            int drivenSlot
    ) {
        Vector3f current = new Vector3f();
        Vector3f rest = new Vector3f();
        require(
                solver.copyCurrentDirection(drivenSlot, current)
                        && solver.copyRestDirection(drivenSlot, rest),
                "Layer recovery direction was unavailable"
        );
        return (float) Math.acos(Math.max(
                -1.0F,
                Math.min(1.0F, current.dot(rest))
        ));
    }

    private static Vector3f liningCollider(
            SpringBoneSolver solver,
            int node,
            int reference
    ) {
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        for (int proxy = 0; proxy < solver.preparedProxyCount(node); proxy++) {
            if (solver.copyPreparedCollisionProxy(node, proxy, data)
                    && data.referenceNodeIndex == reference) {
                return data.boxCenter;
            }
        }
        throw new AssertionError("The apron has no collider for its lining");
    }

    /**
     * A torso to anchor the body axis, an inner skirt panel, and an apron one
     * pixel in front of it. Both panels are their own driven chain.
     */
    private static BoneModelSnapshot stackedModel() {
        return coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.stacked_panels",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0],
                     "cubes":[{"origin":[-4,8,-2],"size":[8,8,4],"uv":[0,0]}]},
                    {"name":"Skirt","parent":"Body","pivot":[0,12,-2],
                     "cubes":[{"origin":[-4,4,-2.5],"size":[8,8,1],
                       "uv":[0,0]}]},
                    {"name":"Apron","parent":"Body","pivot":[0,12,-3],
                     "cubes":[{"origin":[-3,4,-3.5],"size":[6,8,1],
                       "uv":[0,0]}]}
                  ]}]}
                """);
    }

    private static PhysicsSolverLayout layout(BoneModelSnapshot model) {
        return PhysicsSolverLayout.build(model, plan(model, true));
    }

    private static PhysicsBoneSelectionPlan plan(
            BoneModelSnapshot model,
            boolean auto
    ) {
        return PhysicsBoneDiscoverer.discover(
                "verification:cloth_layer_" + auto,
                model,
                PhysicsMetadataJsonParser.parse(
                        JsonParser.parseString("""
                                {"schema_version":3,"mode":"explicit","chains":[
                                  {"id":"lining","type":"SKIRT",
                                   "root":"Root/Body/Skirt",
                                   "constraints":{"collision":{"auto":%b}}},
                                  {"id":"cover","type":"SKIRT",
                                   "root":"Root/Body/Apron",
                                   "constraints":{"collision":{"auto":%b}}}]}
                                """.formatted(auto, auto)).getAsJsonObject(),
                        "cloth layer verification"
                )
        );
    }

    private static Set<String> references(
            PhysicsSolverLayout layout,
            String name
    ) {
        int index = indexOf(layout, name);
        require(index >= 0, "Missing active layout node " + name);
        CollisionProxySet proxies =
                layout.node(index).constraint().collisionProxies();
        Set<String> names = new HashSet<>();
        for (int slot = 0; slot < proxies.proxyCount(); slot++) {
            int reference = proxies.proxy(slot).referenceNodeIndex();
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
