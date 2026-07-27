package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.SpringBoneSolver;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.forEachBone;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class InitialPoseStabilityVerification {
    private static final float EPSILON = 1.0E-6F;
    private static final int HELD_FRAMES = 600;
    private static final float HELD_STEP = 1.0F / 60.0F;
    /**
     * Angle, in radians, a driven segment may drift over ten seconds of
     * standing still. Nothing should move it at all, so this is only wide
     * enough to absorb float accumulation over six hundred steps.
     */
    private static final float HELD_DRIFT = 0.01F;

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
        AnimatedGeoModel model = new AnimatedGeoModel(loadGeoModel(modelPath));
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
        solver.reset();
        verifyHeld(fileName, layout, solver);
    }

    /**
     * The single zero-length step above proves the solver writes nothing before
     * it has integrated anything, which is a different claim from the pose
     * surviving once it does. Every constant influence in the solver shows up
     * only over time — gravity settling a spring away from where it was drawn,
     * a projection walking a panel out of an overlap the author drew it inside
     * — and each is invisible at {@code dt = 0}. So this spends real seconds
     * standing perfectly still, where the only correct answer is the pose the
     * model shipped with.
     */
    private static void verifyHeld(
            String label,
            PhysicsSolverLayout layout,
            SpringBoneSolver solver
    ) {
        /*
         * Segment directions rather than written-back bone angles. The Euler
         * triple the solver writes is not unique — a bone authored near half a
         * turn comes back decomposed the other way round, so (0.013, -3.1506,
         * 0) and (-3.1286, 0.009, 3.1416) are the same orientation and differ
         * in every channel. The direction is what the pose actually means.
         */
        int nodes = layout.activeNodeCount();
        Vector3f[] authored = new Vector3f[nodes];
        // A zero-length step seeds the springs without integrating anything.
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        for (int index = 0; index < nodes; index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (!node.driven()) {
                continue;
            }
            Vector3f direction = new Vector3f();
            if (solver.copyCurrentDirection(node.drivenSlot(), direction)) {
                authored[index] = direction;
            }
        }
        for (int frame = 0; frame < HELD_FRAMES; frame++) {
            solver.restoreAnimationPose();
            solver.solve(new Vector3f(), 0.0F, HELD_STEP, false);
        }
        Vector3f held = new Vector3f();
        for (int index = 0; index < nodes; index++) {
            if (authored[index] == null) {
                continue;
            }
            PhysicsSolverLayout.Node node = layout.node(index);
            require(
                    solver.copyCurrentDirection(node.drivenSlot(), held),
                    label + " stopped reporting " + node.bone().getName()
            );
            float drift = authored[index].angle(held);
            require(
                    drift <= HELD_DRIFT,
                    label + " drifted off the authored pose of "
                            + node.bone().getName()
                            + " while standing still by " + drift
                            + " rad (authored=" + authored[index]
                            + ", held=" + held + ", decision="
                            + node.decision() + ")"
            );
        }
    }

    private static void verifyUnchanged(
            String label,
            AnimatedGeoModel model,
            PhysicsBoneSelectionPlan plan,
            SpringBoneSolver solver
    ) {
        IdentityHashMap<AnimatedGeoBone, Transform> before =
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

    private static IdentityHashMap<AnimatedGeoBone, Transform> captureDriven(
            AnimatedGeoModel model,
            PhysicsBoneSelectionPlan plan
    ) {
        IdentityHashMap<AnimatedGeoBone, Transform> output =
                new IdentityHashMap<>();
        forEachBone(model, bone -> {
            if (plan.isDriven(bone)) {
                output.put(bone, Transform.capture(bone));
            }
        });
        return output;
    }

    private static void applyAnimatedPose(
            AnimatedGeoModel model,
            PhysicsBoneSelectionPlan plan
    ) {
        int[] ordinal = {0};
        forEachBone(model, bone -> {
            if (!plan.isDriven(bone)) {
                return;
            }
            float sign = (ordinal[0]++ & 1) == 0 ? 1.0F : -1.0F;
            bone.setRotationX(bone.getRotationX() + sign * 0.013F);
            bone.setRotationY(bone.getRotationY() - sign * 0.009F);
            bone.setPositionX(bone.getPositionX() + sign * 0.05F);
            bone.setPositionZ(bone.getPositionZ() - sign * 0.03F);
        });
    }

    private record Transform(
            float rotationX,
            float rotationY,
            float rotationZ,
            float positionX,
            float positionY,
            float positionZ
    ) {
        static Transform capture(AnimatedGeoBone bone) {
            return new Transform(
                    bone.getRotationX(),
                    bone.getRotationY(),
                    bone.getRotationZ(),
                    bone.getPositionX(),
                    bone.getPositionY(),
                    bone.getPositionZ()
            );
        }

        boolean matches(AnimatedGeoBone bone) {
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
