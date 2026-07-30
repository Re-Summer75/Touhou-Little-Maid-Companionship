package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.Converter;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.RawGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.tree.RawGeometryTree;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.GeoBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneClassifier.ChainType;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneClassifier.Classification;
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
        require(bone != null, message + " (bone missing)");
        PhysicsBoneSelectionPlan.Decision decision = plan.decision(bone);
        require(decision.driven() && decision.type() == type, message
                + ": " + decision.type() + " / " + decision.reason()
                + " / " + decision.confidence());
    }

    static AnimatedGeoModel modelFromJson(String json) {
        return new AnimatedGeoModel(geoModelFromJson(json));
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
