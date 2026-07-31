package com.laixia.maidintelligence.feature.physics.client.audit;

import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySource;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProxyDebugData;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class MeshPenetrationSummary {
    private MeshPenetrationSummary() {
    }

    static AuditRow audit(Path path) throws Exception {
        String name = path.getFileName().toString();
        BoneModelSnapshot model = AuditSupport.loadModel(path);
        PhysicsSolverLayout layout = AuditSupport.buildLayout(
                "audit:" + name, model
        );
        /*
         * The layout keeps only leverArm and segmentLength, both scalars along
         * the axis, so the mesh has to come from a separate analysis and be
         * matched back by bone identity.
         */
        PhysicsBoneGeometry.Analysis geometry = AuditSupport.analyze(model);
        AuditRow row = measure(layout, geometry, model);
        AuditRow.print(name.replace(".json", ""), row);
        return row;
    }

    private static AuditRow measure(
            PhysicsSolverLayout layout,
            PhysicsBoneGeometry.Analysis geometry,
            BoneModelSnapshot model
    ) {
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        List<BoneModelSnapshot.Bone> legs = AuditSupport.legs(model);
        int[] driven = AuditSupport.drivenSegments(layout);
        AuditRow row = new AuditRow();
        row.segments = driven.length;
        if (driven.length == 0) {
            return row;
        }

        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        for (int frame = 0; frame < AuditSupport.SETTLE; frame++) {
            solver.restoreAnimationPose();
            AuditSupport.sit(legs);
            solver.solve(new Vector3f(), 0.0F, AuditSupport.DT, false);
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

        for (int frame = 0; frame < AuditSupport.SAMPLE; frame++) {
            solver.restoreAnimationPose();
            AuditSupport.sit(legs);
            solver.solve(new Vector3f(), 0.0F, AuditSupport.DT, false);
            for (int slot = 0; slot < driven.length; slot++) {
                int node = driven[slot];
                PhysicsSolverLayout.Node layoutNode = layout.node(node);
                String label = layoutNode.bone().getName();

                solver.copyCurrentDirection(
                        layoutNode.drivenSlot(), current
                );
                step.set(current).sub(previous[slot]);
                float travelled = step.length();
                boolean reversed = travelled > AuditSupport.MOVED
                        && lastStep[slot].lengthSquared() > 1.0E-8F
                        && step.dot(lastStep[slot]) < 0.0F;
                if (reversed) {
                    reversals[slot]++;
                    /*
                     * Frames between direction changes, summed so a mean can be
                     * taken. Amplitude alone cannot tell a buzz from a swing: a
                     * segment trembling at the step rate and one swinging across
                     * its cone can cover the same distance per frame, and what
                     * separates them is how often they turn round. A half-period
                     * of one or two frames is the frame-rate chatter this is
                     * hunting; ten or more is motion.
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
                    float sunk = AuditSupport.meshDepth(
                            mesh, pivot, tip, data
                    );
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
                        depth[slot] * AuditSupport.PIXELS_PER_BLOCK,
                        layout.node(driven[slot]).constraint()
                                .copyMeshHalfExtents(new Vector3f())
                                .length() * AuditSupport.PIXELS_PER_BLOCK,
                        layout.node(driven[slot]).kinematics().leverArm()
                                * AuditSupport.PIXELS_PER_BLOCK
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
             * a segment swinging somewhere the same as one trembling in place;
             * the ratio alone ranks a limit cycle of 0.0003 rad level with one of
             * 0.27, though the first moves its tip by under a hundredth of a pixel
             * and cannot be seen at all. Scaling by the lever arm puts an angle
             * where it belongs, since the same rotation on a long strand shows
             * far more than on a stub. Read together with revs, which says whether
             * that travel was retraced or spent going somewhere.
             */
            row.considerJitter(
                    path[slot] / AuditSupport.SAMPLE
                            * layout.node(driven[slot]).kinematics().leverArm()
                            * AuditSupport.PIXELS_PER_BLOCK,
                    reversals[slot],
                    gapCount[slot] > 0
                            ? gapSum[slot] / (float) gapCount[slot]
                            : 0.0F,
                    layout.node(driven[slot]).bone().getName()
            );
        }
        return row;
    }
}
