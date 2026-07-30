package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.laixia.maidintelligence.feature.physics.client.SecondaryMotionFixture.Fixture;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class ConstraintProjectionEdgeVerification {
    private ConstraintProjectionEdgeVerification() {
    }

    static void run() {
        Fixture fixture = SecondaryMotionFixture.create(
                20.0F,
                0.0F,
                false,
                false
        );
        Vector3f rest = new Vector3f(0.0F, -1.0F, 0.0F);
        Vector3f reversed = new Vector3f(rest).negate();
        boolean corrected = fixture.hairNode().constraint().projectSwing(
                reversed,
                rest,
                new Quaternionf(),
                new Vector3f()
        );
        require(corrected, "A reversed direction escaped swing projection");
        require(
                reversed.dot(rest) > 0.0F,
                "A reversed direction remained behind the animation axis"
        );
    }
}
