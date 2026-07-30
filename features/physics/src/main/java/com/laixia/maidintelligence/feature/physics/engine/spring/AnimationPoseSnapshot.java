package com.laixia.maidintelligence.feature.physics.engine.spring;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;

import java.util.Arrays;

/**
 * Saves the animation-only local pose that existed before physics wrote bones.
 *
 * <p>Gecko may rate-limit its controller update while hardcoded animations
 * still edit individual bones. Restoring this pose at the start of the next
 * render call makes the physics layer a reversible overlay instead of allowing
 * its previous rotations and pivot compensation to become animation input.
 */
final class AnimationPoseSnapshot {
    private final PhysicsSolverLayout layout;
    private final float[] rotationX;
    private final float[] rotationY;
    private final float[] rotationZ;
    private final float[] positionX;
    private final float[] positionY;
    private final float[] positionZ;
    private final boolean[] captured;

    AnimationPoseSnapshot(PhysicsSolverLayout layout) {
        this.layout = layout;
        int count = layout.drivenBoneCount();
        rotationX = new float[count];
        rotationY = new float[count];
        rotationZ = new float[count];
        positionX = new float[count];
        positionY = new float[count];
        positionZ = new float[count];
        captured = new boolean[count];
    }

    void capture(
            PhysicsSolverLayout.Node node,
            float rx,
            float ry,
            float rz,
            float px,
            float py,
            float pz
    ) {
        int slot = node.drivenSlot();
        rotationX[slot] = rx;
        rotationY[slot] = ry;
        rotationZ[slot] = rz;
        positionX[slot] = px;
        positionY[slot] = py;
        positionZ[slot] = pz;
        captured[slot] = true;
    }

    void restore() {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (!node.driven()) {
                continue;
            }
            int slot = node.drivenSlot();
            if (!captured[slot]) {
                continue;
            }
            BoneModelSnapshot.Bone bone = node.bone();
            bone.setRotationX(rotationX[slot]);
            bone.setRotationY(rotationY[slot]);
            bone.setRotationZ(rotationZ[slot]);
            bone.setPositionX(positionX[slot]);
            bone.setPositionY(positionY[slot]);
            bone.setPositionZ(positionZ[slot]);
        }
    }

    void reset() {
        Arrays.fill(captured, false);
    }
}
