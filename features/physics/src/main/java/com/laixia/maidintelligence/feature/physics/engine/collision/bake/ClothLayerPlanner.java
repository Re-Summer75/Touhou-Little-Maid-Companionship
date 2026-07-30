package com.laixia.maidintelligence.feature.physics.engine.collision.bake;


import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.SwingCone;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps stacked cloth panels in their authored order.
 *
 * <p>An apron over a skirt, a tabard over a robe or a sash over a sleeve are
 * separate physics chains hanging from the same anchor. The rigid mesh pass
 * cannot help there: both sides move, so neither is a collider, and the outer
 * panel eventually swings through the inner one.
 *
 * <p>Pairing is deliberately one-way. The outer panel treats the inner one as
 * a collider and the inner panel never sees the outer, which preserves the
 * authored layer order and rules out two soft panels shoving each other into
 * an oscillation. Outward is measured from the body axis, so "outer" means the
 * panel a viewer sees first.
 */
final class ClothLayerPlanner {
    /**
     * A cube is cloth when its thin axis is at most this fraction of the next
     * one. Chunky parts such as bow knots share a plane with a skirt panel by
     * accident; a plate does not.
     */
    private static final float PLATE_RATIO = 0.5F;
    private static final float PIXELS_PER_BLOCK = 16.0F;
    /**
     * Clear air between the two sheets. Beyond this they belong to different
     * garments and never meet; a lining and its cover are modelled within a
     * few pixels of each other so the cover does not show the gap.
     */
    private static final float MAX_LAYER_GAP = 3.0F / 16.0F;
    /**
     * A row of flaps around a hip is authored at one depth, so the little
     * offsets between its bones are incidental — under a fifth of the sheet
     * thickness on the models measured. A deliberate second layer is a much
     * larger step. Below this fraction of the thinner sheet the two count as
     * one surface split across bones, with no order to preserve.
     */
    private static final float COPLANAR_RATIO = 0.2F;
    /** In-plane overlap below this is a shared edge, not a covered area. */
    private static final float MIN_OVERLAP = 0.5F / 16.0F;
    /**
     * Overlap as a fraction of the narrower panel. Skirt flaps sitting side by
     * side around a hip touch along an edge; a lining covers a real area.
     */
    private static final float OVERLAP_RATIO = 0.35F;
    private static final float PARALLEL = 0.94F;
    /** Below this the plate faces sideways and has no inside or outside. */
    private static final float FACING = 0.35F;
    /**
     * Panels on opposite sides of the body, such as a front and a back skirt
     * flap, each look inward from the other. They are not a stack.
     */
    private static final float SAME_SIDE = 0.30F;
    private static final float AXIS_EPSILON = 1.0E-6F;
    private static final float SWING_ENVELOPE = 1.4F;
    private static final float REFERENCE_ROTATION = 0.35F;
    private static final float REACH_MARGIN = 2.0F / 16.0F;

    private final PhysicsBoneSelectionPlan selectionPlan;
    private final List<Panel> panels;
    private final Map<PhysicsBoneGeometry.Node,
            Set<PhysicsBoneGeometry.Node>> lining;
    private final Vector3f bodyAxis;
    private final SwingCone cone = new SwingCone();
    private final Vector3f coneAxis = new Vector3f();

    ClothLayerPlanner(
            PhysicsBoneGeometry.Analysis geometry,
            PhysicsBoneSelectionPlan selectionPlan,
            BodyCollisionGeometry bodyGeometry
    ) {
        this.selectionPlan = selectionPlan;
        this.bodyAxis = bodyAxis(bodyGeometry);
        this.panels = bodyAxis == null ? List.of() : collectPanels(geometry);
        this.lining = resolveLayerOrder();
    }

    /**
     * Which bone lines which, decided once for the whole model.
     *
     * <p>Order has to hold for the bone, not for a single cube: a garment that
     * wraps around a hip has one cube behind its neighbour and another in
     * front of it. When both bones read as each other's lining there is no
     * order to preserve, so the pair is dropped in both directions.
     */
    private Map<PhysicsBoneGeometry.Node, Set<PhysicsBoneGeometry.Node>>
    resolveLayerOrder() {
        Map<PhysicsBoneGeometry.Node, Set<PhysicsBoneGeometry.Node>> found =
                new IdentityHashMap<>();
        for (Panel outer : panels) {
            for (Panel inner : panels) {
                if (stackable(outer, inner) && covered(outer, inner)) {
                    found.computeIfAbsent(
                            outer.node(),
                            key -> Collections.newSetFromMap(
                                    new IdentityHashMap<>()
                            )
                    ).add(inner.node());
                }
            }
        }
        List<PhysicsBoneGeometry.Node[]> mutual = new ArrayList<>();
        found.forEach((outer, inners) -> {
            for (PhysicsBoneGeometry.Node inner : inners) {
                Set<PhysicsBoneGeometry.Node> reverse = found.get(inner);
                if (reverse != null && reverse.contains(outer)) {
                    mutual.add(new PhysicsBoneGeometry.Node[]{outer, inner});
                }
            }
        });
        for (PhysicsBoneGeometry.Node[] pair : mutual) {
            found.get(pair[0]).remove(pair[1]);
        }
        found.forEach(this::retainNearestLiningChain);
        return found;
    }

    /**
     * A broad cover can overlap several neighbouring chains. Keep every
     * segment of the nearest chain, but never let adjacent chains compete for
     * ownership of the same covering panel.
     */
    private void retainNearestLiningChain(
            PhysicsBoneGeometry.Node outerNode,
            Set<PhysicsBoneGeometry.Node> inners
    ) {
        if (inners.size() < 2) {
            return;
        }
        Map<String, Float> distanceByChain = new HashMap<>();
        for (Panel outer : panels) {
            if (outer.node() != outerNode) {
                continue;
            }
            for (Panel inner : panels) {
                if (!inners.contains(inner.node()) || !covered(outer, inner)) {
                    continue;
                }
                String chain = chainId(inner.node());
                distanceByChain.merge(
                        chain,
                        inPlaneDistanceSquared(outer, inner),
                        Math::min
                );
            }
        }
        String nearest = distanceByChain.entrySet().stream()
                .min(Map.Entry.<String, Float>comparingByValue()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(null);
        if (nearest != null) {
            inners.removeIf(inner -> !nearest.equals(chainId(inner)));
        }
    }

    private static float inPlaneDistanceSquared(Panel outer, Panel inner) {
        Vector3f delta = new Vector3f(inner.cube().center())
                .sub(outer.cube().center());
        delta.fma(-delta.dot(outer.normal()), outer.normal());
        return delta.lengthSquared();
    }

    List<CollisionProxyPlan.MeshCollider> plan(
            PhysicsBoneGeometry.Node drivenNode
    ) {
        Set<PhysicsBoneGeometry.Node> inners = drivenNode == null
                ? null
                : lining.get(drivenNode);
        if (inners == null || inners.isEmpty()) {
            return List.of();
        }
        float endpointReach = SegmentReach.reach(drivenNode);
        cone.set(
                SegmentReach.restDirection(drivenNode, coneAxis),
                SWING_ENVELOPE
        );
        Set<PhysicsBoneGeometry.CubeBox> taken = Collections.newSetFromMap(
                new IdentityHashMap<>()
        );
        List<CollisionProxyPlan.MeshCollider> output = new ArrayList<>();
        for (Panel outer : panels) {
            if (outer.node() != drivenNode) {
                continue;
            }
            for (Panel inner : panels) {
                if (!inners.contains(inner.node())
                        || !covered(outer, inner)
                        || !reachable(drivenNode, inner, endpointReach)
                        || !taken.add(inner.cube())) {
                    continue;
                }
                output.add(sheet(outer, inner));
            }
        }
        return List.copyOf(output);
    }

    /**
     * The lining exactly as it is modelled, closed on its outward face only.
     *
     * <p>A solid cube one pixel thick is useless against an endpoint that
     * travels further than that between frames: it lands on the far side and
     * reports no contact, so the covering panel appears to jump through.
     * Rather than fattening the box until it stops being the mesh, the box
     * keeps the cube's extents and loses its back face, so there is nowhere to
     * land. The stored axis is flipped where needed so the surviving face is
     * the one turned away from the body.
     *
     * <p>The endpoint is the covering sheet's own half thickness. Deriving it
     * from the bone instead gives a ball as wide as the whole panel is deep,
     * which parks the two layers a visible gap apart.
     */
    private static CollisionProxyPlan.MeshCollider sheet(
            Panel outer,
            Panel inner
    ) {
        PhysicsBoneGeometry.CubeBox cube = inner.cube();
        int minor = inner.minorAxis();
        Vector3f[] axes = {
                new Vector3f(cube.axisX()),
                new Vector3f(cube.axisY()),
                new Vector3f(cube.axisZ())
        };
        // Negating one axis leaves an identical box: the tests are symmetric
        // in each axis, so only the open side's sign changes.
        if (axes[minor].dot(inner.normal()) < 0.0F) {
            axes[minor].negate();
        }
        return new CollisionProxyPlan.MeshCollider(
                CollisionReference.of(inner.node()),
                new Vector3f(cube.center()),
                axes[0],
                axes[1],
                axes[2],
                new Vector3f(cube.half()),
                Optional.of(outer.thickness() * PIXELS_PER_BLOCK),
                0.0F,
                minor
        );
    }

    /** Whether the two plates could form a stack at all. */
    private boolean stackable(Panel outer, Panel inner) {
        return inner.node() != outer.node()
                && !chainId(outer.node()).equals(chainId(inner.node()))
                && !inner.node().isDescendantOf(outer.node())
                && !outer.node().isDescendantOf(inner.node())
                && Math.abs(outer.normal().dot(inner.normal())) >= PARALLEL
                && outer.outward().dot(inner.outward()) >= SAME_SIDE;
    }

    /** Whether {@code inner} sits behind {@code outer}'s outward face. */
    private static boolean covered(Panel outer, Panel inner) {
        Vector3f delta = new Vector3f(inner.cube().center())
                .sub(outer.cube().center());
        float separation = delta.dot(outer.normal());
        float thickness = outer.thickness() + inner.thickness();
        // The band excludes both a shared surface and a panel that merely
        // happens to be parallel somewhere else on the body.
        float coplanar = 2.0F * COPLANAR_RATIO
                * Math.min(outer.thickness(), inner.thickness());
        if (separation > -coplanar
                || separation < -(thickness + MAX_LAYER_GAP)) {
            return false;
        }
        for (int index = 0; index < 3; index++) {
            if (index == outer.minorAxis()) {
                continue;
            }
            Vector3f axis = axis(outer.cube(), index);
            float outerHalf = outer.cube().half().get(index);
            float innerHalf = extentAlong(inner.cube(), axis);
            float overlap = outerHalf + innerHalf
                    - Math.abs(delta.dot(axis));
            if (overlap < MIN_OVERLAP
                    || overlap < 2.0F * Math.min(outerHalf, innerHalf)
                    * OVERLAP_RATIO) {
                return false;
            }
        }
        return true;
    }

    private boolean reachable(
            PhysicsBoneGeometry.Node drivenNode,
            Panel inner,
            float endpointReach
    ) {
        float margin = inner.cube().half().length()
                + inner.sweptRadius() * REFERENCE_ROTATION
                + REACH_MARGIN;
        return cone.distanceToSweep(
                drivenNode.pivot(),
                inner.cube().center(),
                endpointReach
        ) <= margin;
    }

    private String chainId(PhysicsBoneGeometry.Node node) {
        return selectionPlan.decision(node.bone()).chainId();
    }

    private List<Panel> collectPanels(PhysicsBoneGeometry.Analysis geometry) {
        List<Panel> output = new ArrayList<>();
        Vector3f corner = new Vector3f();
        for (PhysicsBoneGeometry.Node node : geometry.nodes()) {
            if (!node.hasGeometry() || !selectionPlan.isDriven(node.bone())) {
                continue;
            }
            for (PhysicsBoneGeometry.CubeBox cube : node.cubeBoxes()) {
                int minor = minorAxis(cube.half());
                if (!isPlate(cube.half(), minor)) {
                    continue;
                }
                Vector3f outward = outward(cube);
                if (outward == null) {
                    continue;
                }
                float facing = axis(cube, minor).dot(outward);
                if (Math.abs(facing) < FACING) {
                    continue;
                }
                output.add(new Panel(
                        node,
                        cube,
                        minor,
                        outward,
                        new Vector3f(axis(cube, minor))
                                .mul(Math.signum(facing)),
                        SegmentReach.sweptRadius(node, cube, corner)
                ));
            }
        }
        return List.copyOf(output);
    }

    /**
     * Horizontal direction from the body axis to this plate. A cube sitting on
     * the axis, or one whose face does not point away from it, has no inside
     * or outside and cannot take part in a stack.
     */
    private Vector3f outward(PhysicsBoneGeometry.CubeBox cube) {
        Vector3f output = new Vector3f(cube.center()).sub(bodyAxis);
        output.y = 0.0F;
        return output.lengthSquared() <= AXIS_EPSILON
                ? null
                : output.normalize();
    }

    /**
     * Horizontal centre of the torso. Layer order is meaningless without it,
     * so a model with no body simply never gets layer colliders.
     */
    private static Vector3f bodyAxis(BodyCollisionGeometry bodyGeometry) {
        if (bodyGeometry == null || !bodyGeometry.hasBody()) {
            return null;
        }
        PhysicsBoneGeometry.Bounds bounds = bodyGeometry.bodyBounds();
        return new Vector3f(
                (float) ((bounds.minX() + bounds.maxX()) * 0.5D),
                0.0F,
                (float) ((bounds.minZ() + bounds.maxZ()) * 0.5D)
        );
    }

    private static boolean isPlate(Vector3f half, int minor) {
        float thin = half.get(minor);
        float next = Float.MAX_VALUE;
        for (int index = 0; index < 3; index++) {
            if (index != minor) {
                next = Math.min(next, half.get(index));
            }
        }
        return thin <= next * PLATE_RATIO;
    }

    private static int minorAxis(Vector3f half) {
        if (half.x <= half.y && half.x <= half.z) {
            return 0;
        }
        return half.y <= half.z ? 1 : 2;
    }

    private static float extentAlong(
            PhysicsBoneGeometry.CubeBox cube,
            Vector3f axis
    ) {
        return Math.abs(cube.axisX().dot(axis)) * cube.half().x
                + Math.abs(cube.axisY().dot(axis)) * cube.half().y
                + Math.abs(cube.axisZ().dot(axis)) * cube.half().z;
    }

    private static Vector3f axis(
            PhysicsBoneGeometry.CubeBox cube,
            int index
    ) {
        return switch (index) {
            case 0 -> cube.axisX();
            case 1 -> cube.axisY();
            default -> cube.axisZ();
        };
    }

    /** One driven cloth plate, with its outward-facing normal precomputed. */
    private record Panel(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.CubeBox cube,
            int minorAxis,
            Vector3f outward,
            Vector3f normal,
            float sweptRadius
    ) {
        float thickness() {
            return cube.half().get(minorAxis);
        }
    }
}
