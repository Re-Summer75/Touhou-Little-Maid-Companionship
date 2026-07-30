package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.client.solver.SwingRange;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySource;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.CollisionProxyDebugData;
import org.joml.Quaternionf;
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
        if (args.length == 2 && args[0].startsWith("chain:")) {
            chain(args[0].substring("chain:".length()), args[1]);
            return;
        }
        if (args.length == 2 && args[0].startsWith("proxies:")) {
            proxies(args[0].substring("proxies:".length()), args[1]);
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
                "%-22s %4s %6s %7s %6s %5s %6s %5s %5s  %-16s %-16s %s%n",
                "model", "segs", "axis", "geom", "hits", "lyr",
                "buzz", "revs", "per", "worstAxis", "worstGeom", "worstJitter"
        );
        System.out.println("-".repeat(136));
        Row total = new Row();
        for (Path path : models) {
            total.accumulate(audit(path));
        }
        System.out.println("-".repeat(136));
        print("WORST OF " + models.size(), total);
    }

    /** Lists the exact collider references attached to one driven segment. */
    private static void proxies(String model, String bone) throws Exception {
        AnimatedGeoModel geo = new AnimatedGeoModel(
                BonePhysicsVerificationSupport.loadGeoModel(
                        BonePhysicsVerificationSupport.MODEL_DIRECTORY
                                .resolve(model)
                )
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                geo,
                PhysicsBoneDiscoverer.discover(
                        "audit:proxies:" + model,
                        geo,
                        PhysicsMetadata.EMPTY
                )
        );
        for (int node = 0; node < layout.activeNodeCount(); node++) {
            PhysicsSolverLayout.Node driven = layout.node(node);
            if (!driven.driven() || !bone.equals(driven.bone().getName())) {
                continue;
            }
            CollisionProxySet set = driven.constraint().collisionProxies();
            System.out.printf(
                    Locale.ROOT,
                    "%s / %s proxies=%d%n",
                    model,
                    bone,
                    set.proxyCount()
            );
            for (int slot = 0; slot < set.proxyCount(); slot++) {
                CollisionProxy proxy = set.proxy(slot);
                int reference = proxy.referenceNodeIndex();
                String referenceName = reference >= 0
                        && reference < layout.activeNodeCount()
                        ? layout.node(reference).bone().getName()
                        : "MODEL";
                System.out.printf(
                        Locale.ROOT,
                        "  %3d %-9s %-8s %s%n",
                        slot,
                        proxy.source(),
                        proxy.kind(),
                        referenceName
                );
            }
            return;
        }
        throw new IllegalArgumentException(
                "Driven bone not found: " + model + " / " + bone
        );
    }

    /**
     * Per-segment jitter for every driven bone whose name starts with a prefix.
     *
     * <p>The summary names only the worst segment per model, which cannot say
     * whether a chain shakes as a whole or carries one bad bone. Same seated pose
     * and same window as the summary, so the figures are comparable to it.
     */
    private static void chain(String model, String prefix) throws Exception {
        AnimatedGeoModel geo = new AnimatedGeoModel(
                BonePhysicsVerificationSupport.loadGeoModel(
                        BonePhysicsVerificationSupport.MODEL_DIRECTORY
                                .resolve(model)
                )
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                geo,
                PhysicsBoneDiscoverer.discover(
                        "chain:" + model, geo, PhysicsMetadata.EMPTY
                )
        );
        chainReport(
                layout, geo, PhysicsBoneGeometry.analyze(geo), prefix, model
        );
    }

    private static void chainReport(
            PhysicsSolverLayout layout,
            AnimatedGeoModel geo,
            PhysicsBoneGeometry.Analysis geometry,
            String prefix,
            String model
    ) {
        List<AnimatedGeoBone> legs = legs(geo);
        List<Integer> picked = new ArrayList<>();
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (node.driven()
                    && node.bone().getName().startsWith(prefix)) {
                picked.add(index);
            }
        }
        if (picked.isEmpty()) {
            System.out.println("no driven segment starting with " + prefix);
            return;
        }
        System.out.println("=== " + model + " / " + prefix + "* (seated) ===");
        System.out.printf(
                Locale.ROOT,
                "%-10s %6s %5s %5s %7s %6s %6s %5s %5s %5s %5s %5s%n",
                "bone", "buzz", "revs", "per", "maxStep", "geom", "axis",
                "prox", "hitF", "swgF", "colF", "sup"
        );
        runChain(layout, geometry, picked, legs);
    }

    private static void runChain(
            PhysicsSolverLayout layout,
            PhysicsBoneGeometry.Analysis geometry,
            List<Integer> picked,
            List<AnimatedGeoBone> legs
    ) {
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        int size = picked.size();
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        for (int frame = 0; frame < SETTLE; frame++) {
            solver.restoreAnimationPose();
            sit(legs);
            solver.solve(new Vector3f(), 0.0F, DT, false);
        }
        Vector3f[] previous = new Vector3f[size];
        Vector3f[] lastStep = new Vector3f[size];
        float[] path = new float[size];
        float[] peak = new float[size];
        int[] revs = new int[size];
        int[] gapSum = new int[size];
        int[] gapCount = new int[size];
        int[] lastRev = new int[size];
        for (int index = 0; index < size; index++) {
            previous[index] = new Vector3f();
            lastStep[index] = new Vector3f();
            lastRev[index] = -1;
            solver.copyCurrentDirection(
                    layout.node(picked.get(index)).drivenSlot(),
                    previous[index]
            );
        }
        Tally tally = new Tally(size);
        sampleChain(
                solver, layout, picked, legs, previous, lastStep,
                path, peak, revs, gapSum, gapCount, lastRev, tally
        );
        reportChain(
                solver, layout, geometry, picked,
                path, peak, revs, gapSum, gapCount, tally
        );
    }

    private static void sampleChain(
            SpringBoneSolver solver,
            PhysicsSolverLayout layout,
            List<Integer> picked,
            List<AnimatedGeoBone> legs,
            Vector3f[] previous,
            Vector3f[] lastStep,
            float[] path,
            float[] peak,
            int[] revs,
            int[] gapSum,
            int[] gapCount,
            int[] lastRev,
            Tally tally
    ) {
        Vector3f current = new Vector3f();
        Vector3f step = new Vector3f();
        for (int frame = 0; frame < SAMPLE; frame++) {
            solver.restoreAnimationPose();
            sit(legs);
            solver.solve(new Vector3f(), 0.0F, DT, false);
            for (int index = 0; index < picked.size(); index++) {
                solver.copyCurrentDirection(
                        layout.node(picked.get(index)).drivenSlot(), current
                );
                step.set(current).sub(previous[index]);
                float travelled = step.length();
                peak[index] = Math.max(peak[index], travelled);
                boolean reversed = travelled > MOVED
                        && lastStep[index].lengthSquared() > 1.0E-8F
                        && step.dot(lastStep[index]) < 0.0F;
                if (reversed) {
                    revs[index]++;
                    path[index] += travelled;
                    if (lastRev[index] >= 0) {
                        gapSum[index] += frame - lastRev[index];
                        gapCount[index]++;
                    }
                    lastRev[index] = frame;
                }
                if (travelled > MOVED) {
                    lastStep[index].set(step);
                }
                previous[index].set(current);
                census(
                        solver,
                        picked.get(index),
                        layout.node(picked.get(index)).drivenSlot(),
                        index,
                        reversed,
                        tally
                );
            }
        }
    }

    /**
     * Per-frame contact record for one segment.
     *
     * <p>Reading contact once after the window closes says nothing: a segment in
     * contact for 119 frames that happens to be clear on the last one reports no
     * contact at all. These count over the whole window instead, and separately
     * over the frames the step reversed, which is what distinguishes a segment
     * shaking because a collider keeps pushing it from one shaking for its own
     * reasons.
     */
    private static final class Tally {
        private final int[] framesTouching;
        private final int[] framesTouchingWhenReversed;
        private final float[] deepestAxis;
        private final int[] maxContacts;
        private final float[] supportSum;
        /** Frames each bound actually moved this segment. */
        private final int[] framesSwung;
        private final int[] framesCollided;

        private Tally(int size) {
            framesTouching = new int[size];
            framesTouchingWhenReversed = new int[size];
            deepestAxis = new float[size];
            maxContacts = new int[size];
            supportSum = new float[size];
            framesSwung = new int[size];
            framesCollided = new int[size];
        }
    }

    private static void census(
            SpringBoneSolver solver,
            int node,
            int drivenSlot,
            int index,
            boolean reversed,
            Tally tally
    ) {
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        int count = solver.preparedProxyCount(node);
        int touching = 0;
        for (int proxy = 0; proxy < count; proxy++) {
            if (!solver.copyPreparedCollisionProxy(node, proxy, data)) {
                continue;
            }
            if (data.clearance < 0.0F) {
                touching++;
                tally.deepestAxis[index] = Math.min(
                        tally.deepestAxis[index], data.clearance
                );
            }
        }
        if (touching > 0) {
            tally.framesTouching[index]++;
            if (reversed) {
                tally.framesTouchingWhenReversed[index]++;
            }
        }
        tally.supportSum[index] += solver.contactSupport(drivenSlot);
        int source = solver.lastProjectionSource(drivenSlot);
        if ((source & 1) != 0) {
            tally.framesSwung[index]++;
        }
        if ((source & 2) != 0) {
            tally.framesCollided[index]++;
        }
        tally.maxContacts[index] = Math.max(
                tally.maxContacts[index], touching
        );
    }

    private static void reportChain(
            SpringBoneSolver solver,
            PhysicsSolverLayout layout,
            PhysicsBoneGeometry.Analysis geometry,
            List<Integer> picked,
            float[] path,
            float[] peak,
            int[] revs,
            int[] gapSum,
            int[] gapCount,
            Tally tally
    ) {
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        Vector3f pivot = new Vector3f();
        Vector3f tip = new Vector3f();
        for (int index = 0; index < picked.size(); index++) {
            int node = picked.get(index);
            PhysicsSolverLayout.Node layoutNode = layout.node(node);
            float arm = layoutNode.kinematics().leverArm();
            float deepest = 0.0F;
            int count = solver.preparedProxyCount(node);
            boolean posed = solver.copyRuntimeTip(node, tip)
                    && solver.copyRuntimePivot(node, pivot);
            PhysicsBoneGeometry.Node mesh =
                    geometry.node(layoutNode.bone());
            for (int proxy = 0; proxy < count; proxy++) {
                if (!solver.copyPreparedCollisionProxy(node, proxy, data)) {
                    continue;
                }
                if (posed && data.kind == CollisionProxyKind.BOX) {
                    deepest = Math.max(
                            deepest, meshDepth(mesh, pivot, tip, data)
                    );
                }
            }
            System.out.printf(
                    Locale.ROOT,
                    "%-10s %6.2f %5d %5.1f %7.5f %6.2f %6.2f %5d %5d %5d"
                            + " %5d %5.2f%n",
                    layoutNode.bone().getName(),
                    path[index] / SAMPLE * arm * PIXELS_PER_BLOCK,
                    revs[index],
                    gapCount[index] > 0
                            ? gapSum[index] / (float) gapCount[index]
                            : 0.0F,
                    peak[index],
                    deepest * PIXELS_PER_BLOCK,
                    tally.deepestAxis[index] * PIXELS_PER_BLOCK,
                    tally.maxContacts[index],
                    tally.framesTouching[index],
                    tally.framesSwung[index],
                    tally.framesCollided[index],
                    tally.supportSum[index] / SAMPLE
            );
        }
    }

    private static void print(String name, Row row) {
        System.out.printf(
                Locale.ROOT,
                "%-22s %4d %6.2f %7.2f %6d %5d %6.1f %5d %5.1f  %-16s %-16s"
                        + " %s%n",
                name,
                row.segments,
                row.axis * PIXELS_PER_BLOCK,
                row.surface * PIXELS_PER_BLOCK,
                row.contacts,
                row.layerContacts,
                row.path,
                row.reversals,
                row.period,
                row.label,
                row.geometricLabel,
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
        PhysicsBoneGeometry.Analysis geometry =
                PhysicsBoneGeometry.analyze(geo);
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        int slot = layout.node(node).drivenSlot();
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        /*
         * The animated pose before any secondary motion, which is what the swing
         * cone is measured from. Taken on the settling frame rather than kept from
         * the rest pose: the sit animation rotates these bones itself, so the cone
         * travels with it.
         */
        Vector3f restDirection = new Vector3f();
        solver.restoreAnimationPose();
        sit(legs);
        solver.copyCurrentDirection(slot, restDirection);
        for (int frame = 0; frame < SETTLE; frame++) {
            solver.restoreAnimationPose();
            sit(legs);
            solver.solve(new Vector3f(), 0.0F, DT, false);
        }
        System.out.println("=== " + model + " / " + bone + " (seated) ===");
        System.out.printf(
                Locale.ROOT,
                "%5s %9s %7s %5s %5s %8s %8s %5s %5s %6s %4s %6s %5s %6s"
                        + " %8s %8s %5s%n",
                "frame", "step", "dot", "auto", "layer", "minAuto",
                    "minLayer", "swing", "coll", "damp", "revs", "geom",
                    "pad", "cone", "integ", "proj", "sup"
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
        Vector3f traceTip = new Vector3f();
        Vector3f tracePivot = new Vector3f();
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        solver.copyCurrentDirection(slot, previous);
        for (int frame = 0; frame < SAMPLE; frame++) {
            solver.restoreAnimationPose();
            sit(legs);
            solver.solve(new Vector3f(), 0.0F, DT, false);
            solver.copyCurrentDirection(slot, current);
            step.set(current).sub(previous);
            int auto = 0;
            int layer = 0;
            float minAuto = 0.0F;
            float minLayer = 0.0F;
            float deepest = 0.0F;
            int count = solver.preparedProxyCount(node);
            for (int proxy = 0; proxy < count; proxy++) {
                if (!solver.copyPreparedCollisionProxy(node, proxy, data)) {
                    continue;
                }
                if (data.kind == CollisionProxyKind.BOX
                        && solver.copyRuntimeTip(node, traceTip)
                        && solver.copyRuntimePivot(node, tracePivot)) {
                    deepest = Math.max(deepest, meshDepth(
                            geometry.node(layout.node(node).bone()),
                            tracePivot, traceTip, data
                    ));
                }
                if (data.clearance >= 0.0F) {
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
                    "%5d %9.5f %7.2f %5d %5d %8.3f %8.3f %5d %5d %6.2f %4d"
                            + " %6.2f %5.2f %6.3f %8.5f %8.5f %5.2f%n",
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
                    solver.projectionReversals(slot),
                    deepest * PIXELS_PER_BLOCK,
                    layout.node(node).constraint()
                            .copyMeshHalfExtents(new Vector3f())
                            .length() * PIXELS_PER_BLOCK,
                    /*
                     * How far off the animated pose the segment currently sits.
                     * Read against the cone printed above: riding the cap means
                     * the limiter is clamping every frame, and sitting well inside
                     * it means whatever the segment is doing, the cone is not the
                     * cause.
                     */
                    (float) Math.acos(Math.max(-1.0, Math.min(1.0,
                            current.dot(restDirection)))),
                    /*
                     * The step split at the one point both halves are visible.
                     * A committed direction cannot say whether a lurch came from
                     * the integrator or from the correction that followed it, and
                     * on winefox_magical that distinction was the whole answer:
                     * hatsidefront2's 0.373 rad frame was 0.024 of step and 0.394
                     * of projection.
                     */
                    solver.lastIntegratorStep(slot),
                    solver.lastProjectionStep(slot),
                    solver.contactSupport(slot)
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
        float[] depth = new float[driven.length];
        int[] reversals = new int[driven.length];
        int[] gapSum = new int[driven.length];
        int[] gapCount = new int[driven.length];
        int[] lastReversal = new int[driven.length];
        for (int index = 0; index < driven.length; index++) {
            previous[index] = new Vector3f();
            lastStep[index] = new Vector3f();
            lastReversal[index] = -1;
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
                boolean reversed = travelled > MOVED
                        && lastStep[slot].lengthSquared() > 1.0E-8F
                        && step.dot(lastStep[slot]) < 0.0F;
                if (reversed) {
                    reversals[slot]++;
                    /*
                     * Frames between direction changes, summed so a mean can be
                     * taken. Amplitude alone cannot tell a buzz from a swing: a
                     * segment trembling at the step rate and one swinging across
                     * its cone can cover the same distance per frame, and what
                     * separates them is how often they turn round. A half-period of
                     * one or two frames is the frame-rate chatter this is hunting;
                     * ten or more is motion.
                     */
                    if (lastReversal[slot] >= 0) {
                        gapSum[slot] += frame - lastReversal[slot];
                        gapCount[slot]++;
                    }
                    lastReversal[slot] = frame;
                }
                /*
                 * Only travel that reverses is counted. A segment swinging one
                 * way covers ground without shaking, and charging it for that
                 * ranked a freely settling hat ornament as the worst buzz in
                 * every model at 10.2 px a frame, while its steps ran the same
                 * direction forty frames running and it never touched anything.
                 */
                if (reversed) {
                    path[slot] += travelled;
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
                    float sunk = meshDepth(mesh, pivot, tip, data);
                    depth[slot] = Math.max(depth[slot], sunk);
                    row.considerGeometric(sunk, label);
                }
            }
        }
        /*
         * Per-segment depths, on request. The summary reports only the worst, and
         * chasing that one figure hides whether it is an outlier or the whole
         * skirt: on winefox it turned out eight segments were sinking about 2 px
         * each while the named one sank 4.
         */
        if (System.getProperty("tlm.dumpDepth") != null) {
            for (int slot = 0; slot < driven.length; slot++) {
                System.out.printf(
                        Locale.ROOT,
                        "  depth %-20s %6.2f pad=%5.2f arm=%6.2f%n",
                        layout.node(driven[slot]).bone().getName(),
                        depth[slot] * PIXELS_PER_BLOCK,
                        layout.node(driven[slot]).constraint()
                                .copyMeshHalfExtents(new Vector3f())
                                .length() * PIXELS_PER_BLOCK,
                        layout.node(driven[slot]).kinematics().leverArm()
                                * PIXELS_PER_BLOCK
                );
            }
        }
        for (int slot = 0; slot < driven.length; slot++) {
            /*
             * How far the tip moves in an average frame, in model pixels. This is
             * the figure the complaint was about: a part shaking visibly moves its
             * tip a noticeable distance every frame, and one settling does not.
             *
             * <p>Neither a bare path nor a bare ratio says that. Path alone counts
             * a segment swinging somewhere the same as one trembling in place; the
             * ratio alone ranks a limit cycle of 0.0003 rad level with one of 0.27,
             * though the first moves its tip by under a hundredth of a pixel and
             * cannot be seen at all. Scaling by the lever arm puts an angle where
             * it belongs, since the same rotation on a long strand shows far more
             * than on a stub. Read together with revs, which says whether that
             * travel was retraced or spent going somewhere.
             */
            row.considerJitter(
                    path[slot] / SAMPLE
                            * layout.node(driven[slot]).kinematics().leverArm()
                            * PIXELS_PER_BLOCK,
                    reversals[slot],
                    gapCount[slot] > 0
                            ? gapSum[slot] / (float) gapCount[slot]
                            : 0.0F,
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
     * How far the deepest corner of the segment's mesh lies inside the box, in
     * model pixels, or nought when every corner is outside it. This is the
     * penetration a viewer sees: cloth is drawn as its cubes, so a cube corner
     * inside a leg is visible however well the axis is placed.
     *
     * <p>Measured as a point-in-box depth, not as a clearance minus a reach.
     * Subtracting an extent taken along the contact normal charges the segment
     * for its own length whenever the normal is not square to the bone axis, and
     * a hanging panel is far longer than it is thick — that read a settled hem as
     * 15.8 px penetrating when nothing had gone inside anything, and it moved for
     * every solver change because it was pinned to geometry rather than to the
     * pose. Walking the corners cannot make that mistake.
     */
    private static float meshDepth(
            PhysicsBoneGeometry.Node node,
            Vector3f pivot,
            Vector3f tip,
            CollisionProxyDebugData data
    ) {
        if (node == null || node.cubeBoxes().isEmpty()) {
            return 0.0F;
        }
        /*
         * The cubes are authored in the rest pose, so they are carried onto the
         * solved axis before being tested. Skipping this would test the hem where
         * the artist left it rather than where the solver put it, which is the
         * whole question.
         */
        Vector3f restAxis = new Vector3f(node.center()).sub(node.pivot());
        Vector3f liveAxis = new Vector3f(tip).sub(pivot);
        if (restAxis.lengthSquared() <= 1.0E-12F
                || liveAxis.lengthSquared() <= 1.0E-12F) {
            return 0.0F;
        }
        Quaternionf pose = new Quaternionf().rotateTo(
                restAxis.normalize(), liveAxis.normalize()
        );
        Vector3f corner = new Vector3f();
        float deepest = 0.0F;
        for (PhysicsBoneGeometry.CubeBox box : node.cubeBoxes()) {
            for (int index = 0; index < 8; index++) {
                box.corner(index, corner);
                corner.sub(node.pivot());
                pose.transform(corner);
                corner.add(pivot);
                deepest = Math.max(deepest, boxDepth(corner, data));
            }
        }
        return deepest;
    }

    /**
     * Depth of a point inside an oriented box, nought if it is outside. The
     * smallest distance to a face, since that is the shortest way back out and so
     * what a viewer reads as how far in the part has sunk.
     */
    private static float boxDepth(
            Vector3f point,
            CollisionProxyDebugData data
    ) {
        Vector3f local = new Vector3f(point).sub(data.boxCenter);
        float[] along = {
                local.dot(data.boxAxisX),
                local.dot(data.boxAxisY),
                local.dot(data.boxAxisZ)
        };
        float[] half = {
                data.boxHalfExtents.x,
                data.boxHalfExtents.y,
                data.boxHalfExtents.z
        };
        float shallowest = Float.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++) {
            /*
             * A half-open box is missing the face on its open axis, so that
             * direction cannot contain the point and is not a way out either.
             */
            if (axis == data.boxOpenAxis) {
                continue;
            }
            float inside = half[axis] - Math.abs(along[axis]);
            if (inside <= 0.0F) {
                return 0.0F;
            }
            shallowest = Math.min(shallowest, inside);
        }
        return shallowest == Float.MAX_VALUE ? 0.0F : shallowest;
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
        /**
         * Worst gap the solver measured at an endpoint, negative when it wanted
         * more room than it had.
         *
         * <p>Not a penetration figure. The projection holds the axis a sheet's
         * half thickness clear of a surface so that the surface lands on it, so
         * an axis settling inside the padded bound is the intended result — on
         * kluonoa's Tail this reads -0.89 px while the mesh itself is 0.06 px in.
         * Read {@code geom} for what a viewer sees, and read this for whether the
         * solver is being asked for something it cannot deliver.
         */
        private float axis;
        private float surface;
        private float layerDepth;
        private float path;
        private int reversals;
        /**
         * Mean frames between direction changes on the buzziest segment.
         *
         * <p>The number that says what kind of motion it is. One or two frames is
         * the frame-rate chatter a viewer reads as shaking; ten or more is a part
         * swinging, however far it travels. Amplitude cannot make that
         * distinction — a lurch every thirtieth frame and a tremble every frame
         * report the same travel per frame.
         */
        private float period;
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

        // A depth, so the worst case is the largest, unlike the clearances.
        private void considerGeometric(float depth, String name) {
            if (depth > surface) {
                surface = depth;
                geometricLabel = name;
            }
        }

        private void considerLayer(float clearance, String name) {
            if (clearance < layerDepth) {
                layerDepth = clearance;
                layerLabel = name;
            }
        }

        private void considerJitter(
                float travel,
                int reversed,
                float halfPeriod,
                String name
        ) {
            if (travel > path) {
                path = travel;
                reversals = reversed;
                period = halfPeriod;
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
            considerJitter(
                    other.path, other.reversals, other.period,
                    other.jitterLabel
            );
        }
    }
}
