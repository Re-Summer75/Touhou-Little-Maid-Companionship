package com.laixia.maidintelligence.feature.physics.geometry;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Platform-neutral skeleton, rest geometry, and mutable animation pose.
 *
 * <p>Adapters build this once per animated model. Runtime pose synchronization
 * updates the existing bone fields in place, so the solver never retains a
 * Gecko, Minecraft, or renderer object and never allocates per frame.
 */
public final class BoneModelSnapshot {
    private final List<Bone> topLevelBones;
    private final List<Bone> boneList;
    private final Map<String, Bone> bones;
    private final Bone head;
    private final Bone leftArm;
    private final Bone rightArm;
    private final List<Bone> leftHandBones;
    private final List<Bone> rightHandBones;
    private final List<Bone> leftWaistBones;
    private final List<Bone> rightWaistBones;
    private final List<Bone> backpackBones;
    private final List<Bone> tacPistolBones;
    private final List<Bone> tacRifleBones;

    BoneModelSnapshot(BoneModelSnapshotBuilderState state) {
        this.topLevelBones = List.copyOf(state.roots());
        this.boneList = List.copyOf(state.bones());
        Map<String, Bone> byName = new LinkedHashMap<>();
        for (Bone bone : boneList) {
            byName.put(bone.getName(), bone);
        }
        this.bones = Map.copyOf(byName);
        this.head = state.head();
        this.leftArm = state.leftArm();
        this.rightArm = state.rightArm();
        this.leftHandBones = List.copyOf(state.leftHandBones());
        this.rightHandBones = List.copyOf(state.rightHandBones());
        this.leftWaistBones = List.copyOf(state.leftWaistBones());
        this.rightWaistBones = List.copyOf(state.rightWaistBones());
        this.backpackBones = List.copyOf(state.backpackBones());
        this.tacPistolBones = List.copyOf(state.tacPistolBones());
        this.tacRifleBones = List.copyOf(state.tacRifleBones());
    }

    public List<Bone> topLevelBones() {
        return topLevelBones;
    }
    public Map<String, Bone> bones() {
        return bones;
    }
    public List<Bone> boneList() {
        return boneList;
    }
    public Bone bone(int index) {
        return boneList.get(index);
    }
    public int boneCount() {
        return boneList.size();
    }
    public Bone head() {
        return head;
    }
    public Bone leftArm() {
        return leftArm;
    }
    public Bone rightArm() {
        return rightArm;
    }
    public List<Bone> leftHandBones() {
        return leftHandBones;
    }
    public List<Bone> rightHandBones() {
        return rightHandBones;
    }
    public List<Bone> leftWaistBones() {
        return leftWaistBones;
    }

    public List<Bone> rightWaistBones() {
        return rightWaistBones;
    }

    public List<Bone> backpackBones() {
        return backpackBones;
    }

    public List<Bone> tacPistolBones() {
        return tacPistolBones;
    }

    public List<Bone> tacRifleBones() {
        return tacRifleBones;
    }

    public Bone firstBoneNamed(String name) {
        for (Bone bone : boneList) {
            if (bone.getName().equals(name)) {
                return bone;
            }
        }
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final BoneModelSnapshotBuilderState state =
                new BoneModelSnapshotBuilderState();

        public Bone addBone(
                Bone parent,
                String name,
                float pivotX,
                float pivotY,
                float pivotZ,
                RestPose restPose,
                RestGeometry geometry
        ) {
            return state.addBone(
                    parent, name, pivotX, pivotY, pivotZ, restPose, geometry
            );
        }

        public Builder head(Bone value) {
            state.head(value);
            return this;
        }

        public Builder leftArm(Bone value) {
            state.leftArm(value);
            return this;
        }

        public Builder rightArm(Bone value) {
            state.rightArm(value);
            return this;
        }

        public Builder leftHandBones(List<Bone> value) {
            state.leftHandBones(value);
            return this;
        }

        public Builder rightHandBones(List<Bone> value) {
            state.rightHandBones(value);
            return this;
        }

        public Builder leftWaistBones(List<Bone> value) {
            state.leftWaistBones(value);
            return this;
        }

        public Builder rightWaistBones(List<Bone> value) {
            state.rightWaistBones(value);
            return this;
        }

        public Builder backpackBones(List<Bone> value) {
            state.backpackBones(value);
            return this;
        }

        public Builder tacPistolBones(List<Bone> value) {
            state.tacPistolBones(value);
            return this;
        }

        public Builder tacRifleBones(List<Bone> value) {
            state.tacRifleBones(value);
            return this;
        }

        public BoneModelSnapshot build() {
            return state.build();
        }
    }

    public static final class Bone extends BoneRuntimePose {
        private final int index;
        private final Bone parent;
        private final String name;
        private final float pivotX;
        private final float pivotY;
        private final float pivotZ;
        private final RestPose initialSnapshot;
        private final RestGeometry geometry;
        private final List<Bone> mutableChildren = new ArrayList<>();
        private List<Bone> children = List.of();

        Bone(
                int index,
                Bone parent,
                String name,
                float pivotX,
                float pivotY,
                float pivotZ,
                RestPose restPose,
                RestGeometry geometry
        ) {
            super(
                    restPose,
                    !Boolean.TRUE.equals(
                            Objects.requireNonNull(geometry, "geometry")
                                    .dontRender()
                    )
            );
            this.index = index;
            this.parent = parent;
            this.name = Objects.requireNonNull(name, "name");
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
            this.initialSnapshot = Objects.requireNonNull(restPose, "restPose");
            this.geometry = geometry;
        }

        void addChild(Bone child) {
            mutableChildren.add(child);
        }

        void freeze() {
            children = Collections.unmodifiableList(
                    new ArrayList<>(mutableChildren)
            );
            mutableChildren.clear();
        }

        public int index() {
            return index;
        }

        public Bone parent() {
            return parent;
        }

        public String getName() {
            return name;
        }

        public List<Bone> children() {
            return children;
        }

        public RestGeometry geometry() {
            return geometry;
        }

        public RestPose getInitialSnapshot() {
            return initialSnapshot;
        }

        public float getPivotX() {
            return pivotX;
        }

        public float getPivotY() {
            return pivotY;
        }

        public float getPivotZ() {
            return pivotZ;
        }
    }

    /**
     * Bind-pose values use the same units as the model adapter supplies.
     */
    public static final class RestPose {
        public final float rotationValueX;
        public final float rotationValueY;
        public final float rotationValueZ;
        public final float positionOffsetX;
        public final float positionOffsetY;
        public final float positionOffsetZ;
        public final float scaleValueX;
        public final float scaleValueY;
        public final float scaleValueZ;

        public RestPose(
                float rotationX,
                float rotationY,
                float rotationZ,
                float positionX,
                float positionY,
                float positionZ,
                float scaleX,
                float scaleY,
                float scaleZ
        ) {
            this.rotationValueX = rotationX;
            this.rotationValueY = rotationY;
            this.rotationValueZ = rotationZ;
            this.positionOffsetX = positionX;
            this.positionOffsetY = positionY;
            this.positionOffsetZ = positionZ;
            this.scaleValueX = scaleX;
            this.scaleValueY = scaleY;
            this.scaleValueZ = scaleZ;
        }
    }

    public static final class RestGeometry {
        private final Mesh cubes;
        private final Vector3f rotation;
        private final Boolean dontRender;

        public RestGeometry(Mesh cubes, Vector3f rotation) {
            this(cubes, rotation, Boolean.FALSE);
        }

        public RestGeometry(
                Mesh cubes,
                Vector3f rotation,
                Boolean dontRender
        ) {
            this.cubes = Objects.requireNonNull(cubes, "cubes");
            this.rotation = new Vector3f(
                    Objects.requireNonNull(rotation, "rotation")
            );
            this.dontRender = dontRender;
        }

        public Mesh cubes() {
            return cubes;
        }

        public Vector3f rotation() {
            return new Vector3f(rotation);
        }

        public Boolean dontRender() {
            return dontRender;
        }
    }

    /**
     * Immutable cube-edge data copied from the adapter at bake time.
     */
    public static final class Mesh {
        public static final Mesh EMPTY = new Mesh(
                new Vector3f[0],
                new Vector3f[0],
                new Vector3f[0],
                new Vector3f[0]
        );

        private final Vector3f[] positions;
        private final Vector3f[] dx;
        private final Vector3f[] dy;
        private final Vector3f[] dz;

        public Mesh(
                Vector3f[] positions,
                Vector3f[] dx,
                Vector3f[] dy,
                Vector3f[] dz
        ) {
            int count = positions.length;
            if (dx.length != count || dy.length != count || dz.length != count) {
                throw new IllegalArgumentException(
                        "cube edge arrays must have equal lengths"
                );
            }
            this.positions = copy(positions);
            this.dx = copy(dx);
            this.dy = copy(dy);
            this.dz = copy(dz);
        }

        private static Vector3f[] copy(Vector3f[] input) {
            Vector3f[] output = new Vector3f[input.length];
            for (int index = 0; index < input.length; index++) {
                output[index] = new Vector3f(
                        Objects.requireNonNull(input[index], "mesh vector")
                );
            }
            return output;
        }

        public int getCubeCount() {
            return positions.length;
        }

        public Vector3f position(int index) {
            return positions[index];
        }

        public Vector3f dx(int index) {
            return dx[index];
        }

        public Vector3f dy(int index) {
            return dy[index];
        }

        public Vector3f dz(int index) {
            return dz[index];
        }
    }
}
