package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.model.GeckoBoneModelPort;
import com.laixia.maidintelligence.feature.physics.layout.BoneKinematics;

import java.util.Locale;

/**
 * Formats attachment frames and real-chain joint spacing for skeleton dumps.
 */
final class PhysicsKinematicsDebugText {
    private PhysicsKinematicsDebugText() {
    }

    static String describe(
            AnimatedGeoBone bone,
            AnimatedGeoBone parent,
            AnimatedGeoBone nearestSolidAncestor,
            PhysicsBoneSelectionPlan.Decision decision,
            PhysicsBoneSelectionPlan plan,
            GeckoBoneModelPort modelPort
    ) {
        BoneModelSnapshot.Bone coreBone = modelPort.coreBone(bone);
        BoneKinematics.Metrics metrics = plan.kinematics(coreBone);
        if (metrics == null) {
            metrics = BoneKinematics.measure(
                    coreBone,
                    modelPort.coreBone(parent),
                    modelPort.coreBone(nearestSolidAncestor),
                    decision.type(),
                    decision.structureRole()
            );
        }
        PhysicsBoneSelectionPlan.ChainSegment segment =
                decision.chainSegment();
        String jointSpacing = segment.count() > 1
                && segment.index() < segment.count() - 1
                ? String.format(
                Locale.ROOT,
                " jointSpacing=%.2fpx",
                metrics.segmentLength()
        )
                : "";
        return String.format(
                Locale.ROOT,
                " effectivePivot=(%.2f,%.2f,%.2f)"
                        + " physicsAxis=(%.3f,%.3f,%.3f)"
                        + " pivotCorrected=%s supportConfidence=%.3f"
                        + " contactConfidence=%.3f pivotScore=%.4f"
                        + " supportStabilityCorrected=%s"
                        + " supportStabilityPreserved=%s"
                        + " attachmentLeverCorrected=%s"
                        + " supportStabilityUnsupported=%s"
                        + " axisPolarityCorrected=%s primaryCluster=%s"
                        + " segmentLength=%.2fpx%s"
                        + " safetyLever=%.2fpx safeAngle=%.1fdeg",
                metrics.effectivePivot().x * 16.0F,
                metrics.effectivePivot().y * 16.0F,
                metrics.effectivePivot().z * 16.0F,
                metrics.axis().x,
                metrics.axis().y,
                metrics.axis().z,
                metrics.compensatesPivot(),
                metrics.supportConfidence(),
                metrics.contactConfidence(),
                metrics.pivotScore(),
                metrics.supportStabilityPivotCorrected(),
                metrics.supportStabilityPivotPreserved(),
                metrics.attachmentLeverPivotCorrected(),
                metrics.supportStabilityUnsupported(),
                metrics.axisPolarityCorrected(),
                metrics.usesDominantCluster(),
                metrics.segmentLength(),
                jointSpacing,
                metrics.leverArm(),
                Math.toDegrees(
                        metrics.safeAngle()
                                * decision.profile().angleScale()
                )
        );
    }
}
