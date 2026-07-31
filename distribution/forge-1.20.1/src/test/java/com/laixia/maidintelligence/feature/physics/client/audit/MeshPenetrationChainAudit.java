package com.laixia.maidintelligence.feature.physics.client.audit;

import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProxyDebugData;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class MeshPenetrationChainAudit {
    private MeshPenetrationChainAudit() {
    }

    /**
     * Per-segment jitter for every driven bone whose name starts with a prefix.
     *
     * <p>The summary names only the worst segment per model, which cannot say
     * whether a chain shakes as a whole or carries one bad bone. Same seated pose
     * and same window as the summary, so the figures are comparable to it.
     */
    static void run(String model, String prefix) throws Exception {
        BoneModelSnapshot geo = AuditSupport.loadModel(
                AuditSupport.modelPath(model)
        );
        PhysicsSolverLayout layout = AuditSupport.buildLayout(
                "chain:" + model, geo
        );
        chainReport(
                layout, geo, AuditSupport.analyze(geo), prefix, model
        );
    }

    private static void chainReport(
            PhysicsSolverLayout layout,
            BoneModelSnapshot geo,
            PhysicsBoneGeometry.Analysis geometry,
            String prefix,
            String model
    ) {
        List<BoneModelSnapshot.Bone> legs = AuditSupport.legs(geo);
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
            List<BoneModelSnapshot.Bone> legs
    ) {
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        int size = picked.size();
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        for (int frame = 0; frame < AuditSupport.SETTLE; frame++) {
            solver.restoreAnimationPose();
            AuditSupport.sit(legs);
            solver.solve(new Vector3f(), 0.0F, AuditSupport.DT, false);
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
            List<BoneModelSnapshot.Bone> legs,
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
        for (int frame = 0; frame < AuditSupport.SAMPLE; frame++) {
            solver.restoreAnimationPose();
            AuditSupport.sit(legs);
            solver.solve(new Vector3f(), 0.0F, AuditSupport.DT, false);
            for (int index = 0; index < picked.size(); index++) {
                solver.copyCurrentDirection(
                        layout.node(picked.get(index)).drivenSlot(), current
                );
                step.set(current).sub(previous[index]);
                float travelled = step.length();
                peak[index] = Math.max(peak[index], travelled);
                boolean reversed = travelled > AuditSupport.MOVED
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
                if (travelled > AuditSupport.MOVED) {
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
                            deepest,
                            AuditSupport.meshDepth(mesh, pivot, tip, data)
                    );
                }
            }
            System.out.printf(
                    Locale.ROOT,
                    "%-10s %6.2f %5d %5.1f %7.5f %6.2f %6.2f %5d %5d %5d"
                            + " %5d %5.2f%n",
                    layoutNode.bone().getName(),
                    path[index] / AuditSupport.SAMPLE * arm
                            * AuditSupport.PIXELS_PER_BLOCK,
                    revs[index],
                    gapCount[index] > 0
                            ? gapSum[index] / (float) gapCount[index]
                            : 0.0F,
                    peak[index],
                    deepest * AuditSupport.PIXELS_PER_BLOCK,
                    tally.deepestAxis[index] * AuditSupport.PIXELS_PER_BLOCK,
                    tally.maxContacts[index],
                    tally.framesTouching[index],
                    tally.framesSwung[index],
                    tally.framesCollided[index],
                    tally.supportSum[index] / AuditSupport.SAMPLE
            );
        }
    }
}
