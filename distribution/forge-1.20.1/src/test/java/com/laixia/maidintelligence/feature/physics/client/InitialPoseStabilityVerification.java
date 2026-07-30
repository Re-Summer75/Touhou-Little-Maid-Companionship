package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class InitialPoseStabilityVerification {
    private static final float EPSILON = 1.0E-6F;

    private InitialPoseStabilityVerification() {
    }

    static void run() throws Exception {
        List<Path> modelPaths;
        try (var paths = Files.list(MODEL_DIRECTORY)) {
            modelPaths = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".json"))
                    .sorted()
                    .toList();
        }
        for (Path modelPath : modelPaths) {
            verifyModel(modelPath);
        }
    }

    private static void verifyModel(Path modelPath) throws Exception {
        BoneModelSnapshot model = coreModel(loadGeoModel(modelPath));
        String fileName = modelPath.getFileName().toString();
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:initial_pose_" + fileName,
                model,
                PhysicsMetadata.EMPTY
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        verifyUnchanged(fileName, model, plan, solver);
        solver.reset();
        applyAnimatedPose(model, plan);
        verifyUnchanged(fileName + " animated", model, plan, solver);
    }

    private static void verifyUnchanged(
            String label,
            BoneModelSnapshot model,
            PhysicsBoneSelectionPlan plan,
            SpringBoneSolver solver
    ) {
        IdentityHashMap<BoneModelSnapshot.Bone, Transform> before =
                captureDriven(model, plan);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        before.forEach((bone, transform) -> require(
                transform.matches(bone),
                label + " changed the initial pose of "
                        + bone.getName() + ": before=" + transform
                        + ", after=" + Transform.capture(bone)
                        + ", decision=" + plan.decision(bone)
        ));
    }

    private static IdentityHashMap<BoneModelSnapshot.Bone, Transform> captureDriven(
            BoneModelSnapshot model,
            PhysicsBoneSelectionPlan plan
    ) {
        IdentityHashMap<BoneModelSnapshot.Bone, Transform> output =
                new IdentityHashMap<>();
        for (BoneModelSnapshot.Bone bone : model.boneList()) {
            if (plan.isDriven(bone)) {
                output.put(bone, Transform.capture(bone));
            }
        }
        return output;
    }

    private static void applyAnimatedPose(
            BoneModelSnapshot model,
            PhysicsBoneSelectionPlan plan
    ) {
        int[] ordinal = {0};
        for (BoneModelSnapshot.Bone bone : model.boneList()) {
            if (!plan.isDriven(bone)) {
                continue;
            }
            float sign = (ordinal[0]++ & 1) == 0 ? 1.0F : -1.0F;
            bone.setRotationX(bone.getRotationX() + sign * 0.013F);
            bone.setRotationY(bone.getRotationY() - sign * 0.009F);
            bone.setPositionX(bone.getPositionX() + sign * 0.05F);
            bone.setPositionZ(bone.getPositionZ() - sign * 0.03F);
        }
    }

    private record Transform(
            float rotationX,
            float rotationY,
            float rotationZ,
            float positionX,
            float positionY,
            float positionZ
    ) {
        static Transform capture(BoneModelSnapshot.Bone bone) {
            return new Transform(
                    bone.getRotationX(),
                    bone.getRotationY(),
                    bone.getRotationZ(),
                    bone.getPositionX(),
                    bone.getPositionY(),
                    bone.getPositionZ()
            );
        }

        boolean matches(BoneModelSnapshot.Bone bone) {
            return near(rotationX, bone.getRotationX())
                    && near(rotationY, bone.getRotationY())
                    && near(rotationZ, bone.getRotationZ())
                    && near(positionX, bone.getPositionX())
                    && near(positionY, bone.getPositionY())
                    && near(positionZ, bone.getPositionZ());
        }

        private static boolean near(float expected, float actual) {
            return Math.abs(expected - actual) <= EPSILON;
        }
    }
}
