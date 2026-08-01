package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.discovery.PhysicsBoneDiscoverer;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadata;
import org.joml.Vector3f;

import java.nio.file.Path;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Guards the authored silhouette against sustained loads and long recovery.
 */
final class AuthoredPoseRecoveryVerification {
    private static final String MODEL = "winefox.json";
    private static final float STEP = 1.0F / 60.0F;
    private static final int STATIC_FRAMES = 180;
    private static final int SHOVE_FRAMES = 12;
    private static final int RECOVERY_FRAMES = 180;
    private static final float MAX_STATIC_OFFSET_PIXELS = 0.08F;
    private static final float MIN_EXCURSION_PIXELS = 0.50F;
    private static final float MAX_RECOVERED_OFFSET_PIXELS = 0.20F;
    private static final Vector3f ZERO = new Vector3f();

    private AuthoredPoseRecoveryVerification() {
    }

    static void run() throws Exception {
        Path modelPath = MODEL_DIRECTORY.resolve(MODEL);
        BoneModelSnapshot model = coreModel(loadGeoModel(modelPath));
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:authored_pose_recovery",
                model,
                PhysicsMetadata.EMPTY
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);

        for (int frame = 0; frame < STATIC_FRAMES; frame++) {
            step(solver, ZERO);
        }
        Measurement staticPose = maximumOffset(layout, solver);
        require(
                staticPose.pixels() <= MAX_STATIC_OFFSET_PIXELS,
                "Static gravity pulled " + MODEL + " " + staticPose.pixels()
                        + " px away from its authored pose"
        );

        Vector3f shove = new Vector3f(16.0F, 3.0F, 12.0F);
        float excursion = 0.0F;
        for (int frame = 0; frame < SHOVE_FRAMES; frame++) {
            step(solver, shove);
            excursion = Math.max(
                    excursion,
                    maximumOffset(layout, solver).pixels()
            );
        }
        for (int frame = 0; frame < RECOVERY_FRAMES; frame++) {
            step(solver, ZERO);
        }
        Measurement recovered = maximumOffset(layout, solver);
        System.out.printf(
                "authored pose: static %.3f px (%s), excursion %.3f px,"
                        + " recovered %.3f px (%s)%n",
                staticPose.pixels(),
                staticPose.bone(),
                excursion,
                recovered.pixels(),
                recovered.bone()
        );
        require(
                excursion >= MIN_EXCURSION_PIXELS,
                "Recovery fixture did not displace " + MODEL
        );
        require(
                recovered.pixels() <= MAX_RECOVERED_OFFSET_PIXELS,
                "A large motion left " + MODEL + " " + recovered.pixels()
                        + " px away from its authored pose"
        );
    }

    private static void step(
            SpringBoneSolver solver,
            Vector3f acceleration
    ) {
        solver.restoreAnimationPose();
        solver.solve(acceleration, 0.0F, STEP, false);
    }

    private static Measurement maximumOffset(
            PhysicsSolverLayout layout,
            SpringBoneSolver solver
    ) {
        float maximum = 0.0F;
        String worstBone = "-";
        Vector3f current = new Vector3f();
        Vector3f rest = new Vector3f();
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (!node.driven()
                    || !solver.copyCurrentDirection(
                            node.drivenSlot(), current)
                    || !solver.copyRestDirection(node.drivenSlot(), rest)) {
                continue;
            }
            float dot = Math.max(-1.0F, Math.min(1.0F, current.dot(rest)));
            float angle = (float) Math.acos(dot);
            float offset = angle * node.kinematics().leverArm() * 16.0F;
            if (offset > maximum) {
                maximum = offset;
                worstBone = node.bone().getName();
            }
        }
        return new Measurement(maximum, worstBone);
    }

    private record Measurement(float pixels, String bone) {
    }
}
