package com.laixia.maidintelligence.feature.physics.client.discovery;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneClassifier;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;

final class CandidateScorer {
    private CandidateScorer() {
    }

    static Candidate score(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            AnimatedGeoModel model
    ) {
        if (!node.hasGeometry()) {
            return Candidate.reject("no geometry");
        }
        if (Boolean.TRUE.equals(node.bone().geoBone().dontRender())) {
            return Candidate.reject("model marks bone as never render");
        }
        if (RigidBoneFilter.isCoreBone(node, geometry, model)) {
            return Candidate.reject("rigid humanoid or locator bone");
        }
        PhysicsBoneClassifier.Classification semantic =
                PhysicsBoneClassifier.classify(node.bone().getName());
        PhysicsBoneSelectionPlan.PartType hint =
                DiscoveryMath.partType(semantic.type());
        ScoringContext context = ScoringContext.create(
                node,
                geometry,
                hint,
                semantic.isPhysical() ? 0.18D : 0.0D
        );
        return context.inHead()
                ? HeadCandidateScorer.score(context)
                : BodyCandidateScorer.score(context);
    }
}
