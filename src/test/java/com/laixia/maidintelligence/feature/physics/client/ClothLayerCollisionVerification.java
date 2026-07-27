package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.CollisionProxyDebugData;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
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

    private ClothLayerCollisionVerification() {
    }

    static void run() {
        verifiesApronTakesSkirtAsLining();
        verifiesCoplanarPanelsAreNotStacked();
        verifiesWrappedPanelsHaveNoOrder();
        verifiesOuterPanelStopsAtInnerPanel();
        verifiesColliderFollowsSolvedPose();
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
     * Flaps sitting side by side in one plane meet along an edge. They are the
     * same sheet split across bones and have no layer order.
     */
    private static void verifiesCoplanarPanelsAreNotStacked() {
        PhysicsSolverLayout layout = layout(modelFromJson("""
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
        PhysicsSolverLayout layout = layout(modelFromJson("""
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
        AnimatedGeoModel model = stackedModel();
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                plan(model, auto)
        );
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        AnimatedGeoBone apron = bone(model, "Apron");
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
        AnimatedGeoModel model = stackedModel();
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                plan(model, true)
        );
        int apron = indexOf(layout, "Apron");
        int skirt = indexOf(layout, "Skirt");
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        Vector3f rest = new Vector3f(liningCollider(solver, apron, skirt));

        AnimatedGeoBone lining = bone(model, "Skirt");
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
    private static AnimatedGeoModel stackedModel() {
        return modelFromJson("""
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

    private static PhysicsSolverLayout layout(AnimatedGeoModel model) {
        return PhysicsSolverLayout.build(model, plan(model, true));
    }

    private static PhysicsBoneSelectionPlan plan(
            AnimatedGeoModel model,
            boolean auto
    ) {
        return PhysicsBoneDiscoverer.discover(
                "verification:cloth_layer_" + auto,
                model,
                PhysicsMetadata.parse(
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

    private static AnimatedGeoBone bone(AnimatedGeoModel model, String name) {
        AnimatedGeoBone[] found = new AnimatedGeoBone[1];
        BonePhysicsVerificationSupport.forEachBone(model, bone -> {
            if (name.equals(bone.getName())) {
                found[0] = bone;
            }
        });
        require(found[0] != null, "Missing bone " + name);
        return found[0];
    }
}
