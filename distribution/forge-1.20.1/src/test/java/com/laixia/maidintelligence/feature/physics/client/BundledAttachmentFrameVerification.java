package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class BundledAttachmentFrameVerification {
    private BundledAttachmentFrameVerification() {
    }

    static void run() throws Exception {
        BoneModelSnapshot zhiban = coreModel(loadGeoModel(
                MODEL_DIRECTORY.resolve("zhiban.json")
        ));
        PhysicsBoneSelectionPlan zhibanPlan = PhysicsBoneDiscoverer.discover(
                "verification:zhiban_attachment",
                zhiban,
                PhysicsMetadata.EMPTY
        );
        requireRigidWithoutFrame(zhibanPlan, zhiban, "bone29");
        requireRigidWithoutFrame(zhibanPlan, zhiban, "bone30");
        requireRigidWithoutFrame(zhibanPlan, zhiban, "bone27");
        requireRigidWithoutFrame(zhibanPlan, zhiban, "bone31");
        requireAxisY(zhibanPlan, zhiban, "bone3", -0.25F);
        requireAxisY(zhibanPlan, zhiban, "bone9", -0.25F);
        requireAxisY(zhibanPlan, zhiban, "bone34", 0.25F);

        BoneModelSnapshot winefox = coreModel(loadGeoModel(
                MODEL_DIRECTORY.resolve("winefox.json")
        ));
        PhysicsBoneSelectionPlan winefoxPlan = PhysicsBoneDiscoverer.discover(
                "verification:winefox_attachment",
                winefox,
                PhysicsMetadata.EMPTY
        );
        requireAxisY(winefoxPlan, winefox, "bone5", -0.50F);

        BoneModelSnapshot hanfu = coreModel(loadGeoModel(
                MODEL_DIRECTORY.resolve("winefox_hanfu.json")
        ));
        PhysicsBoneSelectionPlan hanfuPlan = PhysicsBoneDiscoverer.discover(
                "verification:winefox_hanfu_attachment",
                hanfu,
                PhysicsMetadata.EMPTY
        );
        requireUpperAttachment(hanfuPlan, hanfu, "Bangs", 2.45F);
        requireUpperAttachment(
                hanfuPlan, hanfu, "RightSideHair", 2.40F
        );
        requireUpperAttachment(
                hanfuPlan, hanfu, "LeftSideHair", 2.40F
        );

        BoneModelSnapshot riceCake = coreModel(loadGeoModel(
                MODEL_DIRECTORY.resolve("rice_cake_fox.json")
        ));
        PhysicsBoneSelectionPlan riceCakePlan = PhysicsBoneDiscoverer.discover(
                "verification:rice_cake_attachment",
                riceCake,
                PhysicsMetadata.EMPTY
        );
        var pony = riceCakePlan.kinematics(riceCake.bones().get("LeftPony"));
        require(
                pony != null && !pony.compensatesPivot() && pony.axis().y < -0.80F,
                "Correct Rice Cake Fox ponytail pivot was unnecessarily changed"
        );
    }

    private static void requireUpperAttachment(
            PhysicsBoneSelectionPlan plan,
            BoneModelSnapshot model,
            String boneName,
            float minimumY
    ) {
        var metrics = plan.kinematics(model.bones().get(boneName));
        require(
                metrics != null
                        && !metrics.attachmentLeverPivotCorrected()
                        && metrics.effectivePivot().y > minimumY
                        && metrics.axis().y < -0.50F
                        && metrics.supportConfidence() >= 0.20F,
                "Winefox Hanfu " + boneName
                        + " attached at its lower endpoint"
        );
    }

    private static void requireRigidWithoutFrame(
            PhysicsBoneSelectionPlan plan,
            BoneModelSnapshot model,
            String boneName
    ) {
        require(
                !plan.isDriven(model.bones().get(boneName))
                        && plan.kinematics(model.bones().get(boneName)) == null,
                boneName + " rigid attachment unexpectedly retained physics"
        );
    }

    private static void requireAxisY(
            PhysicsBoneSelectionPlan plan,
            BoneModelSnapshot model,
            String boneName,
            float threshold
    ) {
        var metrics = plan.kinematics(model.bones().get(boneName));
        float axisY = metrics == null ? 0.0F : metrics.axis().y;
        require(
                metrics != null && (
                        threshold > 0.0F
                                ? axisY > threshold
                                : axisY < threshold
                ),
                boneName + " attachment axis was reversed: " + axisY
        );
    }
}
