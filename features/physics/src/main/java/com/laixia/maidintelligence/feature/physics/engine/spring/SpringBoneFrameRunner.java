package com.laixia.maidintelligence.feature.physics.engine.spring;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProjection;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class SpringBoneFrameRunner {
    private SpringBoneFrameRunner() {
    }
    static void solve(
            SpringBoneContext context,
            Vector3f modelAcceleration,
            Vector3f modelPoseDrive,
            float yawRate,
            float dt,
            boolean paused
    ) {
        PhysicsSolverLayout layout = context.layout;
        SpringBoneState state = context.state;
        SpringBoneScratch scratch = context.scratch;
        scratch.rootOrientation.identity();
        float turbulenceStep = !paused && dt > SpringBoneMath.EPSILON
                ? dt
                : 0.0F;
        context.metrics.beginFrame();
        SpringCollisionCoordinator.beginFrame(context, dt);
        state.beginReferenceFrame(context.constraintsEnabled);
        state.skirtCoupling.beginFrame();
        if (context.constraintsEnabled) {
            context.animationMotion.sample(state, dt, paused);
        }
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            BoneModelSnapshot.Bone bone = node.bone();
            float rx = bone.getRotationX();
            float ry = bone.getRotationY();
            float rz = bone.getRotationZ();
            float px = bone.getPositionX();
            float py = bone.getPositionY();
            float pz = bone.getPositionZ();
            Quaternionf parentOrientation = node.parentIndex() < 0
                    ? scratch.rootOrientation
                    : state.renderedOrientations[node.parentIndex()];
            Quaternionf boneBaseOrientation =
                    state.renderedOrientations[index];
            SpringOrientationComposer.compose(
                    parentOrientation,
                    rx,
                    ry,
                    rz,
                    boneBaseOrientation,
                    scratch
            );

            if (node.driven()) {
                integrateAndApply(
                        context,
                        index,
                        node,
                        boneBaseOrientation,
                        modelAcceleration,
                        modelPoseDrive,
                        turbulenceStep,
                        yawRate,
                        dt,
                        paused,
                        rx,
                        ry,
                        rz,
                        px,
                        py,
                        pz
                );
                SpringOrientationComposer.compose(
                        parentOrientation,
                        bone.getRotationX(),
                        bone.getRotationY(),
                        bone.getRotationZ(),
                        boneBaseOrientation,
                        scratch
                );
            }
            context.endpoints.update(index, scratch.localRotation);
            context.metrics.visitNode();
        }
    }

    private static void integrateAndApply(
            SpringBoneContext context,
            int nodeIndex,
            PhysicsSolverLayout.Node node,
            Quaternionf boneBaseOrientation,
            Vector3f modelAcceleration,
            Vector3f modelPoseDrive,
            float turbulenceStep,
            float yawRate,
            float dt,
            boolean paused,
            float rx,
            float ry,
            float rz,
            float px,
            float py,
            float pz
    ) {
        SpringBoneState state = context.state;
        SpringBoneScratch scratch = context.scratch;
        context.animationPose.capture(
                node,
                rx,
                ry,
                rz,
                px,
                py,
                pz
        );
        int referenceIndex =
                node.constraint().referenceNodeIndex();
        Quaternionf referenceOrientation =
                !context.constraintsEnabled || referenceIndex < 0
                        ? scratch.rootOrientation
                        : state.animationOrientations[referenceIndex];

        SpringDirectionIntegrator.prepareRestDirection(
                node,
                boneBaseOrientation,
                modelPoseDrive,
                turbulenceStep,
                state,
                scratch
        );
        if (context.constraintsEnabled) {
            SpringReferenceTransport.apply(
                    node.constraint(),
                    node.drivenSlot(),
                    referenceOrientation,
                    state,
                    scratch
            );
        }
        boolean stepped = SpringDirectionIntegrator.integrate(
                node,
                modelAcceleration,
                context.animationMotion.acceleration(node.drivenSlot()),
                yawRate,
                dt,
                paused,
                context.constraintsEnabled,
                state,
                scratch
        );
        CollisionProjection preparedCollisions =
                SpringCollisionCoordinator.prepareNode(
                        context,
                        nodeIndex,
                        rx,
                        ry,
                        rz
                );
        boolean recoveryLimited = context.constraintsEnabled
                && stepped
                && SpringAuthoredPoseAnchor.undriven(
                        modelAcceleration,
                        context.animationMotion.acceleration(
                                node.drivenSlot()
                        ),
                        yawRate,
                        scratch.poseDriveBias
                )
                && SpringAuthoredPoseAnchor.limitRecovery(
                        state.integration.currentDirections[node.drivenSlot()],
                        scratch.nextDirection,
                        scratch.authoredRestDirection,
                        node.kinematics().leverArm()
                                * scratch.runtimeSafetyScale,
                        dt
                );
        scratch.integratedDirection.set(scratch.nextDirection);
        boolean corrected = false;
        /*
         * Keep the pre-projection direction even when no constraint runs. The
         * damper needs those quiet frames to age its histories and recognise a
         * collider ejecting a segment again after a gap.
         */
        scratch.projectionStart.set(scratch.nextDirection);
        float maximumSwing = SpringConstraintProjector.maximumSwing(
                node,
                scratch.runtimeSafetyScale
        );
        if (context.constraintsEnabled
                && scratch.nextDirection.lengthSquared()
                > SpringBoneMath.EPSILON) {
            corrected = SpringConstraintProjector.project(
                    node.constraint(),
                    boneBaseOrientation,
                    preparedCollisions,
                    scratch.nextDirection,
                    maximumSwing,
                    state.integration.currentDirections[node.drivenSlot()],
                    SpringConstraintProjector.maximumCorrection(
                            dt, scratch.runtimeSegmentLength
                    ),
                    scratch,
                    context.metrics
            );
        }
        boolean coupled = context.constraintsEnabled
                && SpringSkirtBranchCoupler.apply(
                context,
                nodeIndex,
                paused ? 0.0F : dt
        );
        if (coupled) {
            context.metrics.recordConstraintProjection();
            corrected |= SpringConstraintProjector.reprojectAfterBranch(
                    preparedCollisions,
                    scratch.nextDirection,
                    scratch.authoredRestDirection,
                    maximumSwing,
                    scratch,
                    context.metrics
            );
        }
        if (context.constraintsEnabled
                && stepped
                && scratch.poseDriveBias.lengthSquared() > 1.0E-8F
                && SpringAuthoredPoseAnchor.recoverPenetration(
                        scratch.nextDirection,
                        scratch.authoredRestDirection,
                        preparedCollisions,
                        scratch.collision
                )) {
            /*
             * A suppressed collider may otherwise leave a steady gust balanced
             * inside the body. The legal authored target wins that conflict.
             */
            SpringContactSupport.clear(node.drivenSlot(), state);
            scratch.collisionCorrected = true;
            scratch.collisionNormal.zero();
            scratch.collision.setUnresolved(true);
            scratch.collision.setRestClearance(Float.MAX_VALUE);
            corrected = true;
            context.metrics.recordConstraintProjection();
        }
        scratch.projectedDirection.set(scratch.nextDirection);
        boolean suppressResponder = false;
        if (stepped || corrected) {
            suppressResponder = SpringProjectionDamper.apply(
                    node.drivenSlot(),
                    scratch.projectionStart,
                    scratch.nextDirection,
                    corrected && scratch.collision.unresolved(),
                    scratch.collisionCorrected,
                    dt,
                    state
            );
        }
        if (preparedCollisions.resolveRecurringContact(
                suppressResponder,
                scratch.projectedDirection,
                scratch.collision
        )) {
            // The selected responder keeps its answer; only the colliders that
            // answer would enter are silenced.
            scratch.nextDirection.set(scratch.projectedDirection);
            SpringProjectionDamper.competitorsSuppressed(
                    node.drivenSlot(),
                    state
            );
        }
        if (SpringInterlockRecovery.apply(
                node.drivenSlot(),
                state.integration.currentDirections[node.drivenSlot()],
                scratch.projectionStart,
                scratch.nextDirection,
                scratch.authoredRestDirection,
                node.kinematics().leverArm() * scratch.runtimeSafetyScale,
                scratch.collisionCorrected,
                dt,
                preparedCollisions,
                scratch.collision,
                state.recovery
        )) {
            /*
             * The recovery answer deliberately crosses the obsolete contact
             * path. Its support normal and oscillation history cannot follow it
             * to the legal target or they would recreate the same lock.
             */
            SpringContactSupport.clear(node.drivenSlot(), state);
            SpringProjectionDamper.clear(node.drivenSlot(), state);
            scratch.collisionCorrected = false;
            scratch.collisionNormal.zero();
            scratch.collision.setUnresolved(false);
            scratch.collision.setRestClearance(Float.MAX_VALUE);
            corrected = true;
        }
        boolean integrationDamped = context.constraintsEnabled
                && SpringIntegrationDamper.apply(
                        node,
                        state.integration.currentDirections[node.drivenSlot()],
                        scratch.nextDirection,
                        stepped,
                        corrected || coupled || recoveryLimited,
                        dt,
                        state
                );
        state.integration.lastIntegratorStep[node.drivenSlot()] =
                scratch.integratedDirection.distance(
                        state.integration.currentDirections[node.drivenSlot()]
                );
        state.integration.lastProjectionStep[node.drivenSlot()] =
                corrected || coupled
                ? scratch.integratedDirection.distance(scratch.nextDirection)
                : 0.0F;
        /*
         * Which bound objected, per segment. The projection counters are totalled
         * across the model, so a single segment's trace reads the whole skirt's
         * activity and cannot say what is pushing the one bone in question.
         */
        state.integration.lastProjectionSource[node.drivenSlot()] =
                (scratch.swingCorrected ? 1 : 0)
                        | (scratch.collisionCorrected ? 2 : 0)
                        | (coupled ? 4 : 0);
        SpringContactSupport.record(node.drivenSlot(), dt, state, scratch);
        SpringDirectionIntegrator.commit(
                node.drivenSlot(),
                stepped,
                corrected || coupled || recoveryLimited || integrationDamped,
                dt,
                state,
                scratch
        );
        SpringDeflectionApplier.apply(
                context.constraintsEnabled,
                node,
                boneBaseOrientation,
                rx,
                ry,
                rz,
                px,
                py,
                pz,
                state,
                scratch,
                context.metrics
        );
    }
}
