package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.google.gson.JsonParser;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadWinefoxGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireDriven;

final class WinefoxGeometryVerification {
    private WinefoxGeometryVerification() {
    }

    static void run() throws Exception {
        GeoModel geoModel = loadWinefoxGeoModel();
        AnimatedGeoModel model = new AnimatedGeoModel(geoModel);
        PhysicsBoneSelectionPlan automatic = PhysicsBoneDiscoverer.discover(
                "geckolib:winefox",
                model,
                PhysicsMetadata.EMPTY
        );

        requireDriven(
                automatic,
                model.bones().get("BaseHair"),
                PhysicsBoneSelectionPlan.PartType.HEAD_SHELL,
                "Visible branching head shell was not discovered"
        );
        requireDriven(
                automatic,
                model.bones().get("bone5"),
                PhysicsBoneSelectionPlan.PartType.HAIR,
                "Anonymous hair geometry below the shell was not discovered"
        );
        requireDriven(
                automatic,
                model.bones().get("Tail7"),
                PhysicsBoneSelectionPlan.PartType.TAIL,
                "Tail tip was not selected"
        );
        require(
                !automatic.isDriven(model.bones().get("Head")),
                "Rigid head bone was selected"
        );
        require(
                !automatic.isDriven(model.bones().get("flower")),
                "Fixed flower attachment was selected"
        );

        AnimatedGeoModel switchedModel = new AnimatedGeoModel(geoModel);
        PhysicsBonePlanCache.clear();
        PhysicsBoneSelectionPlan firstCached = PhysicsBonePlanCache.getOrCompute(
                "geckolib:winefox",
                model
        );
        PhysicsBoneSelectionPlan switchedCached =
                PhysicsBonePlanCache.getOrCompute(
                        "geckolib:winefox",
                        switchedModel
                );
        require(
                firstCached != switchedCached,
                "Model switch reused a stale selection plan"
        );
        require(
                !firstCached.isDriven(switchedModel.bones().get("BaseHair")),
                "A plan accepted a same-name bone from another model instance"
        );
        require(
                switchedCached.isDriven(switchedModel.bones().get("BaseHair")),
                "Switched model did not receive a fresh head-shell decision"
        );
        PhysicsBonePlanCache.clear();

        PhysicsMetadata explicit = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {
                          "mode": "explicit",
                          "exclude": ["flower"],
                          "chains": [{
                            "id": "head_shell_only",
                            "type": "HEAD_SHELL",
                            "root": "Head/Hair/BaseHair",
                            "include_descendants": true,
                            "profile": {"stiffness_scale": 0.5}
                          }]
                        }
                        """).getAsJsonObject(),
                "verification"
        );
        PhysicsBoneSelectionPlan explicitPlan = PhysicsBoneDiscoverer.discover(
                "geckolib:winefox",
                model,
                explicit
        );
        require(
                explicitPlan.isDriven(model.bones().get("BaseHair")),
                "Explicit metadata did not select its root"
        );
        require(
                Math.abs(explicitPlan.decision(model.bones().get("BaseHair"))
                        .profile().stiffnessScale()
                        - PhysicsBoneSelectionPlan.SpringProfile.defaults(
                        PhysicsBoneSelectionPlan.PartType.HEAD_SHELL
                ).stiffnessScale() * 0.5F) < 1.0E-5F,
                "Metadata profile was not multiplied over the HEAD_SHELL preset"
        );
        require(
                explicitPlan.isDriven(model.bones().get("bone5")),
                "Explicit descendant binding did not include anonymous child"
        );
        require(
                !explicitPlan.isDriven(model.bones().get("flower")),
                "Explicit exclude did not override an included subtree"
        );
        require(
                !explicitPlan.isDriven(model.bones().get("Tail7")),
                "Explicit mode unexpectedly ran automatic discovery"
        );
    }
}
