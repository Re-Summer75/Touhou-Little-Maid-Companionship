package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.PreparedCollisionProxySet;
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
        if (context.constraintsEnabled) {
            context.animationMotion.sample(state, dt, paused);
        }
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            AnimatedGeoBone bone = node.bone();
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
                state,
                scratch
        );
        PreparedCollisionProxySet preparedCollisions =
                SpringCollisionCoordinator.prepareNode(
                        context,
                        nodeIndex,
                        rx,
                        ry,
                        rz
                );
        boolean corrected = false;
        if (context.constraintsEnabled
                && scratch.nextDirection.lengthSquared()
                > SpringBoneMath.EPSILON) {
            scratch.projectionStart.set(scratch.nextDirection);
            corrected = SpringConstraintProjector.project(
                    node.constraint(),
                    boneBaseOrientation,
                    preparedCollisions,
                    scratch.nextDirection,
                    SpringConstraintProjector.maximumSwing(
                            node,
                            scratch.runtimeSafetyScale
                    ),
                    state.currentDirections[node.drivenSlot()],
                    SpringConstraintProjector.maximumCorrection(dt),
                    scratch,
                    context.metrics
            );
            if (corrected) {
                SpringProjectionDamper.apply(
                        node.drivenSlot(),
                        scratch.projectionStart,
                        scratch.nextDirection,
                        scratch.collision.unresolved(),
                        dt,
                        state
                );
            }
        }
        state.lastIntegratorStep[node.drivenSlot()] =
                scratch.projectionStart.distance(
                        state.currentDirections[node.drivenSlot()]
                );
        state.lastProjectionStep[node.drivenSlot()] = corrected
                ? scratch.projectionStart.distance(scratch.nextDirection)
                : 0.0F;
        SpringContactSupport.record(node.drivenSlot(), dt, state, scratch);
        SpringDirectionIntegrator.commit(
                node.drivenSlot(),
                stepped,
                corrected,
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
