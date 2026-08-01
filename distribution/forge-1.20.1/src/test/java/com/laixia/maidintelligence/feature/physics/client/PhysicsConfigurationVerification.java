package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.forge.PhysicsClientConfig;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Verifies that the client preference remains live after setup.
 */
final class PhysicsConfigurationVerification {
    private PhysicsConfigurationVerification() {
    }

    static void run() {
        AtomicBoolean configured = new AtomicBoolean(false);
        try {
            MaidBonePhysics.configure(configured::get);
            require(
                    !MaidBonePhysics.enabled(),
                    "The disabled client preference was ignored"
            );
            configured.set(true);
            require(
                    MaidBonePhysics.enabled(),
                    "The live client preference did not re-enable physics"
            );
            require(
                    "tlm_companionship-client.toml".equals(
                            PhysicsClientConfig.FILE_NAME
                    ),
                    "The physics preference uses an unexpected config file"
            );
        } finally {
            MaidBonePhysics.configure(() -> true);
        }
    }
}
