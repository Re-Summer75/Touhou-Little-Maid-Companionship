package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class BundledPivotSurfaceVerification {
    private BundledPivotSurfaceVerification() {
    }

    static void run() throws Exception {
        List<Path> paths;
        try (var stream = Files.list(MODEL_DIRECTORY)) {
            paths = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".json"))
                    .sorted()
                    .toList();
        }
        for (Path path : paths) {
            verifyModel(path);
        }
    }

    private static void verifyModel(Path path) throws Exception {
        AnimatedGeoModel model = new AnimatedGeoModel(loadGeoModel(path));
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:pivot_surface_" + path.getFileName(),
                model,
                PhysicsMetadata.EMPTY
        );
        for (AnimatedGeoBone bone : model.bones().values()) {
            var metrics = plan.kinematics(bone);
            if (metrics == null
                    || !metrics.compensatesPivot()
                    || plan.decision(bone).type()
                    == PhysicsBoneSelectionPlan.PartType.HEAD_SHELL) {
                continue;
            }
            float distance = meshDistance(
                    bone.geoBone().cubes(),
                    metrics.effectivePivot()
            );
            require(
                    distance < 1.0E-4F,
                    path.getFileName() + " " + bone.getName()
                            + " corrected to empty aggregate space: "
                            + distance * 16.0F + "px"
            );
        }
    }

    private static float meshDistance(GeoMesh mesh, Vector3f point) {
        float minimum = Float.POSITIVE_INFINITY;
        for (int cube = 0; cube < mesh.getCubeCount(); cube++) {
            Vector3f residual = new Vector3f(point).sub(mesh.position(cube));
            removeAxis(residual, mesh.dx(cube));
            removeAxis(residual, mesh.dy(cube));
            removeAxis(residual, mesh.dz(cube));
            minimum = Math.min(minimum, residual.length());
        }
        return minimum;
    }

    private static void removeAxis(Vector3f residual, Vector3f axis) {
        float lengthSquared = axis.lengthSquared();
        if (lengthSquared <= 1.0E-8F) {
            return;
        }
        float coordinate = residual.dot(axis) / lengthSquared;
        float clamped = Math.max(0.0F, Math.min(1.0F, coordinate));
        residual.fma(-clamped, axis);
    }
}
