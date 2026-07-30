package com.laixia.maidintelligence.feature.physics.client.discovery;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.client.PhysicsMetadata;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class MetadataPlanApplier {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MetadataPlanApplier() {
    }

    static Set<AnimatedGeoBone> apply(
            String modelId,
            PhysicsBoneSelectionPlan.Builder plan,
            PhysicsBoneGeometry.Analysis geometry,
            PhysicsMetadata metadata
    ) {
        Set<AnimatedGeoBone> excluded =
                Collections.newSetFromMap(new IdentityHashMap<>());
        for (String reference : metadata.excludes()) {
            for (PhysicsBoneGeometry.Node node :
                    DiscoveryReferences.match(geometry, reference)) {
                DiscoveryReferences.addSubtree(node, excluded);
            }
        }
        for (PhysicsMetadata.Chain chain : metadata.chains()) {
            applyChain(modelId, plan, geometry, metadata, chain, excluded);
        }
        return excluded;
    }

    private static void applyChain(
            String modelId,
            PhysicsBoneSelectionPlan.Builder plan,
            PhysicsBoneGeometry.Analysis geometry,
            PhysicsMetadata metadata,
            PhysicsMetadata.Chain chain,
            Set<AnimatedGeoBone> excluded
    ) {
        Set<AnimatedGeoBone> chainExcluded =
                Collections.newSetFromMap(new IdentityHashMap<>());
        for (String reference : chain.excludes()) {
            for (PhysicsBoneGeometry.Node node :
                    DiscoveryReferences.match(geometry, reference)) {
                DiscoveryReferences.addSubtree(node, chainExcluded);
            }
        }
        List<PhysicsBoneGeometry.Node> selected = new ArrayList<>();
        for (String root : chain.roots()) {
            List<PhysicsBoneGeometry.Node> matches =
                    DiscoveryReferences.match(geometry, root);
            warnIfMissing(modelId, metadata, chain.id(), "root", root, matches);
            for (PhysicsBoneGeometry.Node node : matches) {
                selected.add(node);
                if (chain.includeDescendants()) {
                    DiscoveryReferences.collectDescendants(
                            node,
                            geometry,
                            selected
                    );
                }
            }
        }
        for (String bone : chain.bones()) {
            List<PhysicsBoneGeometry.Node> matches =
                    DiscoveryReferences.match(geometry, bone);
            warnIfMissing(modelId, metadata, chain.id(), "bone", bone, matches);
            selected.addAll(matches);
        }
        PhysicsBoneSelectionPlan.SpringProfile profile =
                PhysicsBoneSelectionPlan.SpringProfile.defaults(chain.type())
                        .multiply(chain.profile());
        for (PhysicsBoneGeometry.Node node : selected) {
            if (!node.hasGeometry()
                    || excluded.contains(node.bone())
                    || chainExcluded.contains(node.bone())) {
                continue;
            }
            plan.decide(
                    node.bone(),
                    PhysicsBoneSelectionPlan.Decision.driven(
                            chain.type(),
                            PhysicsBoneSelectionPlan.Source.METADATA,
                            chain.id(),
                            1.0D,
                            profile,
                            chain.constraints(),
                            "explicit metadata: " + metadata.origin()
                    )
            );
        }
        excluded.addAll(chainExcluded);
    }

    private static void warnIfMissing(
            String modelId,
            PhysicsMetadata metadata,
            String chainId,
            String kind,
            String reference,
            List<PhysicsBoneGeometry.Node> matches
    ) {
        if (matches.isEmpty()) {
            LOGGER.warn(
                    "Physics metadata {} for model {} chain {} has unmatched {} reference '{}'",
                    metadata.origin(),
                    modelId,
                    chainId,
                    kind,
                    reference
            );
        }
    }
}
