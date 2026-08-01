package com.laixia.maidintelligence.feature.physics.client.collision;

import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProxyDebugData;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.MeshCollisionTestFacade.require;
import static com.laixia.maidintelligence.feature.physics.client.collision.MeshCollisionScenarioSupport.bone;
import static com.laixia.maidintelligence.feature.physics.client.collision.MeshCollisionScenarioSupport.indexOf;
import static com.laixia.maidintelligence.feature.physics.client.collision.MeshCollisionScenarioSupport.model;
import static com.laixia.maidintelligence.feature.physics.client.collision.MeshCollisionScenarioSupport.plan;

/**
 * Verifies runtime blocking and relative contact projection.
 */
public final class MeshCollisionRuntimeProjectionVerification {
    /** Swings the strand sideways until it is buried in the skull. */
    private static final float SWING = 0.9F;
    /** Swings the strand until it just meets the skull surface. */
    private static final float CONTACT = 0.30F;
    /** Frame acceleration that throws the strand toward the skull. */
    private static final Vector3f INWARD = new Vector3f(-2.0F, 0.0F, 0.0F);

    private MeshCollisionRuntimeProjectionVerification() {
    }

    public static void run() {
        verifiesEndpointStopsAtHeadSurface();
        verifiesAuthoredOverlapIsNotForcedApart();
        verifiesHiddenMeshIsNotPrepared();
    }

    /**
     * Rests the strand against the skull and then blows it inward with frame
     * acceleration. Secondary motion is exactly what collision exists to stop,
     * so the mesh box has to hold the endpoint on the surface.
     *
     * <p>Where the surface is, is checked exactly inside {@code driveIntoHead}.
     * This adds the end-to-end half: that the clearance reached the written
     * pose rather than being computed and dropped. The margin is small on
     * purpose — the endpoint radius is a tolerance now, not a thickness, so the
     * box no longer stands a ring off its own cube and the only swing taken
     * back is the part that truly sank in. A collider that stopped working
     * entirely still lands on {@code free} and is caught.
     */
    private static void verifiesEndpointStopsAtHeadSurface() {
        float free = driveIntoHead(false);
        require(
                free > 0.10F,
                "The uncollided strand did not swing into the head: " + free
        );
        float blocked = driveIntoHead(true);
        require(
                blocked < free - 0.01F,
                "Mesh collision did not hold the strand back: " + blocked
                        + " against a free swing of " + free
        );
    }

    /**
     * An animation is free to overlap its own mesh: a seated pose folds legs
     * through a hem, an embrace presses two bodies together, and no authored
     * frame owes the collider anything. Enforcing the surface in absolute
     * terms would fight the animation every frame, so the constraint is
     * relative — the authored depth is kept, and only what physics adds on top
     * is rejected.
     */
    private static void verifiesAuthoredOverlapIsNotForcedApart() {
        BoneModelSnapshot model = model();
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                plan(model, true)
        );
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        BoneModelSnapshot.Bone hair = bone(model, "Hair");
        float minimumSettled = Float.POSITIVE_INFINITY;
        float maximumSettled = Float.NEGATIVE_INFINITY;
        for (int frame = 0; frame < 120; frame++) {
            solver.restoreAnimationPose();
            hair.setRotationZ(SWING);
            solver.solve(new Vector3f(), 0.0F, 1.0F / 60.0F, false);
            if (frame >= 90) {
                minimumSettled = Math.min(
                        minimumSettled,
                        hair.getRotationZ()
                );
                maximumSettled = Math.max(
                        maximumSettled,
                        hair.getRotationZ()
                );
            }
        }
        // An idle constrained spring now settles on the authored swing itself.
        require(
                hair.getRotationZ() > SWING - 0.02F,
                "Collision forced the authored overlap apart: "
                        + hair.getRotationZ()
        );
        require(
                maximumSettled - minimumSettled < 0.01F,
                "The authored overlap buzzed against the collider: "
                        + minimumSettled + ".." + maximumSettled
        );

        // Physics on top of that overlap must still find a surface.
        for (int frame = 0; frame < 120; frame++) {
            solver.restoreAnimationPose();
            hair.setRotationZ(SWING);
            solver.solve(INWARD, 0.0F, 1.0F / 60.0F, false);
        }
        requireSurfaceContact(solver, indexOf(layout, "Hair"));
    }

    private static void verifiesHiddenMeshIsNotPrepared() {
        BoneModelSnapshot model = model();
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model, plan(model, true)
        );
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        BoneModelSnapshot.Bone head = bone(model, "Head");
        BoneModelSnapshot.Bone hair = bone(model, "Hair");
        int hairIndex = indexOf(layout, "Hair");
        int headIndex = indexOf(layout, "Head");

        head.setRenderVisibility(true, false);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        require(
                !hasPreparedReference(solver, hairIndex, headIndex),
                "A hidden head mesh remained an active collision proxy"
        );

        head.setRenderVisibility(true, true);
        solver.restoreAnimationPose();
        hair.setRotationZ(CONTACT);
        solver.solve(INWARD, 0.0F, 1.0F / 60.0F, false);
        require(
                hasPreparedReference(solver, hairIndex, headIndex),
                "A newly visible head mesh did not rejoin collision"
        );

        head.setRenderVisibility(true, false);
        solver.restoreAnimationPose();
        hair.setRotationZ(CONTACT);
        solver.solve(INWARD, 0.0F, 1.0F / 60.0F, false);
        require(
                !hasPreparedReference(solver, hairIndex, headIndex),
                "A mesh visibility transition left a ghost collider"
        );
    }

    /** Returns the settled swing angle after driving the strand inward. */
    private static float driveIntoHead(boolean auto) {
        BoneModelSnapshot model = model();
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                plan(model, auto)
        );
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        // Calibrate the rest allowance on the untouched authored pose first.
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);

        BoneModelSnapshot.Bone hair = bone(model, "Hair");
        for (int frame = 0; frame < 90; frame++) {
            solver.restoreAnimationPose();
            hair.setRotationZ(CONTACT);
            solver.solve(INWARD, 0.0F, 1.0F / 30.0F, false);
        }
        if (auto) {
            requireSurfaceContact(solver, indexOf(layout, "Hair"));
        }
        return hair.getRotationZ();
    }

    private static void requireSurfaceContact(
            SpringBoneSolver solver,
            int node
    ) {
        int proxyCount = solver.preparedProxyCount(node);
        require(proxyCount > 0, "Hair lost its prepared mesh collision");
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        float nearest = Float.MAX_VALUE;
        for (int proxy = 0; proxy < proxyCount; proxy++) {
            require(
                    solver.copyPreparedCollisionProxy(node, proxy, data),
                    "Prepared mesh collision was unavailable"
            );
            require(
                    data.clearance >= -1.0E-4F,
                    "Driven endpoint sank into the mesh collider: "
                            + data.clearance
            );
            nearest = Math.min(nearest, data.clearance);
        }
        /*
         * Contact is the sheet's surface meeting the face, so the endpoint on its
         * axis stops half a thickness short of it rather than on it. The tolerance
         * is that half thickness: this strand is 2 px through, and requiring the
         * axis itself to land on the face is requiring the visible surface to be
         * halfway inside.
         */
        float halfThickness = 1.0F / 16.0F;
        float contactMargin = 1.0F / 128.0F;
        require(
                nearest <= halfThickness + contactMargin,
                "The strand never reached the mesh collider: " + nearest
        );
    }

    private static boolean hasPreparedReference(
            SpringBoneSolver solver,
            int node,
            int reference
    ) {
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        int count = solver.preparedProxyCount(node);
        for (int proxy = 0; proxy < count; proxy++) {
            if (solver.copyPreparedCollisionProxy(node, proxy, data)
                    && data.referenceNodeIndex == reference) {
                return true;
            }
        }
        return false;
    }
}
