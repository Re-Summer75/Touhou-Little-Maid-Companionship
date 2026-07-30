package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadWinefoxGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireVectorNear;

final class SolverEquivalenceVerification {
    private SolverEquivalenceVerification() {
    }

    static void run() throws Exception {
        verifyFixture(
                "winefox",
                loadWinefoxGeoModel(),
                PhysicsMetadata.EMPTY
        );
        verifyFixture(
                "anonymous",
                AnonymousGoldenFixture.geoModel(),
                AnonymousGoldenFixture.metadata()
        );
    }

    private static void verifyFixture(
            String label,
            GeoModel geoModel,
            PhysicsMetadata metadata
    ) {
        BoneModelSnapshot referenceModel = coreModel(geoModel);
        BoneModelSnapshot optimizedModel = coreModel(geoModel);
        PhysicsBoneSelectionPlan referencePlan =
                PhysicsBoneDiscoverer.discover(
                        "verification:" + label,
                        referenceModel,
                        metadata
                );
        PhysicsBoneSelectionPlan optimizedPlan =
                PhysicsBoneDiscoverer.discover(
                        "verification:" + label,
                        optimizedModel,
                        metadata
                );
        ReferenceSpringBoneSolver reference =
                new ReferenceSpringBoneSolver(referenceModel, referencePlan);
        PhysicsSolverLayout layout =
                PhysicsSolverLayout.build(optimizedModel, optimizedPlan);
        SpringBoneSolver optimized = new SpringBoneSolver(layout, false);
        require(
                layout.drivenBoneCount() > 0,
                label + " golden fixture contains no driven bones"
        );

        boolean hasPivotCompensation = false;
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            hasPivotCompensation |= node.driven()
                    && node.kinematics().compensatesPivot();
        }
        require(
                hasPivotCompensation,
                label + " golden fixture does not cover pivot compensation"
        );

        Vector3f acceleration = new Vector3f();
        Vector3f expectedDirection = new Vector3f();
        Vector3f actualDirection = new Vector3f();
        for (int frame = 0; frame < 240; frame++) {
            GoldenMotionFixture.applyAnimationPose(referenceModel, frame);
            GoldenMotionFixture.applyAnimationPose(optimizedModel, frame);
            GoldenMotionFixture.motion(frame, acceleration);
            float yawRate = GoldenMotionFixture.yawRate(frame);
            float dt = GoldenMotionFixture.deltaSeconds(frame);
            boolean paused = frame >= 96 && frame < 108
                    || frame == 171;

            reference.solve(acceleration, yawRate, dt, paused);
            optimized.solve(acceleration, yawRate, dt, paused);
            GoldenMotionFixture.compareModelPose(
                    label,
                    frame,
                    referenceModel,
                    optimizedModel,
                    1.0E-5F
            );
            verifySpringDirections(
                    label,
                    frame,
                    layout,
                    reference,
                    optimized,
                    referenceModel,
                    expectedDirection,
                    actualDirection
            );
            require(
                    optimized.lastVisitedNodeCount()
                            == layout.activeNodeCount(),
                    label + " solver visited an unexpected node count"
            );
        }
    }

    private static void verifySpringDirections(
            String label,
            int frame,
            PhysicsSolverLayout layout,
            ReferenceSpringBoneSolver reference,
            SpringBoneSolver optimized,
            BoneModelSnapshot referenceModel,
            Vector3f expectedDirection,
            Vector3f actualDirection
    ) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (!node.driven()) {
                continue;
            }
            BoneModelSnapshot.Bone referenceBone =
                    referenceModel.bone(node.bone().index());
            boolean expectedInitialized = reference.copyCurrentDirection(
                    referenceBone,
                    expectedDirection
            );
            boolean actualInitialized = optimized.copyCurrentDirection(
                    node.drivenSlot(),
                    actualDirection
            );
            require(
                    expectedInitialized == actualInitialized,
                    label + " spring initialization diverged at frame "
                            + frame + " for " + node.path()
            );
            requireVectorNear(
                    actualDirection,
                    expectedDirection,
                    1.0E-5F,
                    label + " spring direction diverged at frame "
                            + frame + " for " + node.path()
            );
        }
    }
}
