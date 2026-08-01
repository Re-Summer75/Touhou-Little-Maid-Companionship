package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.discovery.PhysicsBoneDiscoverer;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProxyDebugData;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadata;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/** Regression coverage for the three front apron chains in winefox. */
final class WinefoxApronCollisionVerification {
    private static final Set<String> CENTRE_SKIRT =
            Set.of("FM", "FM1", "FM2");
    private static final Set<String> LEFT_SKIRT =
            Set.of("FL", "FL1", "FL2");
    private static final Set<String> RIGHT_SKIRT =
            Set.of("FR", "FR1", "FR2");

    private WinefoxApronCollisionVerification() {
    }

    static void run() throws Exception {
        BoneModelSnapshot model = coreModel(loadGeoModel(
                MODEL_DIRECTORY.resolve("winefox.json")
        ));
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:winefox_apron_layers",
                model,
                PhysicsMetadata.EMPTY
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);

        require(
                containsAny(
                        bakedReferences(layout, "FFM2", "FFM2_1"),
                        LEFT_SKIRT
                ),
                "FFM2 apron chain has no FL lining"
        );
        require(
                containsAny(
                        bakedReferences(layout, "FFM3", "FFM3_1"),
                        RIGHT_SKIRT
                ),
                "FFM3 apron chain has no FR lining"
        );

        SpringBoneSolver solver = new SpringBoneSolver(layout);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        requireRuntimeLining(
                solver, layout, CENTRE_SKIRT, "FFM1", "FFM1_1"
        );
        requireRuntimeLining(
                solver, layout, LEFT_SKIRT, "FFM2", "FFM2_1"
        );
        requireRuntimeLining(
                solver, layout, RIGHT_SKIRT, "FFM3", "FFM3_1"
        );
    }

    private static void requireRuntimeLining(
            SpringBoneSolver solver,
            PhysicsSolverLayout layout,
            Set<String> lining,
            String... apron
    ) {
        require(
                containsAny(preparedReferences(solver, layout, apron), lining),
                apron[0] + " lining was evicted from runtime Top-K"
        );
    }

    private static Set<String> bakedReferences(
            PhysicsSolverLayout layout,
            String... names
    ) {
        Set<String> output = new HashSet<>();
        for (String name : names) {
            int node = indexOf(layout, name);
            CollisionProxySet proxies =
                    layout.node(node).constraint().collisionProxies();
            for (int slot = 0; slot < proxies.proxyCount(); slot++) {
                addReference(layout, proxies.proxy(slot).referenceNodeIndex(),
                        output);
            }
        }
        return output;
    }

    private static Set<String> preparedReferences(
            SpringBoneSolver solver,
            PhysicsSolverLayout layout,
            String... names
    ) {
        Set<String> output = new HashSet<>();
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        for (String name : names) {
            int node = indexOf(layout, name);
            for (int slot = 0; slot < solver.preparedProxyCount(node); slot++) {
                if (solver.copyPreparedCollisionProxy(node, slot, data)) {
                    addReference(layout, data.referenceNodeIndex, output);
                }
            }
        }
        return output;
    }

    private static void addReference(
            PhysicsSolverLayout layout,
            int reference,
            Set<String> output
    ) {
        if (reference >= 0 && reference < layout.activeNodeCount()) {
            output.add(layout.node(reference).bone().getName());
        }
    }

    private static boolean containsAny(
            Set<String> references,
            Set<String> expected
    ) {
        return references.stream().anyMatch(expected::contains);
    }

    private static int indexOf(PhysicsSolverLayout layout, String name) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (name.equals(layout.node(index).bone().getName())) {
                return index;
            }
        }
        throw new AssertionError("Missing active layout node " + name);
    }
}
