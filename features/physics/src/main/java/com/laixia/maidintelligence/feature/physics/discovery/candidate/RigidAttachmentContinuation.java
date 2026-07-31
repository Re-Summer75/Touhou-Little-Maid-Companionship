package com.laixia.maidintelligence.feature.physics.discovery.candidate;


import com.laixia.maidintelligence.feature.physics.discovery.classifier.PhysicsBoneClassifier;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.discovery.structure.BoneStructureMetrics;
import org.joml.Vector3f;

/**
 * Propagates rigid mounts through empty anchors without swallowing real tails.
 */
final class RigidAttachmentContinuation {
    private RigidAttachmentContinuation() {
    }

    static PhysicsBoneGeometry.Node ancestor(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneSelectionPlan.Builder plan
    ) {
        PhysicsBoneGeometry.Node cursor = node.parent();
        while (cursor != null) {
            PhysicsBoneSelectionPlan.Decision decision =
                    plan.current(cursor.bone());
            if (decision != null
                    && decision.structureRole()
                    == PhysicsBoneSelectionPlan.StructureRole
                    .RIGID_ATTACHMENT_BASE) {
                return cursor;
            }
            if (cursor.hasGeometry()) {
                return null;
            }
            cursor = cursor.parent();
        }
        return null;
    }

    static boolean shouldRemainRigid(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Node rigidAncestor,
            Candidate candidate,
            BoneStructureMetrics structure
    ) {
        if (rigidAncestor == null
                || PhysicsBoneClassifier.classifyVisibleGeometry(
                node.bone().getName()
        ).isPhysical()
                || candidate.structureRole()
                == PhysicsBoneSelectionPlan.StructureRole
                .DANGLING_ACCESSORY
                || structure.longLeaf()) {
            return false;
        }
        double childMaximum = maximum(node.size());
        double parentMaximum = Math.max(
                DiscoveryMath.EPSILON,
                maximum(rigidAncestor.size())
        );
        boolean smallChild = childMaximum <= parentMaximum * 0.65D
                && node.bounds().volume()
                <= rigidAncestor.bounds().volume() * 0.45D;
        if (structure.compact()) {
            return true;
        }
        if (candidate.confidence()
                >= AutomaticPlanSelector.AUTO_THRESHOLD
                && !smallChild) {
            return false;
        }
        return smallChild
                || node.pivot().distance(rigidAncestor.pivot()) <= 0.05F;
    }

    private static double maximum(Vector3f size) {
        return Math.max(size.x, Math.max(size.y, size.z));
    }
}
