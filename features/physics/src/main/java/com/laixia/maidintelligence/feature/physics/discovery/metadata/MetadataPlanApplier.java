package com.laixia.maidintelligence.feature.physics.discovery.metadata;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;


import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class MetadataPlanApplier {
    private static final System.Logger LOGGER =
            System.getLogger(MetadataPlanApplier.class.getName());

    private MetadataPlanApplier() {
    }

    static Set<BoneModelSnapshot.Bone> apply(
            String modelId,
            PhysicsBoneSelectionPlan.Builder plan,
            PhysicsBoneGeometry.Analysis geometry,
            PhysicsMetadata metadata
    ) {
        Set<BoneModelSnapshot.Bone> excluded =
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
            Set<BoneModelSnapshot.Bone> excluded
    ) {
        Set<BoneModelSnapshot.Bone> chainExcluded =
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
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Physics metadata " + metadata.origin()
                            + " for model " + modelId
                            + " chain " + chainId
                            + " has unmatched " + kind
                            + " reference '" + reference + "'"
            );
        }
    }
}
