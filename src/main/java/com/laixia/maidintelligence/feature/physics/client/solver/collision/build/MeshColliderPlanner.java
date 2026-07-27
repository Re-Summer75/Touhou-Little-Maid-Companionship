package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.SwingCone;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Derives collision boxes straight from rigid meshes so authors never have to
 * place capsules by hand.
 *
 * <p>Candidates are individual rest-pose cubes, not per-bone hulls. A cube is
 * already an oriented box, so the collider is the mesh itself; a bone hull
 * would instead inflate scattered decorations into one loose volume that
 * blocks empty air. Selection happens once at layout time and is not capped:
 * every cube a segment can physically reach is attached, so collision follows
 * the model rather than a hand-picked subset. Frame cost stays bounded because
 * the runtime re-culls against the live swing cone and only resolves the
 * nearest few.
 */
final class MeshColliderPlanner {
    /**
     * A cube thinner than this cannot be resolved against an endpoint sphere:
     * it only produces side-to-side flicker, never a stable contact.
     */
    private static final float MIN_THICKNESS = 0.5F / 16.0F;
    private static final float REACH_MARGIN = 2.0F / 16.0F;
    /**
     * Widest cap a segment can ever occupy: the solver's own swing ceiling
     * plus room for the animation carrying segment and collider apart.
     */
    private static final float SWING_ENVELOPE = 1.4F;
    /**
     * A reference bone rotates too, and a cube far from that bone's pivot
     * travels further. Roughly a chord for this much rotation.
     */
    private static final float REFERENCE_ROTATION = 0.35F;
    /** A pivot buried this deep can never satisfy the collider. */
    private static final float PIVOT_TOLERANCE = 1.0F / 16.0F;
    private static final float CONTAINMENT_EPSILON = 1.0E-4F;

    private final PhysicsBoneGeometry.Analysis geometry;
    private final BodyCollisionGeometry bodyGeometry;
    private final PhysicsBoneSelectionPlan selectionPlan;
    private final List<Candidate> candidates;
    private final SwingCone cone = new SwingCone();
    private final Vector3f coneAxis = new Vector3f();

    MeshColliderPlanner(
            PhysicsBoneGeometry.Analysis geometry,
            BodyCollisionGeometry bodyGeometry,
            PhysicsBoneSelectionPlan selectionPlan
    ) {
        this.geometry = geometry;
        this.bodyGeometry = bodyGeometry;
        this.selectionPlan = selectionPlan;
        this.candidates = collectCandidates();
    }

    boolean hasCandidates() {
        return !candidates.isEmpty();
    }

    List<CollisionProxyPlan.MeshCollider> plan(
            PhysicsBoneGeometry.Node drivenNode
    ) {
        if (drivenNode == null || candidates.isEmpty()) {
            return List.of();
        }
        Vector3f pivot = drivenNode.pivot();
        /*
         * Swing limits keep the endpoint inside a cone around the rest
         * direction, so the reachable set is a spherical cap, not the whole
         * sphere. A cube is kept when it is within its own motion margin of
         * that cap, which is generous enough to survive animation yet drops
         * everything on the far side of the body.
         */
        float endpointReach = SegmentReach.reach(drivenNode);
        cone.set(
                SegmentReach.restDirection(drivenNode, coneAxis),
                SWING_ENVELOPE
        );
        List<Ranked> reachable = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.node() == drivenNode
                    || !isSafe(candidate.node(), drivenNode)) {
                continue;
            }
            float distance = cone.distanceToSweep(
                    pivot,
                    candidate.center(),
                    endpointReach
            ) - margin(candidate);
            boolean bodySupport = overlapsBody(candidate);
            if (distance > 0.0F
                    || (!bodySupport && depthInsideBox(
                    pivot,
                    candidate.center(),
                    candidate.half()
            ) > PIVOT_TOLERANCE)) {
                continue;
            }
            reachable.add(new Ranked(candidate, distance));
        }
        // Nearest first only decides drawing and dedupe order; nothing is
        // dropped for being ranked low.
        reachable.sort(Comparator.comparingDouble(Ranked::distance));
        List<Candidate> accepted = new ArrayList<>();
        List<CollisionProxyPlan.MeshCollider> colliders = new ArrayList<>();
        for (Ranked ranked : reachable) {
            Candidate candidate = ranked.candidate();
            if (isCovered(candidate, accepted)) {
                continue;
            }
            accepted.add(candidate);
            PhysicsBoneGeometry.CubeBox cube = candidate.cube();
            colliders.add(new CollisionProxyPlan.MeshCollider(
                    CollisionReference.of(candidate.node()),
                    new Vector3f(cube.center()),
                    new Vector3f(cube.axisX()),
                    new Vector3f(cube.axisY()),
                    new Vector3f(cube.axisZ()),
                    new Vector3f(cube.half())
            ));
        }
        return List.copyOf(colliders);
    }

    private List<Candidate> collectCandidates() {
        List<Candidate> output = new ArrayList<>();
        Vector3f half = new Vector3f();
        Vector3f corner = new Vector3f();
        for (PhysicsBoneGeometry.Node node : geometry.nodes()) {
            if (!node.hasGeometry()
                    || selectionPlan.isDriven(node.bone())
                    || hasDrivenAncestor(node)) {
                continue;
            }
            for (PhysicsBoneGeometry.CubeBox cube : node.cubeBoxes()) {
                if (!isSolid(cube)) {
                    continue;
                }
                output.add(new Candidate(
                        node,
                        cube,
                        new Vector3f(cube.center()),
                        new Vector3f(cube.axisAlignedHalf(half)),
                        SegmentReach.sweptRadius(node, cube, corner)
                ));
            }
        }
        return List.copyOf(output);
    }

    private boolean isSafe(
            PhysicsBoneGeometry.Node candidate,
            PhysicsBoneGeometry.Node drivenNode
    ) {
        return CollisionReferenceSafety.isSafe(
                candidate,
                drivenNode,
                selectionPlan,
                geometry
        );
    }

    private boolean hasDrivenAncestor(PhysicsBoneGeometry.Node node) {
        PhysicsBoneGeometry.Node cursor = node.parent();
        while (cursor != null) {
            if (selectionPlan.isDriven(cursor.bone())) {
                return true;
            }
            cursor = cursor.parent();
        }
        return false;
    }

    private static boolean isSolid(PhysicsBoneGeometry.CubeBox cube) {
        Vector3f half = cube.half();
        return Math.min(half.x, Math.min(half.y, half.z)) * 2.0F
                >= MIN_THICKNESS;
    }

    /** How far a cube may travel before the segment could reach it. */
    private static float margin(Candidate candidate) {
        return candidate.half().length()
                + candidate.sweptRadius() * REFERENCE_ROTATION
                + REACH_MARGIN;
    }

    /**
     * Layered clothing repeats the same volume several times. Only a cube an
     * accepted box fully swallows is dropped: it can never add a contact, so
     * this removes cost without removing collision.
     */
    private static boolean isCovered(
            Candidate candidate,
            List<Candidate> accepted
    ) {
        for (Candidate other : accepted) {
            if (other.node() == candidate.node()
                    && contains(other, candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A cloth pivot is commonly authored just inside the waist. That says
     * nothing about whether its endpoint can satisfy a torso cube, so the
     * generic "buried pivot" rejection must not remove exact mesh that
     * overlaps the measured body landmark. Swing-cone reachability still
     * applies and keeps unrelated head, arm, and prop geometry out.
     */
    private boolean overlapsBody(Candidate candidate) {
        if (bodyGeometry == null || !bodyGeometry.hasBody()) {
            return false;
        }
        PhysicsBoneGeometry.Bounds body = bodyGeometry.bodyBounds();
        Vector3f center = candidate.center();
        Vector3f half = candidate.half();
        return center.x + half.x > body.minX()
                && center.x - half.x < body.maxX()
                && center.y + half.y > body.minY()
                && center.y - half.y < body.maxY()
                && center.z + half.z > body.minZ()
                && center.z - half.z < body.maxZ();
    }

    /** True when {@code inner} lies inside {@code outer} on every axis. */
    private static boolean contains(Candidate outer, Candidate inner) {
        return covers(outer.center().x, outer.half().x,
                inner.center().x, inner.half().x)
                && covers(outer.center().y, outer.half().y,
                inner.center().y, inner.half().y)
                && covers(outer.center().z, outer.half().z,
                inner.center().z, inner.half().z);
    }

    private static boolean covers(
            float outerCenter,
            float outerHalf,
            float innerCenter,
            float innerHalf
    ) {
        return Math.abs(outerCenter - innerCenter)
                <= outerHalf - innerHalf + CONTAINMENT_EPSILON;
    }

    private static float depthInsideBox(
            Vector3f point,
            Vector3f center,
            Vector3f half
    ) {
        float qx = Math.abs(point.x - center.x) - half.x;
        float qy = Math.abs(point.y - center.y) - half.y;
        float qz = Math.abs(point.z - center.z) - half.z;
        float deepest = Math.max(qx, Math.max(qy, qz));
        return deepest >= 0.0F ? 0.0F : -deepest;
    }

    /**
     * Axis-aligned {@code center}/{@code half} only drive the cheap culling.
     * {@code sweptRadius} bounds the cube for any rotation of its own bone.
     */
    private record Candidate(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.CubeBox cube,
            Vector3f center,
            Vector3f half,
            float sweptRadius
    ) {
    }

    private record Ranked(Candidate candidate, float distance) {
    }
}
