package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

final class CollisionLayoutVerification {
    private CollisionLayoutVerification() {
    }

    static void run() throws Exception {
        AutomaticCollisionLayoutVerification.run();
        AutomaticReferenceSafetyVerification.run();
        HeadMeshCollisionVerification.run();
        MeshCollisionVerification.run();
        ClothLayerCollisionVerification.run();
        CollisionSchemaCompatibilityVerification.run();
        ExplicitCollisionLayoutVerification.run();
        SqueezedContactVerification.run();
        SweptContactVerification.run();
        SeatedOverlapVerification.run();
        WindCollisionVerification.run();
        CullBucketSubdivisionVerification.run();
    }
}
