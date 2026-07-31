package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;

import java.nio.file.Path;

/**
 * Exposes only the package-local fixture operations needed by tail motion
 * scenarios.
 */
public final class TailAnimationContinuityTestFacade {
    public static final Path MODEL_DIRECTORY =
            BonePhysicsVerificationSupport.MODEL_DIRECTORY;

    private TailAnimationContinuityTestFacade() {
    }

    public static GeoModel loadWinefoxGeoModel() throws Exception {
        return BonePhysicsVerificationSupport.loadWinefoxGeoModel();
    }

    public static GeoModel loadGeoModel(Path modelPath) throws Exception {
        return BonePhysicsVerificationSupport.loadGeoModel(modelPath);
    }

    public static BoneModelSnapshot coreModel(GeoModel model) {
        return BonePhysicsVerificationSupport.coreModel(model);
    }

    public static BoneModelSnapshot coreModelFromJson(String json) {
        return BonePhysicsVerificationSupport.coreModelFromJson(json);
    }

    public static void require(boolean condition, String message) {
        BonePhysicsVerificationSupport.require(condition, message);
    }
}
