package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.laixia.maidintelligence.feature.physics.layout.BoneKinematics;
import org.joml.Quaternionf;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Covers preserved and corrected compact mounts in the bundled Hanfu model.
 */
final class BundledAttachmentPivotVerification {
    private BundledAttachmentPivotVerification() {
    }

    static void run() throws Exception {
        BoneModelSnapshot model = coreModel(loadGeoModel(
                MODEL_DIRECTORY.resolve("zhiban_hanfu.json")
        ));
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:attachment_pivot/zhiban_hanfu",
                model,
                PhysicsMetadata.EMPTY
        );
        requirePreserved(model, plan, "bone101");
        requirePreserved(model, plan, "bone103");
        requireRemoteOriginCorrected(model, plan, "bone109");
    }

    private static void requirePreserved(
            BoneModelSnapshot model,
            PhysicsBoneSelectionPlan plan,
            String name
    ) {
        BoneModelSnapshot.Bone bone = model.bones().get(name);
        BoneKinematics.Metrics metrics = plan.kinematics(bone);
        PhysicsBoneSelectionPlan.Decision decision = plan.decision(bone);
        require(
                decision.driven()
                        && metrics != null
                        && metrics.supportStabilityPivotPreserved()
                        && !metrics.compensatesPivot()
                        && metrics.contactConfidence() >= 0.15F
                        && metrics.safeAngle() <= 0.1201F
                        && isStableCompound(decision),
                name + " accepted a destabilizing lower contact pivot: "
                        + decision + " / authored="
                        + metrics.authoredPivot() + " / effective="
                        + metrics.effectivePivot()
        );
    }

    private static void requireRemoteOriginCorrected(
            BoneModelSnapshot model,
            PhysicsBoneSelectionPlan plan,
            String name
    ) {
        BoneModelSnapshot.Bone bone = model.bones().get(name);
        BoneKinematics.Metrics metrics = plan.kinematics(bone);
        PhysicsBoneSelectionPlan.Decision decision = plan.decision(bone);
        float pivotShift = metrics == null
                ? 0.0F
                : metrics.authoredPivot().distance(
                metrics.effectivePivot()
        ) * 16.0F;
        require(
                decision.driven()
                        && metrics != null
                        && metrics.attachmentLeverPivotCorrected()
                        && metrics.compensatesPivot()
                        && pivotShift >= 3.0F
                        && metrics.leverArm() <= 3.1F
                        && metrics.contactConfidence() >= 0.20F
                        && metrics.safeAngle() <= 0.1201F
                        && metrics.compensationOffset(new Quaternionf())
                        .lengthSquared() <= 1.0E-8F
                        && isStableCompound(decision),
                name + " retained a peripheral rotation origin: "
                        + decision + " / authored="
                        + metrics.authoredPivot() + " / effective="
                        + metrics.effectivePivot() + " / lever="
                        + metrics.leverArm()
        );
    }

    private static boolean isStableCompound(
            PhysicsBoneSelectionPlan.Decision decision
    ) {
        return decision.structureRole()
                == PhysicsBoneSelectionPlan.StructureRole
                .COMPOUND_SINGLE_BONE
                && decision.profile().gravityScale() == 0.0F;
    }
}
