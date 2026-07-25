package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.snapshot.BoneSnapshot;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionScratch;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.PreparedCollisionProxy;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireVectorNear;

final class RuntimeEndpointHierarchyVerification {
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private RuntimeEndpointHierarchyVerification() {
    }

    static void run() {
        AnimatedGeoModel model = RuntimeEndpointHierarchyFixture.model();
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:runtime_endpoints",
                model,
                RuntimeEndpointHierarchyFixture.metadata()
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        int parentIndex = indexOf(layout, "ParentHair");
        int childIndex = indexOf(layout, "ChildHair");
        require(parentIndex >= 0 && childIndex >= 0,
                "Runtime endpoint fixture lost its chain");
        require(layout.node(parentIndex).driven()
                        && layout.node(childIndex).driven()
                        && layout.node(childIndex).parentIndex() == parentIndex,
                "Runtime endpoint fixture has no direct driven hierarchy");
        require(layout.node(parentIndex).kinematics().compensatesPivot(),
                "Runtime endpoint fixture did not cover a virtual pivot");

        Vector3f actual = new Vector3f();
        require(!solver.copyRuntimePivot(childIndex, actual),
                "Runtime pivot existed before the first solve");
        applyAnimationPose(model);
        solver.solve(new Vector3f(), 0.0F, 1.0F / 60.0F, true);
        Vector3f baselineChildPivot = new Vector3f();
        require(solver.copyRuntimePivot(childIndex, baselineChildPivot),
                "Paused initialization did not publish runtime pivots");

        Vector3f acceleration = new Vector3f(0.30F, 0.0F, -0.08F);
        for (int frame = 0; frame < 90; frame++) {
            applyAnimationPose(model);
            solver.solve(acceleration, 0.0F, 1.0F / 60.0F, false);
        }
        require(solver.lastPeakDeflection() > 0.05F,
                "Parent chain did not receive a physical deflection");
        require(solver.copyRuntimePivot(childIndex, actual)
                        && actual.distance(baselineChildPivot) > 0.01F,
                "Parent physical deflection did not move the child pivot");
        verifyAffineEndpoints(layout, solver);

        solver.reset();
        require(!solver.copyRuntimePivot(childIndex, actual)
                        && !solver.copyRuntimeTip(childIndex, actual),
                "Solver reset retained stale runtime endpoints");
        verifiesRuntimeSegmentCollision();
    }

    private static void verifiesRuntimeSegmentCollision() {
        AnimatedGeoModel model = RuntimeEndpointHierarchyFixture.model();
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:runtime_segment_collision",
                        model,
                        RuntimeEndpointHierarchyFixture.collisionMetadata()
                )
        );
        int parentIndex = indexOf(layout, "ParentHair");
        int childIndex = indexOf(layout, "ChildHair");
        require(parentIndex >= 0 && childIndex >= 0,
                "Runtime segment collision chain is missing");
        require(layout.node(parentIndex).constraint()
                        .collisionProxies().proxyCount() == 0
                        && layout.node(childIndex).constraint()
                        .collisionProxies().proxyCount() == 1,
                "Runtime segment fixture did not isolate child collision");
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        applyAnimationPose(model);
        solver.solve(new Vector3f(), 0.0F, 1.0F / 60.0F, true);
        Vector3f parentRest = new Vector3f();
        require(solver.copyCurrentDirection(
                        layout.node(parentIndex).drivenSlot(),
                        parentRest
                ), "Runtime collision parent did not initialize");
        int projections = 0;
        for (int frame = 0; frame < 120; frame++) {
            applyAnimationPose(model);
            solver.solve(
                    new Vector3f(0.45F, 0.0F, -0.12F),
                    0.0F,
                    1.0F / 60.0F,
                    false
            );
            projections += solver.lastCollisionProjectionCount();
        }
        Vector3f parentDirection = new Vector3f();
        solver.copyCurrentDirection(
                layout.node(parentIndex).drivenSlot(),
                parentDirection
        );
        require(parentDirection.distance(parentRest) > 0.03F,
                "Parent driven segment did not physically deflect");
        require(projections > 0,
                "Child segment runtime collision never projected");

        Vector3f pivot = new Vector3f();
        Vector3f direction = new Vector3f();
        require(solver.copyRuntimePivot(childIndex, pivot)
                        && solver.copyCurrentDirection(
                        layout.node(childIndex).drivenSlot(),
                        direction
                ), "Child runtime collision state is unavailable");
        CollisionProxy baked = layout.node(childIndex).constraint()
                .collisionProxies().proxy(0);
        PreparedCollisionProxy prepared = new PreparedCollisionProxy();
        baked.copyStaticShape(
                new Quaternionf(),
                prepared,
                new CollisionScratch()
        );
        prepared.prepare(pivot, new Matrix4f(), new Matrix3f(), 1.0F);
        float clearance = prepared.clearance(
                direction,
                new CollisionScratch()
        );
        require(clearance >= -2.0E-4F,
                "Child segment retained illegal runtime clearance: "
                        + clearance + ", pivot=" + pivot
                        + ", direction=" + direction);

        solver.reset();
        require(!solver.copyRuntimePivot(childIndex, pivot),
                "Runtime collision reset retained a stale pivot");
        applyAnimationPose(model);
        solver.solve(new Vector3f(), 0.0F, 1.0F / 60.0F, true);
        require(solver.lastCollisionProjectionCount() > 0,
                "Runtime collision cache did not rebuild after reset");
    }

    private static void verifyAffineEndpoints(
            PhysicsSolverLayout layout,
            SpringBoneSolver solver
    ) {
        Matrix4f identity = new Matrix4f();
        Matrix4f[] transforms = new Matrix4f[layout.activeNodeCount()];
        Quaternionf rotation = new Quaternionf();
        Vector3f localPoint = new Vector3f();
        Vector3f expected = new Vector3f();
        Vector3f actual = new Vector3f();
        Vector3f axis = new Vector3f();
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            AnimatedGeoBone bone = node.bone();
            Matrix4f parent = node.parentIndex() < 0
                    ? identity
                    : transforms[node.parentIndex()];
            float px = bone.getPivotX() / PIXELS_PER_BLOCK;
            float py = bone.getPivotY() / PIXELS_PER_BLOCK;
            float pz = bone.getPivotZ() / PIXELS_PER_BLOCK;
            transforms[index] = new Matrix4f(parent)
                    .translate(
                            -bone.getPositionX() / PIXELS_PER_BLOCK,
                            bone.getPositionY() / PIXELS_PER_BLOCK,
                            bone.getPositionZ() / PIXELS_PER_BLOCK
                    )
                    .translate(px, py, pz)
                    .rotate(rotation.identity().rotateZYX(
                            bone.getRotationZ(),
                            bone.getRotationY(),
                            bone.getRotationX()
                    ))
                    .scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ())
                    .translate(-px, -py, -pz);
            if (node.driven()) {
                localPoint.set(node.kinematics().effectivePivot());
            } else {
                localPoint.set(px, py, pz);
            }
            transforms[index].transformPosition(localPoint, expected);
            require(solver.copyRuntimePivot(index, actual),
                    "Runtime pivot missing for " + node.path());
            requireVectorNear(actual, expected, 1.0E-5F,
                    "Hierarchical runtime pivot diverged for " + node.path());
            if (!node.driven()) {
                require(!solver.copyRuntimeTip(index, actual),
                        "Rigid ancestor exposed a flexible tip");
                continue;
            }
            node.kinematics().axisInto(axis);
            localPoint.fma(
                    node.kinematics().segmentLength()
                            / PIXELS_PER_BLOCK,
                    axis
            );
            transforms[index].transformPosition(localPoint, expected);
            require(solver.copyRuntimeTip(index, actual),
                    "Runtime tip missing for " + node.path());
            requireVectorNear(actual, expected, 1.0E-5F,
                    "Hierarchical runtime tip diverged for " + node.path());
        }
    }

    private static void applyAnimationPose(AnimatedGeoModel model) {
        for (AnimatedGeoBone root : model.topLevelBones()) {
            resetBone(root);
        }
        AnimatedGeoBone head = find(model, "Head");
        AnimatedGeoBone parent = find(model, "ParentHair");
        require(head != null && parent != null,
                "Runtime endpoint animation bones are missing");
        head.setRotationX(0.18F);
        head.setRotationZ(-0.12F);
        head.setPositionX(1.50F);
        head.setPositionY(0.75F);
        head.setScaleX(1.20F);
        head.setScaleY(0.82F);
        head.setScaleZ(1.08F);
        parent.setScaleX(0.90F);
        parent.setScaleY(1.15F);
        parent.setScaleZ(1.05F);
    }

    private static void resetBone(AnimatedGeoBone bone) {
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
            resetBone(child);
        }
    }

    private static int indexOf(PhysicsSolverLayout layout, String name) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).bone().getName().equals(name)) {
                return index;
            }
        }
        return -1;
    }

    private static AnimatedGeoBone find(AnimatedGeoModel model, String name) {
        for (AnimatedGeoBone root : model.topLevelBones()) {
            AnimatedGeoBone found = find(root, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static AnimatedGeoBone find(AnimatedGeoBone bone, String name) {
        if (bone.getName().equals(name)) {
            return bone;
        }
        for (AnimatedGeoBone child : bone.children()) {
            AnimatedGeoBone found = find(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
