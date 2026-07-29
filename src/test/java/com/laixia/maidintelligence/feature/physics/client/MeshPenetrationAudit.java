package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.client.solver.SwingRange;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySource;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.CollisionProxyDebugData;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Diagnostic: measures penetration as a viewer sees it, surface against
 * surface, and reports whether the pose it settles into is actually at rest.
 *
 * <p>The solver resolves a point on the bone axis against a collider box. A
 * viewer sees a panel with real width against a body with real width, so the
 * two differ by however far the mesh extends past its own axis. Reporting both
 * splits "the solver thinks it is done" from "the model looks right".
 *
 * <p>Sitting is the pose that matters most here. Legs fold into a skirt, so the
 * overlap is deep, lasting, and entirely authored — exactly the case the rest
 * allowance exists for and the case where excusing too much is invisible to
 * every clearance-based check. The motion columns are there because the failure
 * mode is not only depth: a segment with nowhere legal to be buzzes, and that
 * shows up as accumulated path with reversals while the pose looks settled.
 */
public final class MeshPenetrationAudit {
    private static final float DT = 1.0F / 60.0F;
    private static final int SETTLE = 240;
    private static final int SAMPLE = 120;
    private static final float PIXELS_PER_BLOCK = 16.0F;
    /** Matches the solver's own body-sample floor. */
    private static final float BODY_MIN_FRACTION = 0.35F;
    /** Below this a step is numerical noise rather than motion. */
    private static final float MOVED = 1.0E-4F;
    /** X rotation in degrees, from the shipped sit animation. */
    private static final Map<String, Float> SIT_POSE = Map.ofEntries(
            Map.entry("LeftLeg", -62.5F),
            Map.entry("RightLeg", -62.5F),
            Map.entry("LeftLowerLeg", 165.0F),
            Map.entry("RightLowerLeg", 165.0F),
            Map.entry("LeftFoot", 35.0F),
            Map.entry("RightFoot", 40.0F),
            Map.entry("UpBody", 20.0F),
            Map.entry("UpperBody", -20.0F),
            Map.entry("DownBody", -10.0F),
            Map.entry("FrontClothe", -65.0F),
            Map.entry("BackClothe", -12.5F),
            Map.entry("FL2", 62.5F),
            Map.entry("FR2", 62.5F),
            Map.entry("FM2", 62.5F),
            Map.entry("BR2", -90.0F),
            Map.entry("BL2", -90.0F),
            Map.entry("BM2", -90.0F),
            Map.entry("LF2", 7.5F),
            Map.entry("LF3", 15.0F),
            Map.entry("LB2", -7.5F),
            Map.entry("RB", -7.5F),
            Map.entry("RF", 7.5F),
            Map.entry("RF3", 15.0F),
            Map.entry("wb", 65.0F),
            Map.entry("Leg", -35.0F),
            Map.entry("LongHair", 12.5F)
    );

    private MeshPenetrationAudit() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].startsWith("trace:")) {
            trace(args[0].substring("trace:".length()), args[1]);
            return;
        }
        List<Path> models;
        if (args.length > 0) {
            models = new ArrayList<>();
            for (String name : args) {
                models.add(
                        BonePhysicsVerificationSupport.MODEL_DIRECTORY
                                .resolve(name)
                );
            }
        } else {
            try (Stream<Path> stream = Files.list(
                    BonePhysicsVerificationSupport.MODEL_DIRECTORY
            )) {
                models = stream.filter(path -> path.getFileName()
                        .toString().endsWith(".json")).sorted().toList();
            }
        }
        System.out.printf(
                Locale.ROOT,
                "%-22s %4s %6s %7s %6s %5s %6s %5s  %-16s %s%n",
                "model", "segs", "axis", "geom", "hits", "lyr",
                "path", "revs", "worstAxis", "worstJitter"
        );
        System.out.println("-".repeat(118));
        Row total = new Row();
        for (Path path : models) {
            total.accumulate(audit(path));
        }
        System.out.println("-".repeat(118));
        print("WORST OF " + models.size(), total);
    }

    private static void print(String name, Row row) {
        System.out.printf(
                Locale.ROOT,
                "%-22s %4d %6.2f %7.2f %6d %5d %6.3f %5d  %-16s %s%n",
                name,
                row.segments,
                row.axis * PIXELS_PER_BLOCK,
                row.surface * PIXELS_PER_BLOCK,
                row.contacts,
                row.layerContacts,
                row.path,
                row.reversals,
                row.label,
                row.jitterLabel
        );
    }

    /**
     * Frame-by-frame log of one segment, to see what a buzz is made of rather
     * than infer it from a total. Prints the step the pose took, which collider
     * sources were penetrating, and the depth each was at.
     */
    private static void trace(String model, String bone) throws Exception {
        AnimatedGeoModel geo = new AnimatedGeoModel(
                BonePhysicsVerificationSupport.loadGeoModel(
                        BonePhysicsVerificationSupport.MODEL_DIRECTORY
                                .resolve(model)
                )
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                geo,
                PhysicsBoneDiscoverer.discover(
                        "trace:" + model, geo, PhysicsMetadata.EMPTY
                )
        );
        int node = -1;
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).driven()
                    && layout.node(index).bone().getName().equals(bone)) {
                node = index;
                break;
            }
        }
        if (node < 0) {
            System.out.println("no driven segment named " + bone);
            System.out.println("driven segments in " + model + ":");
            for (int index = 0; index < layout.activeNodeCount(); index++) {
                PhysicsSolverLayout.Node candidate = layout.node(index);
                if (candidate.driven()) {
                    System.out.printf(
                            Locale.ROOT,
                            "  %-24s %-16s proxies=%d%n",
                            candidate.bone().getName(),
                            candidate.decision().type(),
                            candidate.constraint().collisionProxies()
                                    .proxyCount()
                    );
                }
            }
            return;
        }
        List<AnimatedGeoBone> legs = legs(geo);
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        int slot = layout.node(node).drivenSlot();
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        for (int frame = 0; frame < SETTLE; frame++) {
            solver.restoreAnimationPose();
            sit(legs);
            solver.solve(new Vector3f(), 0.0F, DT, false);
        }
        System.out.println("=== " + model + " / " + bone + " (seated) ===");
        System.out.printf(
                Locale.ROOT,
                "%5s %9s %7s %5s %5s %8s %8s %5s %5s %6s %4s%n",
                "frame", "step", "dot", "auto", "layer", "minAuto",
                "minLayer", "swing", "coll", "damp", "revs"
        );
        /*
         * Where the two states of a cycle sit against the cone tells the two
         * candidate causes apart: both outside means nothing is clamping, one
         * of each means the clamp is the wall being bounced off.
         */
        float cap = SwingRange.maximum(layout.node(node));
        System.out.printf(
                Locale.ROOT, "cone half-angle = %.5f rad%n", cap
        );
        Vector3f previous = new Vector3f();
        Vector3f current = new Vector3f();
        Vector3f step = new Vector3f();
        Vector3f lastStep = new Vector3f();
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        solver.copyCurrentDirection(slot, previous);
        for (int frame = 0; frame < 40; frame++) {
            solver.restoreAnimationPose();
            sit(legs);
            solver.solve(new Vector3f(), 0.0F, DT, false);
            solver.copyCurrentDirection(slot, current);
            step.set(current).sub(previous);
            int auto = 0;
            int layer = 0;
            float minAuto = 0.0F;
            float minLayer = 0.0F;
            int count = solver.preparedProxyCount(node);
            for (int proxy = 0; proxy < count; proxy++) {
                if (!solver.copyPreparedCollisionProxy(node, proxy, data)
                        || data.clearance >= 0.0F) {
                    continue;
                }
                if (data.source == CollisionProxySource.LAYER) {
                    layer++;
                    minLayer = Math.min(minLayer, data.clearance);
                } else {
                    auto++;
                    minAuto = Math.min(minAuto, data.clearance);
                }
            }
            System.out.printf(
                    Locale.ROOT,
                    "%5d %9.5f %7.2f %5d %5d %8.3f %8.3f %5d %5d %6.2f %4d%n",
                    frame,
                    step.length(),
                    lastStep.lengthSquared() > 1.0E-9F
                            ? step.dot(lastStep) / (step.length()
                            * lastStep.length()) : 0.0F,
                    auto,
                    layer,
                    minAuto * PIXELS_PER_BLOCK,
                    minLayer * PIXELS_PER_BLOCK,
                    solver.lastConstraintProjectionCount(),
                    solver.lastCollisionProjectionCount(),
                    solver.projectionDamping(slot),
                    solver.projectionReversals(slot)
            );
            lastStep.set(step);
            previous.set(current);
        }
    }

    private static Row audit(Path path) throws Exception {
        String name = path.getFileName().toString();
        AnimatedGeoModel model = new AnimatedGeoModel(
                BonePhysicsVerificationSupport.loadGeoModel(path)
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "audit:" + name, model, PhysicsMetadata.EMPTY
                )
        );
        /*
         * The layout keeps only leverArm and segmentLength, both scalars along
         * the axis, so the mesh has to come from a separate analysis and be
         * matched back by bone identity.
         */
        PhysicsBoneGeometry.Analysis geometry =
                PhysicsBoneGeometry.analyze(model);
        Row row = measure(layout, geometry, model);
        print(name.replace(".json", ""), row);
        return row;
    }

    private static Row measure(
            PhysicsSolverLayout layout,
            PhysicsBoneGeometry.Analysis geometry,
            AnimatedGeoModel model
    ) {
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        List<AnimatedGeoBone> legs = legs(model);
        int[] driven = drivenSegments(layout);
        Row row = new Row();
        row.segments = driven.length;
        if (driven.length == 0) {
            return row;
        }

        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        for (int frame = 0; frame < SETTLE; frame++) {
            solver.restoreAnimationPose();
            sit(legs);
            solver.solve(new Vector3f(), 0.0F, DT, false);
        }

        Vector3f[] previous = new Vector3f[driven.length];
        Vector3f[] lastStep = new Vector3f[driven.length];
        float[] path = new float[driven.length];
        int[] reversals = new int[driven.length];
        for (int index = 0; index < driven.length; index++) {
            previous[index] = new Vector3f();
            lastStep[index] = new Vector3f();
            solver.copyCurrentDirection(
                    layout.node(driven[index]).drivenSlot(), previous[index]
            );
        }

        CollisionProxyDebugData data = new CollisionProxyDebugData();
        Vector3f current = new Vector3f();
        Vector3f step = new Vector3f();
        Vector3f tip = new Vector3f();
        Vector3f pivot = new Vector3f();

        for (int frame = 0; frame < SAMPLE; frame++) {
            solver.restoreAnimationPose();
            sit(legs);
            solver.solve(new Vector3f(), 0.0F, DT, false);
            for (int slot = 0; slot < driven.length; slot++) {
                int node = driven[slot];
                PhysicsSolverLayout.Node layoutNode = layout.node(node);
                String label = layoutNode.bone().getName();

                solver.copyCurrentDirection(
                        layoutNode.drivenSlot(), current
                );
                step.set(current).sub(previous[slot]);
                float travelled = step.length();
                /*
                 * Accumulated travel, per segment. A settled pose goes nowhere,
                 * so path is the jitter amplitude summed over the window: a
                 * segment buzzing between two surfaces racks up path while its
                 * position looks unchanged.
                 */
                path[slot] += travelled;
                if (travelled > MOVED
                        && lastStep[slot].lengthSquared() > 1.0E-8F
                        && step.dot(lastStep[slot]) < 0.0F) {
                    reversals[slot]++;
                }
                lastStep[slot].set(step);
                previous[slot].set(current);

                if (!solver.copyRuntimeTip(node, tip)
                        || !solver.copyRuntimePivot(node, pivot)) {
                    continue;
                }
                PhysicsBoneGeometry.Node mesh =
                        geometry.node(layoutNode.bone());
                int count = solver.preparedProxyCount(node);
                for (int proxy = 0; proxy < count; proxy++) {
                    if (!solver.copyPreparedCollisionProxy(
                            node, proxy, data
                    )) {
                        continue;
                    }
                    if (data.kind != CollisionProxyKind.BOX) {
                        continue;
                    }
                    boolean layer =
                            data.source == CollisionProxySource.LAYER;
                    if (!layer
                            && data.source != CollisionProxySource.AUTOMATIC) {
                        continue;
                    }
                    float reach = normalReach(mesh, pivot, tip, data);
                    if (data.clearance < 0.0F) {
                        row.contacts++;
                        if (layer) {
                            row.layerContacts++;
                        }
                    }
                    if (layer) {
                        row.considerLayer(data.clearance, label);
                        continue;
                    }
                    row.considerAxis(data.clearance, label);
                    /*
                     * Reported, not minimised. Reach is a property of the mesh
                     * and the contact direction alone, so once the solver has
                     * driven clearance to nothing this figure is pinned at
                     * -reach and no amount of solving moves it. Four separate
                     * attempts to improve it each left it where it was while
                     * driving axis clearance from -0.12 px to -3.98 px, which is
                     * the measurement that does answer to the solver. It stays
                     * here as the standing cost of tracking a sheet by a point
                     * on its axis, and it is read, not chased.
                     */
                    row.considerGeometric(data.clearance - reach, label);
                }
            }
        }
        for (int slot = 0; slot < driven.length; slot++) {
            row.considerJitter(
                    path[slot],
                    reversals[slot],
                    layout.node(driven[slot]).bone().getName()
            );
        }
        return row;
    }

    /**
     * The shipped {@code sit} pose, taken from winefox.animation.json rather
     * than invented. The values matter: this animation keyframes the driven
     * skirt bones themselves — FrontClothe to -65 degrees, the FM/FL/FR panels
     * forward 62.5, the back panels to -90 — so the authored pose already folds
     * cloth around legs that are themselves rotated into it. An invented pose
     * puts the legs somewhere the panels were never drawn to accommodate and
     * measures a configuration the model never renders.
     */
    private static void sit(List<AnimatedGeoBone> bones) {
        for (AnimatedGeoBone bone : bones) {
            Float degrees = SIT_POSE.get(bone.getName());
            if (degrees != null) {
                bone.setRotationX((float) Math.toRadians(degrees));
            }
        }
    }

    /**
     * How far the segment's mesh reaches past its endpoint along the direction
     * the collider pushes back. Taken along the contact normal rather than as
     * an omnidirectional maximum: a bow whose cubes fan out sideways reaches
     * far from its axis without any of that width facing the surface.
     */
    private static float normalReach(
            PhysicsBoneGeometry.Node node,
            Vector3f pivot,
            Vector3f tip,
            CollisionProxyDebugData data
    ) {
        if (node == null || node.cubeBoxes().isEmpty()) {
            return 0.0F;
        }
        Vector3f normal = contactNormal(data, tip);
        if (normal == null) {
            return 0.0F;
        }
        Vector3f axis = new Vector3f(tip).sub(pivot);
        if (axis.lengthSquared() <= 1.0E-12F) {
            return 0.0F;
        }
        axis.normalize();
        /*
         * The span is taken about the axis rather than about a pivot: the
         * authored pivot of an accessory can sit well away from its mesh, and
         * the solver uses a corrected one, so anchoring on either would report
         * that offset instead of a thickness.
         */
        Vector3f corner = new Vector3f();
        float nearest = Float.MAX_VALUE;
        float furthest = -Float.MAX_VALUE;
        for (PhysicsBoneGeometry.CubeBox box : node.cubeBoxes()) {
            for (int index = 0; index < 8; index++) {
                box.corner(index, corner);
                float along = corner.dot(normal);
                nearest = Math.min(nearest, along);
                furthest = Math.max(furthest, along);
            }
        }
        if (nearest > furthest) {
            return 0.0F;
        }
        // Half the extent: the endpoint tracks the middle of the sheet.
        return (furthest - nearest) * 0.5F;
    }

    /** Direction the collider pushes the endpoint, in model space. */
    private static Vector3f contactNormal(
            CollisionProxyDebugData data,
            Vector3f tip
    ) {
        Vector3f local = new Vector3f(tip).sub(data.boxCenter);
        float[] extents = {
                data.boxHalfExtents.x,
                data.boxHalfExtents.y,
                data.boxHalfExtents.z
        };
        Vector3f[] axes = {data.boxAxisX, data.boxAxisY, data.boxAxisZ};
        int shallowest = -1;
        float best = -Float.MAX_VALUE;
        for (int index = 0; index < 3; index++) {
            if (axes[index].lengthSquared() <= 1.0E-12F) {
                continue;
            }
            float over = Math.abs(local.dot(axes[index])) - extents[index];
            if (over > best) {
                best = over;
                shallowest = index;
            }
        }
        if (shallowest < 0) {
            return null;
        }
        Vector3f normal = new Vector3f(axes[shallowest]);
        return local.dot(normal) < 0.0F ? normal.negate() : normal;
    }

    /** Every bone the sit pose keyframes, whichever of them a model has. */
    private static List<AnimatedGeoBone> legs(AnimatedGeoModel model) {
        List<AnimatedGeoBone> found = new ArrayList<>();
        BonePhysicsVerificationSupport.forEachBone(model, bone -> {
            if (SIT_POSE.containsKey(bone.getName())) {
                found.add(bone);
            }
        });
        return found;
    }

    private static int[] drivenSegments(PhysicsSolverLayout layout) {
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

    private static final class Row {
        private int segments;
        private float axis;
        private float surface;
        private float layerDepth;
        private float deficit;
        private float path;
        private int reversals;
        private int contacts;
        private int layerContacts;
        private String label = "-";
        private String geometricLabel = "-";
        private String layerLabel = "-";
        private String jitterLabel = "-";

        private void considerAxis(float clearance, String name) {
            if (clearance < axis) {
                axis = clearance;
                label = name;
            }
        }

        private void considerGeometric(float clearance, String name) {
            if (clearance < surface) {
                surface = clearance;
                geometricLabel = name;
            }
        }

        private void considerLayer(float clearance, String name) {
            if (clearance < layerDepth) {
                layerDepth = clearance;
                layerLabel = name;
            }
        }

        private void considerJitter(float travel, int reversed, String name) {
            if (travel > path) {
                path = travel;
                reversals = reversed;
                jitterLabel = name;
            }
        }

        private void accumulate(Row other) {
            segments = Math.max(segments, other.segments);
            contacts = Math.max(contacts, other.contacts);
            layerContacts = Math.max(layerContacts, other.layerContacts);
            considerAxis(other.axis, other.label);
            considerGeometric(other.surface, other.geometricLabel);
            considerLayer(other.layerDepth, other.layerLabel);
            considerJitter(other.path, other.reversals, other.jitterLabel);
        }
    }
}
