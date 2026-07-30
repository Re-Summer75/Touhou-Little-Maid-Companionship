package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProxyDebugData;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Covers wind holding cloth against the body it hangs on.
 *
 * <p>Wind is applied as a lean on the spring rather than as a force, so a gust
 * moves the pose the segment is drawn back towards. When it blows inwards that
 * target sits inside the body, and the spring then pulls the cloth into the
 * collider for as long as the weather lasts. Projection pushes back every frame,
 * but a target it can never reach means it is answering the same violation
 * forever, and the part rides at whatever depth the two balance at.
 */
final class WindCollisionVerification {
    private static final float DT = 1.0F / 60.0F;
    private static final int SETTLE = 600;
    private static final String MODEL = "winefox.json";
    /** Far above saturation, so every direction reaches the safety ceiling. */
    private static final float STORM = 12.0F;
    /**
     * Depth wind may drive cloth into a collider, in blocks.
     *
     * <p>Contact tolerance is 0.1 px, or 0.006 blocks, and projection resolves
     * to within a fraction of that; this leaves room for the numerical slack of
     * a settled contact without admitting a visible overlap.
     */
    private static final float TOLERANCE = 0.01F;

    private WindCollisionVerification() {
    }

    static void run() throws Exception {
        verifiesWindDoesNotPressClothIntoTheBody();
    }

    private static void verifiesWindDoesNotPressClothIntoTheBody()
            throws Exception {
        BoneModelSnapshot model = BonePhysicsVerificationSupport.coreModel(
                BonePhysicsVerificationSupport.loadGeoModel(
                        BonePhysicsVerificationSupport.MODEL_DIRECTORY
                                .resolve(MODEL)
                )
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:wind", model, PhysicsMetadata.EMPTY
                )
        );
        int[] cloth = drivenWithProxies(layout);
        require(cloth.length > 0, MODEL + " has no driven cloth with proxies");

        /*
         * Depth is measured against still air, not against zero. Clearance is
         * reported from the collider's own surface while the projection resolves
         * to a surface the rest allowance has already pulled in by whatever
         * overlap the model is drawn at, so a part can sit a little inside one
         * with nothing wrong. What wind may not do is add to that.
         */
        float calm = settleInWind(layout, cloth, new Vector3f());
        Vector3f[] winds = {
                new Vector3f(0.0F, 0.0F, -STORM),
                new Vector3f(0.0F, 0.0F, STORM),
                new Vector3f(-STORM, 0.0F, 0.0F),
                new Vector3f(STORM, 0.0F, 0.0F)
        };
        for (Vector3f wind : winds) {
            float deepest = settleInWind(layout, cloth, wind);
            float added = calm - deepest;
            require(
                    added <= TOLERANCE,
                    "Wind blowing " + wind + " drove cloth of " + MODEL + " "
                            + added + " blocks deeper into a collider than"
                            + " still air does (" + deepest + " against "
                            + calm + "); a gust should lean cloth against the"
                            + " body, not through it"
            );
        }
    }

    /** Deepest penetration reached once the pose has settled under one gust. */
    private static float settleInWind(
            PhysicsSolverLayout layout,
            int[] cloth,
            Vector3f wind
    ) {
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        solver.solve(new Vector3f(), wind, 0.0F, 0.0F, false);
        for (int frame = 0; frame < SETTLE; frame++) {
            solver.restoreAnimationPose();
            solver.solve(new Vector3f(), wind, 0.0F, DT, false);
        }

        float deepest = Float.MAX_VALUE;
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        for (int node : cloth) {
            int count = solver.preparedProxyCount(node);
            for (int proxy = 0; proxy < count; proxy++) {
                if (solver.copyPreparedCollisionProxy(node, proxy, data)) {
                    deepest = Math.min(deepest, data.clearance);
                }
            }
        }
        return deepest == Float.MAX_VALUE ? 0.0F : deepest;
    }

    private static int[] drivenWithProxies(PhysicsSolverLayout layout) {
        List<Integer> found = new ArrayList<>();
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (node.driven()
                    && node.constraint().collisionProxies().proxyCount() > 0) {
                found.add(index);
            }
        }
        int[] result = new int[found.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = found.get(index);
        }
        return result;
    }
}
