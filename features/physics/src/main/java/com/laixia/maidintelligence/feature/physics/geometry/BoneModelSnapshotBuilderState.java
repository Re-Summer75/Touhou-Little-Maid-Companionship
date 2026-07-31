package com.laixia.maidintelligence.feature.physics.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable bake-time state kept behind {@link BoneModelSnapshot.Builder}.
 */
final class BoneModelSnapshotBuilderState {
    private final List<BoneModelSnapshot.Bone> roots = new ArrayList<>();
    private final List<BoneModelSnapshot.Bone> bones = new ArrayList<>();
    private BoneModelSnapshot.Bone head;
    private BoneModelSnapshot.Bone leftArm;
    private BoneModelSnapshot.Bone rightArm;
    private List<BoneModelSnapshot.Bone> leftHandBones = List.of();
    private List<BoneModelSnapshot.Bone> rightHandBones = List.of();
    private List<BoneModelSnapshot.Bone> leftWaistBones = List.of();
    private List<BoneModelSnapshot.Bone> rightWaistBones = List.of();
    private List<BoneModelSnapshot.Bone> backpackBones = List.of();
    private List<BoneModelSnapshot.Bone> tacPistolBones = List.of();
    private List<BoneModelSnapshot.Bone> tacRifleBones = List.of();

    BoneModelSnapshot.Bone addBone(
            BoneModelSnapshot.Bone parent,
            String name,
            float pivotX,
            float pivotY,
            float pivotZ,
            BoneModelSnapshot.RestPose restPose,
            BoneModelSnapshot.RestGeometry geometry
    ) {
        BoneModelSnapshot.Bone bone = new BoneModelSnapshot.Bone(
                bones.size(),
                parent,
                name,
                pivotX,
                pivotY,
                pivotZ,
                restPose,
                geometry
        );
        bones.add(bone);
        if (parent == null) {
            roots.add(bone);
        } else {
            parent.addChild(bone);
        }
        return bone;
    }

    void head(BoneModelSnapshot.Bone value) {
        head = value;
    }

    void leftArm(BoneModelSnapshot.Bone value) {
        leftArm = value;
    }

    void rightArm(BoneModelSnapshot.Bone value) {
        rightArm = value;
    }

    void leftHandBones(List<BoneModelSnapshot.Bone> value) {
        leftHandBones = List.copyOf(value);
    }

    void rightHandBones(List<BoneModelSnapshot.Bone> value) {
        rightHandBones = List.copyOf(value);
    }

    void leftWaistBones(List<BoneModelSnapshot.Bone> value) {
        leftWaistBones = List.copyOf(value);
    }

    void rightWaistBones(List<BoneModelSnapshot.Bone> value) {
        rightWaistBones = List.copyOf(value);
    }

    void backpackBones(List<BoneModelSnapshot.Bone> value) {
        backpackBones = List.copyOf(value);
    }

    void tacPistolBones(List<BoneModelSnapshot.Bone> value) {
        tacPistolBones = List.copyOf(value);
    }

    void tacRifleBones(List<BoneModelSnapshot.Bone> value) {
        tacRifleBones = List.copyOf(value);
    }

    BoneModelSnapshot build() {
        for (BoneModelSnapshot.Bone bone : bones) {
            bone.freeze();
        }
        return new BoneModelSnapshot(this);
    }

    List<BoneModelSnapshot.Bone> roots() {
        return roots;
    }

    List<BoneModelSnapshot.Bone> bones() {
        return bones;
    }

    BoneModelSnapshot.Bone head() {
        return head;
    }

    BoneModelSnapshot.Bone leftArm() {
        return leftArm;
    }

    BoneModelSnapshot.Bone rightArm() {
        return rightArm;
    }

    List<BoneModelSnapshot.Bone> leftHandBones() {
        return leftHandBones;
    }

    List<BoneModelSnapshot.Bone> rightHandBones() {
        return rightHandBones;
    }

    List<BoneModelSnapshot.Bone> leftWaistBones() {
        return leftWaistBones;
    }

    List<BoneModelSnapshot.Bone> rightWaistBones() {
        return rightWaistBones;
    }

    List<BoneModelSnapshot.Bone> backpackBones() {
        return backpackBones;
    }

    List<BoneModelSnapshot.Bone> tacPistolBones() {
        return tacPistolBones;
    }

    List<BoneModelSnapshot.Bone> tacRifleBones() {
        return tacRifleBones;
    }
}
