package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.snapshot.BoneSnapshot;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneKinematics;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.SpringBoneSolver;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireNear;

final class BundledChainJointVerification {
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private BundledChainJointVerification() {
    }

    static void run() throws Exception {
        verifyModel("zhiban_hanfu.json");
        verifyModel("zhiban_new_year.json");
    }

    private static void verifyModel(String fileName) throws Exception {
        AnimatedGeoModel model = new AnimatedGeoModel(loadGeoModel(
                MODEL_DIRECTORY.resolve(fileName)
        ));
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:joints_" + fileName,
                model,
                PhysicsMetadata.EMPTY
        );
        AnimatedGeoBone[] left = chain(model, "LeftMWX");
        AnimatedGeoBone[] right = chain(model, "RightMWX");
        verifyBakedJoints(plan, left, fileName + " left");
        verifyBakedJoints(plan, right, fileName + " right");

        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        SpringBoneSolver solver = new SpringBoneSolver(layout, false);
        restore(model);
        solver.solve(new Vector3f(), 0.0F, 1.0F / 60.0F, true);
        verifyRuntimeJoints(layout, solver, left, fileName + " left");
        verifyRuntimeJoints(layout, solver, right, fileName + " right");

        for (int frame = 0; frame < 12; frame++) {
            restore(model);
            solver.solve(new Vector3f(), 0.0F, 1.0F / 60.0F, false);
        }
        Vector3f controlRoot = rotation(left[0]);
        Vector3f controlTip = rotation(left[2]);
        solver.reset();
        for (int frame = 0; frame < 12; frame++) {
            restore(model);
            solver.solve(
                    frame == 0
                            ? new Vector3f(8.0F, 0.0F, 0.0F)
                            : new Vector3f(),
                    0.0F,
                    1.0F / 60.0F,
                    false
            );
        }
        Vector3f rootResponse = rotation(left[0]).sub(controlRoot);
        Vector3f tipResponse = rotation(left[2]).sub(controlTip);
        require(
                rootResponse.lengthSquared() > 1.0E-8F
                        && tipResponse.lengthSquared() > 1.0E-8F
                        && rootResponse.distance(tipResponse) > 1.0E-4F,
                fileName + " segments did not respond independently"
        );
        verifyRuntimeJoints(layout, solver, left, fileName + " deflected left");
        verifyRuntimeJoints(layout, solver, right, fileName + " deflected right");
    }

    private static void verifyBakedJoints(
            PhysicsBoneSelectionPlan plan,
            AnimatedGeoBone[] bones,
            String label
    ) {
        for (int index = 0; index < bones.length; index++) {
            PhysicsBoneSelectionPlan.Decision decision =
                    plan.decision(bones[index]);
            require(
                    decision.driven()
                            && decision.chainSegment().index() == index
                            && decision.chainSegment().count() == bones.length,
                    label + " has invalid baked segment " + index
            );
        }
        for (int index = 0; index < bones.length - 1; index++) {
            BoneKinematics.Metrics metrics = plan.kinematics(bones[index]);
            Vector3f nextPivot = authoredPivot(bones[index + 1]);
            float expected = nextPivot.distance(metrics.effectivePivot())
                    * PIXELS_PER_BLOCK;
            requireNear(
                    metrics.segmentLength(),
                    expected,
                    1.0E-3F,
                    label + " endpoint does not terminate at the next joint"
            );
            require(
                    metrics.effectivePivot().distance(
                            plan.kinematics(bones[index + 1])
                                    .effectivePivot()
                    ) > 0.10F,
                    label + " adjacent effective pivots collapsed together"
            );
        }
    }

    private static void verifyRuntimeJoints(
            PhysicsSolverLayout layout,
            SpringBoneSolver solver,
            AnimatedGeoBone[] bones,
            String label
    ) {
        Vector3f tip = new Vector3f();
        Vector3f pivot = new Vector3f();
        for (int index = 0; index < bones.length - 1; index++) {
            int parent = indexOf(layout, bones[index]);
            int child = indexOf(layout, bones[index + 1]);
            require(
                    parent >= 0 && child >= 0
                            && solver.copyRuntimeTip(parent, tip)
                            && solver.copyRuntimePivot(child, pivot)
                            && tip.distance(pivot) < 1.0E-4F,
                    label + " runtime joint " + index + " separated"
            );
        }
    }

    private static AnimatedGeoBone[] chain(
            AnimatedGeoModel model,
            String root
    ) {
        return new AnimatedGeoBone[]{
                model.bones().get(root),
                model.bones().get(root + "2"),
                model.bones().get(root + "3")
        };
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

    private static Vector3f authoredPivot(AnimatedGeoBone bone) {
        return new Vector3f(
                bone.getPivotX(),
                bone.getPivotY(),
                bone.getPivotZ()
        ).div(PIXELS_PER_BLOCK);
    }

    private static Vector3f rotation(AnimatedGeoBone bone) {
        return new Vector3f(
                bone.getRotationX(),
                bone.getRotationY(),
                bone.getRotationZ()
        );
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
