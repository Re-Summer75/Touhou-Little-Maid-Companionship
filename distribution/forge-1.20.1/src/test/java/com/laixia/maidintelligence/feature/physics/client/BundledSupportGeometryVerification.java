package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class BundledSupportGeometryVerification {
    private BundledSupportGeometryVerification() {
    }

    static void run() throws Exception {
        requireUpperSupport("hailuo_new_year.json", "Bangs", 2.45F);
        requireUpperSupport("winefox_mini.json", "Bangs", 0.90F);
        requireUpperSupport("winefox_magical.json", "Bangs", 2.05F);
        requireUpperSupport("rice_cake_fox.json", "HairFront", 2.05F);
        requireUpperSupport("winefox_hanfu.json", "weioqun", 1.40F);
        requireUpperSupport("kluonoa.json", "SkiftLeft", 1.05F);
    }

    private static void requireUpperSupport(
            String fileName,
            String boneName,
            float minimumY
    ) throws Exception {
        AnimatedGeoModel model = new AnimatedGeoModel(loadGeoModel(
                MODEL_DIRECTORY.resolve(fileName)
        ));
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:support/" + fileName,
                model,
                PhysicsMetadata.EMPTY
        );
        var metrics = plan.kinematics(model.bones().get(boneName));
        require(
                metrics != null
                        && metrics.effectivePivot().y > minimumY
                        && metrics.axis().y < -0.35F
                        && metrics.supportConfidence() >= 0.20F,
                fileName + "/" + boneName
                        + " did not attach to its upper support: "
                        + (metrics == null
                        ? "not driven"
                        : metrics.effectivePivot() + " / " + metrics.axis()
                        + " / support=" + metrics.supportConfidence())
        );
    }
}
