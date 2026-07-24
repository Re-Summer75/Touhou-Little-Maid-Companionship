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
            float yawRate,
            float dt,
            boolean paused
    ) {
        PhysicsSolverLayout layout = context.layout;
        SpringBoneState state = context.state;
        SpringBoneScratch scratch = context.scratch;
        scratch.rootOrientation.identity();
        context.metrics.beginFrame();
        SpringCollisionCoordinator.beginFrame(context);
        state.beginReferenceFrame(context.constraintsEnabled);
        boolean inlineAnimationOrientations =
                context.constraintsEnabled
                        && layout.referencesPreordered();
        if (context.constraintsEnabled
                && !inlineAnimationOrientations) {
            SpringOrientationComposer.collectAnimationOrientations(
                    layout,
                    state,
                    scratch
            );
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
            if (inlineAnimationOrientations) {
                Quaternionf animationParent = node.parentIndex() < 0
                        ? scratch.rootOrientation
                        : state.animationOrientations[node.parentIndex()];
                SpringOrientationComposer.composeBoth(
                        animationParent,
                        parentOrientation,
                        rx,
                        ry,
                        rz,
                        state.animationOrientations[index],
                        boneBaseOrientation,
                        scratch
                );
            } else {
                SpringOrientationComposer.compose(
                        parentOrientation,
                        rx,
                        ry,
                        rz,
                        boneBaseOrientation,
                        scratch
                );
            }

            if (node.driven()) {
                integrateAndApply(
                        context,
                        index,
                        node,
                        boneBaseOrientation,
                        modelAcceleration,
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
        int referenceIndex =
                node.constraint().referenceNodeIndex();
        Quaternionf referenceOrientation =
                !context.constraintsEnabled || referenceIndex < 0
                        ? scratch.rootOrientation
                        : state.animationOrientations[referenceIndex];

        SpringDirectionIntegrator.prepareRestDirection(
                node,
                boneBaseOrientation,
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
            corrected = SpringConstraintProjector.project(
                    node.constraint(),
                    boneBaseOrientation,
                    preparedCollisions,
                    scratch.nextDirection,
                    SpringConstraintProjector.maximumSwing(
                            node,
                            scratch.runtimeSafetyScale
                    ),
                    scratch,
                    context.metrics
            );
        }
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
