package com.laixia.maidintelligence.feature.physics.client.model;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.snapshot.BoneSnapshot;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.port.BoneModelPort;
import com.laixia.maidintelligence.feature.physics.port.PoseWriterPort;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Bakes Gecko's stable skeleton and mesh into the platform-neutral core model.
 * Frame updates only copy scalar pose fields between the two prebuilt trees.
 */
public final class GeckoBoneModelPort
        implements BoneModelPort, PoseWriterPort {
    private static final Map<AnimatedGeoModel, GeckoBoneModelPort> PORTS =
            new WeakHashMap<>();
    private static final Map<
            AnimatedGeoBone,
            BoneModelSnapshot.Bone
            > CORE_BY_SOURCE = new WeakHashMap<>();

    private final BoneModelSnapshot model;
    private final IdentityHashMap<AnimatedGeoBone, BoneModelSnapshot.Bone>
            coreBySource = new IdentityHashMap<>();
    private final AnimatedGeoBone[] sourceByIndex;

    private GeckoBoneModelPort(AnimatedGeoModel source) {
        Objects.requireNonNull(source, "source");
        BoneModelSnapshot.Builder builder = BoneModelSnapshot.builder();
        List<AnimatedGeoBone> ordered = new ArrayList<>();
        for (AnimatedGeoBone root : source.topLevelBones()) {
            bakeBone(root, null, builder, ordered);
        }
        configureLocators(source, builder);
        model = builder.build();
        sourceByIndex = ordered.toArray(AnimatedGeoBone[]::new);
        CORE_BY_SOURCE.putAll(coreBySource);
        readAnimationPose();
    }

    public static synchronized GeckoBoneModelPort of(
            AnimatedGeoModel source
    ) {
        return PORTS.computeIfAbsent(source, GeckoBoneModelPort::new);
    }

    public static synchronized void clearCache() {
        PORTS.clear();
        CORE_BY_SOURCE.clear();
    }

    public static synchronized BoneModelSnapshot snapshotOf(
            AnimatedGeoModel source
    ) {
        return of(source).model();
    }

    public static synchronized BoneModelSnapshot.Bone coreBoneOf(
            AnimatedGeoBone source
    ) {
        return source == null ? null : CORE_BY_SOURCE.get(source);
    }

    @Override
    public BoneModelSnapshot model() {
        return model;
    }

    public BoneModelSnapshot.Bone coreBone(AnimatedGeoBone bone) {
        return coreBySource.get(bone);
    }

    @Override
    public void readAnimationPose() {
        for (int index = 0; index < sourceByIndex.length; index++) {
            AnimatedGeoBone input = sourceByIndex[index];
            BoneModelSnapshot.Bone output = model.bone(index);
            output.setRotationX(input.getRotationX());
            output.setRotationY(input.getRotationY());
            output.setRotationZ(input.getRotationZ());
            output.setPositionX(input.getPositionX());
            output.setPositionY(input.getPositionY());
            output.setPositionZ(input.getPositionZ());
            output.setScaleX(input.getScaleX());
            output.setScaleY(input.getScaleY());
            output.setScaleZ(input.getScaleZ());
        }
    }

    @Override
    public void writePose(BoneModelSnapshot ignored) {
        for (int index = 0; index < sourceByIndex.length; index++) {
            AnimatedGeoBone output = sourceByIndex[index];
            BoneModelSnapshot.Bone input = model.bone(index);
            output.setRotationX(input.getRotationX());
            output.setRotationY(input.getRotationY());
            output.setRotationZ(input.getRotationZ());
            output.setPositionX(input.getPositionX());
            output.setPositionY(input.getPositionY());
            output.setPositionZ(input.getPositionZ());
            output.setScaleX(input.getScaleX());
            output.setScaleY(input.getScaleY());
            output.setScaleZ(input.getScaleZ());
        }
    }

    private void bakeBone(
            AnimatedGeoBone sourceBone,
            BoneModelSnapshot.Bone parent,
            BoneModelSnapshot.Builder builder,
            List<AnimatedGeoBone> ordered
    ) {
        BoneSnapshot initial = sourceBone.getInitialSnapshot();
        BoneModelSnapshot.Bone coreBone = builder.addBone(
                parent,
                sourceBone.getName(),
                sourceBone.getPivotX(),
                sourceBone.getPivotY(),
                sourceBone.getPivotZ(),
                new BoneModelSnapshot.RestPose(
                        initial.rotationValueX,
                        initial.rotationValueY,
                        initial.rotationValueZ,
                        initial.positionOffsetX,
                        initial.positionOffsetY,
                        initial.positionOffsetZ,
                        initial.scaleValueX,
                        initial.scaleValueY,
                        initial.scaleValueZ
                ),
                new BoneModelSnapshot.RestGeometry(
                        bakeMesh(sourceBone.geoBone().cubes()),
                        new Vector3f(),
                        sourceBone.geoBone().dontRender()
                )
        );
        coreBySource.put(sourceBone, coreBone);
        ordered.add(sourceBone);
        for (AnimatedGeoBone child : sourceBone.children()) {
            bakeBone(child, coreBone, builder, ordered);
        }
    }

    private void configureLocators(
            AnimatedGeoModel source,
            BoneModelSnapshot.Builder builder
    ) {
        builder.head(coreBySource.get(source.head()))
                .leftArm(coreBySource.get(source.leftArm()))
                .rightArm(coreBySource.get(source.rightArm()))
                .leftHandBones(map(source.leftHandBones()))
                .rightHandBones(map(source.rightHandBones()))
                .leftWaistBones(map(source.leftWaistBones()))
                .rightWaistBones(map(source.rightWaistBones()))
                .backpackBones(map(source.backpackBones()))
                .tacPistolBones(map(source.tacPistolBones()))
                .tacRifleBones(map(source.tacRifleBones()));
    }

    private List<BoneModelSnapshot.Bone> map(
            List<AnimatedGeoBone> sourceBones
    ) {
        if (sourceBones == null || sourceBones.isEmpty()) {
            return List.of();
        }
        List<BoneModelSnapshot.Bone> result =
                new ArrayList<>(sourceBones.size());
        for (AnimatedGeoBone bone : sourceBones) {
            BoneModelSnapshot.Bone mapped = coreBySource.get(bone);
            if (mapped != null) {
                result.add(mapped);
            }
        }
        return result;
    }

    private static BoneModelSnapshot.Mesh bakeMesh(GeoMesh mesh) {
        int count = mesh.getCubeCount();
        Vector3f[] positions = new Vector3f[count];
        Vector3f[] dx = new Vector3f[count];
        Vector3f[] dy = new Vector3f[count];
        Vector3f[] dz = new Vector3f[count];
        for (int index = 0; index < count; index++) {
            positions[index] = new Vector3f(mesh.position(index));
            dx[index] = new Vector3f(mesh.dx(index));
            dy[index] = new Vector3f(mesh.dy(index));
            dz[index] = new Vector3f(mesh.dz(index));
        }
        return new BoneModelSnapshot.Mesh(positions, dx, dy, dz);
    }
}
