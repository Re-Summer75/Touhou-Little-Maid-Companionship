package com.laixia.maidintelligence.feature.physics.client.audit;

import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySet;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySource;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProxyDebugData;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.layout.SwingRange;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;

final class MeshPenetrationDiagnostics {
    private MeshPenetrationDiagnostics() {
    }

    /** Lists the exact collider references attached to one driven segment. */
    static void proxies(String model, String bone) throws Exception {
        BoneModelSnapshot geo = AuditSupport.loadModel(
                AuditSupport.modelPath(model)
        );
        PhysicsSolverLayout layout = AuditSupport.buildLayout(
                "audit:proxies:" + model, geo
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
     * Frame-by-frame log of one segment, to see what a buzz is made of rather
     * than infer it from a total. Prints the step the pose took, which collider
     * sources were penetrating, and the depth each was at.
     */
    static void trace(String model, String bone) throws Exception {
        BoneModelSnapshot geo = AuditSupport.loadModel(
                AuditSupport.modelPath(model)
        );
        PhysicsSolverLayout layout = AuditSupport.buildLayout(
                "trace:" + model, geo
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
        List<BoneModelSnapshot.Bone> legs = AuditSupport.legs(geo);
        PhysicsBoneGeometry.Analysis geometry = AuditSupport.analyze(geo);
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
        AuditSupport.sit(legs);
        solver.copyCurrentDirection(slot, restDirection);
        for (int frame = 0; frame < AuditSupport.SETTLE; frame++) {
            solver.restoreAnimationPose();
            AuditSupport.sit(legs);
            solver.solve(new Vector3f(), 0.0F, AuditSupport.DT, false);
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
        for (int frame = 0; frame < AuditSupport.SAMPLE; frame++) {
            solver.restoreAnimationPose();
            AuditSupport.sit(legs);
            solver.solve(new Vector3f(), 0.0F, AuditSupport.DT, false);
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
                    deepest = Math.max(deepest, AuditSupport.meshDepth(
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
                    minAuto * AuditSupport.PIXELS_PER_BLOCK,
                    minLayer * AuditSupport.PIXELS_PER_BLOCK,
                    solver.lastConstraintProjectionCount(),
                    solver.lastCollisionProjectionCount(),
                    solver.projectionDamping(slot),
                    solver.projectionReversals(slot),
                    deepest * AuditSupport.PIXELS_PER_BLOCK,
                    layout.node(node).constraint()
                            .copyMeshHalfExtents(new Vector3f())
                            .length() * AuditSupport.PIXELS_PER_BLOCK,
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
}
