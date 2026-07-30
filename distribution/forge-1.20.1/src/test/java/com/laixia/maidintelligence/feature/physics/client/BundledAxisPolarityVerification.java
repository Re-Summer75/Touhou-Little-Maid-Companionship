package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.laixia.maidintelligence.feature.physics.layout.BoneKinematics;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Ensures the simulated tip points into the visible mass of every driven bone.
 */
final class BundledAxisPolarityVerification {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float EPSILON = 1.0E-6F;

    private BundledAxisPolarityVerification() {
    }

    static void run() throws Exception {
        List<Path> modelPaths;
        try (var paths = Files.list(MODEL_DIRECTORY)) {
            modelPaths = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".json"))
                    .sorted()
                    .toList();
        }
        StringBuilder reversed = new StringBuilder();
        int checked = 0;
        int corrected = 0;
        for (Path path : modelPaths) {
            BoneModelSnapshot model = coreModel(loadGeoModel(path));
            PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                    "verification:axis_polarity/" + path.getFileName(),
                    model,
                    PhysicsMetadata.EMPTY
            );
            for (BoneModelSnapshot.Bone bone : model.bones().values()) {
                BoneKinematics.Metrics metrics = plan.kinematics(bone);
                if (metrics == null) {
                    continue;
                }
                PhysicsBoneSelectionPlan.Decision decision =
                        plan.decision(bone);
                PhysicsBoneSelectionPlan.ChainSegment segment =
                        decision.chainSegment();
                if (segment.count() > 1
                        && segment.index() < segment.count() - 1) {
                    continue;
                }
                if (metrics.axisPolarityCorrected()) {
                    corrected++;
                }
                Vector3f towardMass = massCenter(bone.geometry().cubes())
                        .sub(metrics.effectivePivot());
                if (towardMass.lengthSquared()
                        <= pixelSquared(0.5F)) {
                    continue;
                }
                checked++;
                float projection = metrics.axis().dot(towardMass)
                        * PIXELS_PER_BLOCK;
                if (projection < -0.25F) {
                    reversed.append(path.getFileName())
                            .append('/').append(bone.getName())
                            .append(" projection=")
                            .append(projection).append("px axis=")
                            .append(metrics.axis()).append(" mass=")
                            .append(towardMass).append(" type=")
                            .append(decision.type()).append(" segment=")
                            .append(segment.index()).append('/')
                            .append(segment.count()).append(" primary=")
                            .append(metrics.usesDominantCluster())
                            .append('\n');
                }
            }
        }
        require(checked > 0, "No bundled driven geometry was checked");
        require(
                corrected > 0,
                "Bundled fixtures did not exercise axis-polarity correction"
        );
        require(
                reversed.isEmpty(),
                "Driven axes point away from visible geometry:\n" + reversed
        );
    }

    private static Vector3f massCenter(BoneModelSnapshot.Mesh mesh) {
        Vector3f center = new Vector3f();
        float totalWeight = 0.0F;
        for (int cube = 0; cube < mesh.getCubeCount(); cube++) {
            Vector3f dx = mesh.dx(cube);
            Vector3f dy = mesh.dy(cube);
            Vector3f dz = mesh.dz(cube);
            float weight = cubeWeight(dx, dy, dz);
            center.add(
                    new Vector3f(mesh.position(cube))
                            .fma(0.5F, dx)
                            .fma(0.5F, dy)
                            .fma(0.5F, dz)
                            .mul(weight)
            );
            totalWeight += weight;
        }
        return totalWeight <= EPSILON
                ? new Vector3f()
                : center.div(totalWeight);
    }

    private static float cubeWeight(
            Vector3f dx,
            Vector3f dy,
            Vector3f dz
    ) {
        float x = dx.length();
        float y = dy.length();
        float z = dz.length();
        float volume = x * y * z;
        float area = Math.max(x * y, Math.max(x * z, y * z));
        return Math.max(EPSILON, Math.max(
                volume,
                area / PIXELS_PER_BLOCK
        ));
    }

    private static float pixelSquared(float pixels) {
        float blocks = pixels / PIXELS_PER_BLOCK;
        return blocks * blocks;
    }
}
