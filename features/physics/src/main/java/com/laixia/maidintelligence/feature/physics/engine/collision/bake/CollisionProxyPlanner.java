package com.laixia.maidintelligence.feature.physics.engine.collision.bake;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves all automatic and authored references before layout flattening.
 */
public final class CollisionProxyPlanner {
    private static final System.Logger LOGGER =
            System.getLogger(CollisionProxyPlanner.class.getName());

    private final PhysicsBoneGeometry.Analysis geometry;
    private final BodyCollisionGeometry bodyGeometry;
    private final PhysicsBoneSelectionPlan selectionPlan;
    private final MeshColliderPlanner meshPlanner;
    private final ClothLayerPlanner layerPlanner;
    private final Map<String, Optional<CollisionReference>> resolved =
            new HashMap<>();

    public CollisionProxyPlanner(
            PhysicsBoneGeometry.Analysis geometry,
            BodyCollisionGeometry bodyGeometry,
            PhysicsBoneSelectionPlan selectionPlan
    ) {
        this.geometry = geometry;
        this.bodyGeometry = bodyGeometry;
        this.selectionPlan = selectionPlan;
        this.meshPlanner = new MeshColliderPlanner(
                geometry,
                bodyGeometry,
                selectionPlan
        );
        this.layerPlanner = new ClothLayerPlanner(
                geometry,
                selectionPlan,
                bodyGeometry
        );
    }

    public CollisionProxyPlan plan(
            BoneModelSnapshot.Bone drivenBone,
            PhysicsBoneSelectionPlan.Decision decision,
            PhysicsBoneSelectionPlan.SimulationSpace space
    ) {
        PhysicsBoneGeometry.Node drivenNode = geometry.node(drivenBone);
        CollisionProxyPlan.Automatic automatic =
                AutomaticCollisionPolicy.select(
                decision,
                space
        );
        boolean allowsMesh = AutomaticCollisionPolicy.allowsMesh(decision);
        List<CollisionProxyPlan.MeshCollider> mesh = allowsMesh
                ? meshPlanner.plan(drivenNode)
                : List.of();
        List<CollisionProxyPlan.MeshCollider> layers = allowsMesh
                ? layerPlanner.plan(drivenNode)
                : List.of();
        CollisionReference body = needsBody(automatic)
                && bodyGeometry.hasBody()
                ? CollisionReference.of(
                bodyGeometry.body(),
                bodyGeometry.bodyBounds()
        ) : null;
        body = safeAutomatic(body, drivenNode) ? body : null;

        List<CollisionProxyPlan.Explicit> explicit = new ArrayList<>();
        for (PhysicsBoneSelectionPlan.CollisionProxySpec spec
                : decision.constraints().collision().proxies()) {
            resolve(spec.reference(), decision.chainId()).ifPresent(
                    reference -> explicit.add(
                            new CollisionProxyPlan.Explicit(spec, reference)
                    )
            );
        }
        return new CollisionProxyPlan(
                automatic,
                body,
                mesh,
                layers,
                explicit
        );
    }

    private Optional<CollisionReference> resolve(
            String authored,
            String chainId
    ) {
        Optional<CollisionReference> cached = resolved.get(authored);
        if (cached != null) {
            return cached;
        }
        Optional<CollisionReference> result;
        if ("MODEL".equalsIgnoreCase(authored)) {
            result = Optional.of(CollisionReference.model());
        } else if ("ROOT".equalsIgnoreCase(authored)) {
            result = uniqueRoot();
        } else {
            List<PhysicsBoneGeometry.Node> matches =
                    geometry.resolve(authored);
            result = matches.size() == 1
                    ? Optional.of(CollisionReference.of(matches.get(0)))
                    : Optional.empty();
        }
        resolved.put(authored, result);
        if (result.isEmpty()) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Skipping collision proxy in chain " + chainId
                            + ": reference '" + authored
                            + "' is missing or ambiguous"
            );
        }
        return result;
    }

    private Optional<CollisionReference> uniqueRoot() {
        PhysicsBoneGeometry.Node root = null;
        for (PhysicsBoneGeometry.Node node : geometry.nodes()) {
            if (node.parent() != null) {
                continue;
            }
            if (root != null) {
                return Optional.empty();
            }
            root = node;
        }
        return root == null
                ? Optional.empty()
                : Optional.of(CollisionReference.of(root));
    }

    private static boolean needsBody(
            CollisionProxyPlan.Automatic automatic
    ) {
        return automatic == CollisionProxyPlan.Automatic.BODY
                || automatic == CollisionProxyPlan.Automatic.CAPE;
    }

    private boolean safeAutomatic(
            CollisionReference reference,
            PhysicsBoneGeometry.Node drivenNode
    ) {
        if (reference == null || reference.bone() == null) {
            return false;
        }
        PhysicsBoneGeometry.Node referenceNode =
                geometry.node(reference.bone());
        return CollisionReferenceSafety.isSafe(
                referenceNode,
                drivenNode,
                selectionPlan,
                geometry
        );
    }
}
