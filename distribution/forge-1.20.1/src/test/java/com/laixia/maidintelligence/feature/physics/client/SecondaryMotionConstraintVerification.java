package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

final class SecondaryMotionConstraintVerification {
    private SecondaryMotionConstraintVerification() {
    }

    static void run() {
        ReferenceSpaceConstraintVerification.run();
        ConstraintProjectionEdgeVerification.run();
        CollisionConstraintVerification.run();
    }
}
