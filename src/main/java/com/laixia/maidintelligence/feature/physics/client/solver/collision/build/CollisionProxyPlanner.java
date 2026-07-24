package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves all automatic and authored references before layout flattening.
 */
public final class CollisionProxyPlanner {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final PhysicsBoneGeometry.Analysis geometry;
    private final BodyCollisionGeometry bodyGeometry;
    private final PhysicsBoneSelectionPlan selectionPlan;
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
    }

    public CollisionProxyPlan plan(
            AnimatedGeoBone drivenBone,
            PhysicsBoneSelectionPlan.Decision decision,
            PhysicsBoneSelectionPlan.SimulationSpace space
    ) {
        PhysicsBoneGeometry.Node drivenNode = geometry.node(drivenBone);
        CollisionProxyPlan.Automatic automatic =
                AutomaticCollisionPolicy.select(
                decision,
                space,
                drivenNode,
                selectionPlan
        );
        CollisionReference head = automatic
                == CollisionProxyPlan.Automatic.HEAD
                && bodyGeometry.hasHead()
                ? CollisionReference.of(
                bodyGeometry.head(),
                bodyGeometry.headBounds()
        ) : null;
        head = safeAutomatic(head, drivenNode) ? head : null;
        CollisionReference body = needsBody(automatic)
                && bodyGeometry.hasBody()
                ? CollisionReference.of(
                bodyGeometry.body(),
                bodyGeometry.bodyBounds()
        ) : null;
        body = safeAutomatic(body, drivenNode) ? body : null;
        CollisionReference left = null;
        CollisionReference right = null;
        if (automatic == CollisionProxyPlan.Automatic.SKIRT
                && bodyGeometry.legs().isPresent()) {
            BodyCollisionGeometry.LegPair pair =
                    bodyGeometry.legs().orElseThrow();
            left = CollisionReference.of(
                    pair.left().node(),
                    pair.left().bounds()
            );
            right = CollisionReference.of(
                    pair.right().node(),
                    pair.right().bounds()
            );
            left = safeAutomatic(left, drivenNode) ? left : null;
            right = safeAutomatic(right, drivenNode) ? right : null;
        }

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
                head,
                body,
                left,
                right,
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
            LOGGER.warn(
                    "Skipping collision proxy in chain {}: reference '{}' is missing or ambiguous",
                    chainId,
                    authored
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
                || automatic == CollisionProxyPlan.Automatic.SKIRT
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
