package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadata;

import java.nio.file.Path;

/**
 * Narrow bridge from the audit subpackage to package-private test fixtures.
 */
public final class MeshPenetrationAuditAccess {
    private MeshPenetrationAuditAccess() {
    }

    public static Path modelDirectory() {
        return BonePhysicsVerificationSupport.MODEL_DIRECTORY;
    }

    public static BoneModelSnapshot loadModel(Path path) throws Exception {
        return BonePhysicsVerificationSupport.coreModel(
                BonePhysicsVerificationSupport.loadGeoModel(path)
        );
    }

    public static PhysicsSolverLayout buildLayout(
            String modelId,
            BoneModelSnapshot model
    ) {
        return PhysicsSolverLayout.build(
                model,
                BonePhysicsVerificationSupport.discover(
                        modelId,
                        model,
                        PhysicsMetadata.EMPTY
                )
        );
    }
}
