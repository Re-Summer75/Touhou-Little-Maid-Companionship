package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadataJsonParser;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.decision;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.discover;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.isDriven;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadWinefoxGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireDriven;

final class WinefoxGeometryVerification {
    private WinefoxGeometryVerification() {
    }

    static void run() throws Exception {
        GeoModel geoModel = loadWinefoxGeoModel();
        AnimatedGeoModel model = new AnimatedGeoModel(geoModel);
        PhysicsBoneSelectionPlan automatic = discover(
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
                !isDriven(automatic, model.bones().get("Head")),
                "Rigid head bone was selected"
        );
        require(
                !isDriven(automatic, model.bones().get("flower")),
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
                !isDriven(firstCached, switchedModel.bones().get("BaseHair")),
                "A plan accepted a same-name bone from another model instance"
        );
        require(
                isDriven(switchedCached, switchedModel.bones().get("BaseHair")),
                "Switched model did not receive a fresh head-shell decision"
        );
        PhysicsBonePlanCache.clear();

        PhysicsMetadata explicit = PhysicsMetadataJsonParser.parse(
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
        PhysicsBoneSelectionPlan explicitPlan = discover(
                "geckolib:winefox",
                model,
                explicit
        );
        require(
                isDriven(explicitPlan, model.bones().get("BaseHair")),
                "Explicit metadata did not select its root"
        );
        require(
                Math.abs(decision(explicitPlan, model.bones().get("BaseHair"))
                        .profile().stiffnessScale()
                        - PhysicsBoneSelectionPlan.SpringProfile.defaults(
                        PhysicsBoneSelectionPlan.PartType.HEAD_SHELL
                ).stiffnessScale() * 0.5F) < 1.0E-5F,
                "Metadata profile was not multiplied over the HEAD_SHELL preset"
        );
        require(
                isDriven(explicitPlan, model.bones().get("bone5")),
                "Explicit descendant binding did not include anonymous child"
        );
        require(
                !isDriven(explicitPlan, model.bones().get("flower")),
                "Explicit exclude did not override an included subtree"
        );
        require(
                !isDriven(explicitPlan, model.bones().get("Tail7")),
                "Explicit mode unexpectedly ran automatic discovery"
        );
    }
}
