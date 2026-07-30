package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.laixia.maidintelligence.feature.physics.layout.BoneKinematics;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Verifies contact-aware pivots without requiring child-surface snapping.
 */
final class ContactAwarePivotVerification {
    private ContactAwarePivotVerification() {
    }

    static void run() {
        verifiesOnePixelContactGap();
        verifiesTwoPixelContactGap();
        verifiesAmbiguousThroughContactFallsBack();
        verifiesHeadShellPrefersSupportContact();
    }

    private static void verifiesOnePixelContactGap() {
        BoneKinematics.Metrics metrics = hangingHair(1.0F);
        requireContactGap(metrics, 15.0F, 16.0F, "one-pixel");
    }

    private static void verifiesTwoPixelContactGap() {
        BoneKinematics.Metrics metrics = hangingHair(2.0F);
        requireContactGap(metrics, 14.0F, 16.0F, "two-pixel");
    }

    private static BoneKinematics.Metrics hangingHair(float gapPixels) {
        float top = 16.0F - gapPixels;
        BoneModelSnapshot model = coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.contact_gap",
                    "texture_width":32,"texture_height":32},
                  "bones":[
                    {"name":"Head","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"Hair","parent":"Head","pivot":[0,-20,0],
                     "cubes":[{"origin":[-1,%s,-1],"size":[2,8,2],"uv":[0,0]}]}
                  ]}]}
                """.formatted(top - 8.0F));
        BoneModelSnapshot.Bone head = model.bones().get("Head");
        return BoneKinematics.measure(
                model.bones().get("Hair"),
                head,
                head,
                PhysicsBoneSelectionPlan.PartType.HAIR
        );
    }

    private static void requireContactGap(
            BoneKinematics.Metrics metrics,
            float childTopPixels,
            float supportBottomPixels,
            String label
    ) {
        float pivotY = metrics.effectivePivot().y * 16.0F;
        require(
                metrics.compensatesPivot()
                        && metrics.contactConfidence() >= 0.20F
                        && pivotY >= childTopPixels - 0.25F
                        && pivotY <= supportBottomPixels + 0.25F
                        && metrics.axis().y < -0.70F,
                label + " gap did not produce a contact-aware root: pivot="
                        + metrics.effectivePivot()
                        + ", axis=" + metrics.axis()
                        + ", contact=" + metrics.contactConfidence()
                        + ", score=" + metrics.pivotScore()
        );
    }

    private static void verifiesAmbiguousThroughContactFallsBack() {
        BoneModelSnapshot model = coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.ambiguous_contact",
                    "texture_width":32,"texture_height":32},
                  "bones":[
                    {"name":"Head","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"Ribbon","parent":"Head","pivot":[4,20,0],
                     "cubes":[{"origin":[-4,19.5,-.5],"size":[8,1,1],"uv":[0,0]}]}
                  ]}]}
                """);
        BoneModelSnapshot.Bone head = model.bones().get("Head");
        BoneKinematics.Metrics metrics = BoneKinematics.measure(
                model.bones().get("Ribbon"),
                head,
                head,
                PhysicsBoneSelectionPlan.PartType.RIBBON
        );
        require(
                !metrics.compensatesPivot()
                        && metrics.contactConfidence() < 0.20F
                        && metrics.safeAngle() <= 0.18F,
                "Broad through-contact was mistaken for a joint: "
                        + metrics.effectivePivot()
                        + " / contact=" + metrics.contactConfidence()
        );
    }

    private static void verifiesHeadShellPrefersSupportContact() {
        BoneModelSnapshot model = coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.shell_contact",
                    "texture_width":32,"texture_height":32},
                  "bones":[
                    {"name":"Head","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"Cap","parent":"Head","pivot":[20,40,0],
                     "cubes":[{"origin":[-4,25,-4],"size":[8,2,8],"uv":[0,0]}]}
                  ]}]}
                """);
        BoneModelSnapshot.Bone head = model.bones().get("Head");
        BoneKinematics.Metrics metrics = BoneKinematics.measure(
                model.bones().get("Cap"),
                head,
                head,
                PhysicsBoneSelectionPlan.PartType.HEAD_SHELL
        );
        require(
                metrics.compensatesPivot()
                        && metrics.contactConfidence() >= 0.15F
                        && metrics.effectivePivot().y * 16.0F < 25.5F,
                "Head shell ignored its support contact: "
                        + metrics.effectivePivot()
                        + " / contact=" + metrics.contactConfidence()
                        + " / score=" + metrics.pivotScore()
        );
    }
}
