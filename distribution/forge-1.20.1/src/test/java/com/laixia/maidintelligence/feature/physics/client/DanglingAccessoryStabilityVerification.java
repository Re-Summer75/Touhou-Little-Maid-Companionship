package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.snapshot.BoneSnapshot;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.SpringBoneSolver;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class DanglingAccessoryStabilityVerification {
    private DanglingAccessoryStabilityVerification() {
    }

    static void run() throws Exception {
        verifyStableAnchor("winefox.json", "Mask");
        verifyStableAnchor("winefox_wedding.json", "Mask");
    }

    private static void verifyStableAnchor(
            String fileName,
            String boneName
    ) throws Exception {
        AnimatedGeoModel model = new AnimatedGeoModel(loadGeoModel(
                MODEL_DIRECTORY.resolve(fileName)
        ));
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:stable_" + fileName,
                model,
                PhysicsMetadata.EMPTY
        );
        AnimatedGeoBone bone = model.bones().get(boneName);
        PhysicsBoneSelectionPlan.Decision decision = plan.decision(bone);
        require(
                decision.driven()
                        && decision.structureRole()
                        == PhysicsBoneSelectionPlan.StructureRole
                        .DANGLING_ACCESSORY,
                fileName + " " + boneName
                        + " is not a bounded dangling accessory"
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        int index = indexOf(layout, bone);
        require(index >= 0, fileName + " accessory is absent from layout");
        SpringBoneSolver solver = new SpringBoneSolver(layout, false);
        restore(model);
        solver.solve(new Vector3f(), 0.0F, 1.0F / 60.0F, true);
        Vector3f baseline = new Vector3f();
        Vector3f baselineTip = new Vector3f();
        require(
                solver.copyRuntimePivot(index, baseline)
                        && solver.copyRuntimeTip(index, baselineTip),
                fileName + " did not publish its attachment point"
        );
        for (int frame = 0; frame < 180; frame++) {
            restore(model);
            solver.solve(new Vector3f(), 0.0F, 1.0F / 60.0F, false);
        }
        Vector3f settled = new Vector3f();
        Vector3f settledTip = new Vector3f();
        require(
                solver.copyRuntimePivot(index, settled)
                        && solver.copyRuntimeTip(index, settledTip)
                        && settled.distance(baseline) < 1.0E-4F
                        && settledTip.distance(baselineTip) < 1.0E-4F,
                fileName + " " + boneName
                        + " drifted away from its authored attachment: pivot="
                        + settled.distance(baseline) + ", tip="
                        + settledTip.distance(baselineTip)
        );
        BoneSnapshot initial = bone.getInitialSnapshot();
        float angularDelta =
                Math.abs(bone.getRotationX() - initial.rotationValueX)
                        + Math.abs(
                        bone.getRotationY() - initial.rotationValueY
                )
                        + Math.abs(
                        bone.getRotationZ() - initial.rotationValueZ
                );
        float positionDelta =
                Math.abs(bone.getPositionX() - initial.positionOffsetX)
                        + Math.abs(
                        bone.getPositionY() - initial.positionOffsetY
                )
                        + Math.abs(
                        bone.getPositionZ() - initial.positionOffsetZ
                );
        require(
                decision.profile().gravityScale() == 0.0F
                        && angularDelta < 1.0E-5F
                        && positionDelta < 1.0E-5F,
                fileName + " " + boneName
                        + " drifted from its non-physical rest pose: angle="
                        + angularDelta + ", position=" + positionDelta
                        + ", gravity="
                        + decision.profile().gravityScale()
        );
        verifyTiltedRestPose(fileName, model, boneName, solver, index);
        solver.reset();
        restore(model);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        restore(model);
        solver.solve(
                new Vector3f(4.0F, 0.0F, 0.0F),
                0.0F,
                1.0F / 60.0F,
                false
        );
        float impulseDelta =
                Math.abs(bone.getRotationX() - initial.rotationValueX)
                        + Math.abs(
                        bone.getRotationY() - initial.rotationValueY
                )
                        + Math.abs(
                        bone.getRotationZ() - initial.rotationValueZ
                );
        require(
                impulseDelta > 1.0E-5F,
                fileName + " " + boneName
                        + " lost inertial motion after gravity removal"
        );
    }

    private static void verifyTiltedRestPose(
            String fileName,
            AnimatedGeoModel model,
            String boneName,
            SpringBoneSolver solver,
            int index
    ) {
        solver.reset();
        restoreWithHeadTilt(model);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        Vector3f baselineTip = new Vector3f();
        require(
                solver.copyRuntimeTip(index, baselineTip),
                fileName + " did not publish its tilted rest tip"
        );
        for (int frame = 0; frame < 180; frame++) {
            restoreWithHeadTilt(model);
            solver.solve(new Vector3f(), 0.0F, 1.0F / 60.0F, false);
        }
        Vector3f settledTip = new Vector3f();
        require(
                solver.copyRuntimeTip(index, settledTip)
                        && settledTip.distance(baselineTip) < 1.0E-4F,
                fileName + " " + boneName
                        + " moved from its authored tilted rest pose: tip="
                        + settledTip.distance(baselineTip)
        );
    }

    private static void restoreWithHeadTilt(AnimatedGeoModel model) {
        restore(model);
        AnimatedGeoBone head = model.bones().get("Head");
        BoneSnapshot initial = head.getInitialSnapshot();
        head.setRotationX(initial.rotationValueX + 0.28F);
        head.setRotationY(initial.rotationValueY - 0.17F);
    }

    private static int indexOf(
            PhysicsSolverLayout layout,
            AnimatedGeoBone bone
    ) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).bone() == bone) {
                return index;
            }
        }
        return -1;
    }

    private static void restore(AnimatedGeoModel model) {
        for (AnimatedGeoBone bone : model.topLevelBones()) {
            restore(bone);
        }
    }

    private static void restore(AnimatedGeoBone bone) {
        BoneSnapshot initial = bone.getInitialSnapshot();
        bone.setRotationX(initial.rotationValueX);
        bone.setRotationY(initial.rotationValueY);
        bone.setRotationZ(initial.rotationValueZ);
        bone.setPositionX(initial.positionOffsetX);
        bone.setPositionY(initial.positionOffsetY);
        bone.setPositionZ(initial.positionOffsetZ);
        bone.setScaleX(initial.scaleValueX);
        bone.setScaleY(initial.scaleValueY);
        bone.setScaleZ(initial.scaleValueZ);
        for (AnimatedGeoBone child : bone.children()) {
            restore(child);
        }
    }
}
