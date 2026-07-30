package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.Converter;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.RawGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.tree.RawGeometryTree;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.GeoBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.laixia.maidintelligence.feature.physics.client.model.GeckoBoneModelPort;
import com.laixia.maidintelligence.feature.physics.discovery.PhysicsBoneClassifier.ChainType;
import com.laixia.maidintelligence.feature.physics.discovery.PhysicsBoneClassifier.Classification;
import org.joml.Vector3f;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

final class BonePhysicsVerificationSupport {
    static final Path MODEL_DIRECTORY = Path.of(
            "geckolib_model_reference",
            "models",
            "entity"
    );

    private BonePhysicsVerificationSupport() {
    }

    static GeoModel loadWinefoxGeoModel() throws Exception {
        Path modelPath = MODEL_DIRECTORY.resolve("winefox.json");
        require(
                Files.isRegularFile(modelPath),
                "Tracked winefox fixture is missing"
        );
        return loadGeoModel(modelPath);
    }

    static GeoModel loadGeoModel(Path modelPath) throws Exception {
        RawGeoModel raw;
        try (InputStream input = Files.newInputStream(modelPath)) {
            raw = Converter.fromInputStream(input);
        }
        return GeoBuilder.getGeoBuilder().constructGeoModel(
                RawGeometryTree.parseHierarchy(raw)
        );
    }

    static Map<GeoBone, AnimatedGeoBone> bonesByGeoBone(
            AnimatedGeoModel model
    ) {
        IdentityHashMap<GeoBone, AnimatedGeoBone> output =
                new IdentityHashMap<>();
        forEachBone(model, bone -> output.put(bone.geoBone(), bone));
        return output;
    }

    static void collectParents(
            AnimatedGeoBone bone,
            AnimatedGeoBone parent,
            Map<AnimatedGeoBone, AnimatedGeoBone> output
    ) {
        if (parent != null) {
            output.put(bone, parent);
        }
        for (AnimatedGeoBone child : bone.children()) {
            collectParents(child, bone, output);
        }
    }

    static void forEachBone(
            AnimatedGeoModel model,
            Consumer<AnimatedGeoBone> action
    ) {
        for (AnimatedGeoBone bone : model.topLevelBones()) {
            forEachBone(bone, action);
        }
    }

    static void forEachBone(
            AnimatedGeoBone bone,
            Consumer<AnimatedGeoBone> action
    ) {
        action.accept(bone);
        for (AnimatedGeoBone child : bone.children()) {
            forEachBone(child, action);
        }
    }

    static void requireDriven(
            PhysicsBoneSelectionPlan plan,
            AnimatedGeoBone bone,
            PhysicsBoneSelectionPlan.PartType type,
            String message
    ) {
        requireDriven(plan, coreBone(bone), type, message);
    }

    static void requireDriven(
            PhysicsBoneSelectionPlan plan,
            BoneModelSnapshot.Bone bone,
            PhysicsBoneSelectionPlan.PartType type,
            String message
    ) {
        require(bone != null, message + " (bone missing)");
        PhysicsBoneSelectionPlan.Decision decision = plan.decision(bone);
        require(decision.driven() && decision.type() == type, message
                + ": " + decision.type() + " / " + decision.reason()
                + " / " + decision.confidence());
    }

    static AnimatedGeoModel modelFromJson(String json) {
        return new AnimatedGeoModel(geoModelFromJson(json));
    }

    static BoneModelSnapshot coreModel(AnimatedGeoModel model) {
        return GeckoBoneModelPort.snapshotOf(model);
    }

    static BoneModelSnapshot coreModel(GeoModel model) {
        return coreModel(new AnimatedGeoModel(model));
    }

    static BoneModelSnapshot.Bone coreBone(AnimatedGeoBone bone) {
        return GeckoBoneModelPort.coreBoneOf(bone);
    }

    static BoneModelSnapshot coreModelFromJson(String json) {
        return coreModel(modelFromJson(json));
    }

    static PhysicsBoneSelectionPlan discover(
            String modelId,
            AnimatedGeoModel model,
            PhysicsMetadata metadata
    ) {
        return PhysicsBoneDiscoverer.discover(
                modelId,
                coreModel(model),
                metadata
        );
    }

    static PhysicsBoneSelectionPlan discover(
            String modelId,
            BoneModelSnapshot model,
            PhysicsMetadata metadata
    ) {
        return PhysicsBoneDiscoverer.discover(modelId, model, metadata);
    }

    static PhysicsSolverLayout layout(
            AnimatedGeoModel model,
            PhysicsBoneSelectionPlan plan
    ) {
        return PhysicsSolverLayout.build(coreModel(model), plan);
    }

    static PhysicsSolverLayout layout(
            BoneModelSnapshot model,
            PhysicsBoneSelectionPlan plan
    ) {
        return PhysicsSolverLayout.build(model, plan);
    }

    static PhysicsBoneGeometry.Analysis analyze(AnimatedGeoModel model) {
        return PhysicsBoneGeometry.analyze(coreModel(model));
    }

    static PhysicsBoneGeometry.Analysis analyze(BoneModelSnapshot model) {
        return PhysicsBoneGeometry.analyze(model);
    }

    static PhysicsBoneSelectionPlan.Decision decision(
            PhysicsBoneSelectionPlan plan,
            AnimatedGeoBone bone
    ) {
        return plan.decision(coreBone(bone));
    }

    static PhysicsBoneSelectionPlan.Decision decision(
            PhysicsBoneSelectionPlan plan,
            BoneModelSnapshot.Bone bone
    ) {
        return plan.decision(bone);
    }

    static boolean isDriven(
            PhysicsBoneSelectionPlan plan,
            AnimatedGeoBone bone
    ) {
        return plan.isDriven(coreBone(bone));
    }

    static boolean isDriven(
            PhysicsBoneSelectionPlan plan,
            BoneModelSnapshot.Bone bone
    ) {
        return plan.isDriven(bone);
    }

    static BoneKinematics.Metrics kinematics(
            PhysicsBoneSelectionPlan plan,
            AnimatedGeoBone bone
    ) {
        return plan.kinematics(coreBone(bone));
    }

    static BoneKinematics.Metrics kinematics(
            PhysicsBoneSelectionPlan plan,
            BoneModelSnapshot.Bone bone
    ) {
        return plan.kinematics(bone);
    }

    static String path(
            PhysicsBoneSelectionPlan plan,
            AnimatedGeoBone bone
    ) {
        return plan.path(coreBone(bone));
    }

    static String path(
            PhysicsBoneSelectionPlan plan,
            BoneModelSnapshot.Bone bone
    ) {
        return plan.path(bone);
    }

    static GeoModel geoModelFromJson(String json) {
        RawGeoModel raw = Converter.fromJsonString(json);
        return GeoBuilder.getGeoBuilder().constructGeoModel(
                RawGeometryTree.parseHierarchy(raw)
        );
    }

    static void requireChain(String name, ChainType type, int depth) {
        Classification classification = PhysicsBoneClassifier.classify(name);
        require(
                classification.type() == type && classification.depth() == depth,
                "Bone " + name + " classified as " + classification.type()
                        + " depth " + classification.depth()
        );
    }

    static void requireNone(String name) {
        require(
                PhysicsBoneClassifier.classify(name).type() == ChainType.NONE,
                "Bone " + name + " should not receive physics"
        );
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void requireNear(float actual, float expected, String message) {
        requireNear(actual, expected, 1.0E-4F, message);
    }

    static void requireNear(
            float actual,
            float expected,
            float tolerance,
            String message
    ) {
        require(
                Math.abs(actual - expected) <= tolerance,
                message + ": expected " + expected + ", got " + actual
        );
    }

    static void requireVectorNear(
            Vector3f actual,
            Vector3f expected,
            float tolerance,
            String message
    ) {
        requireNear(actual.x, expected.x, tolerance, message + " (x)");
        requireNear(actual.y, expected.y, tolerance, message + " (y)");
        requireNear(actual.z, expected.z, tolerance, message + " (z)");
    }
}
