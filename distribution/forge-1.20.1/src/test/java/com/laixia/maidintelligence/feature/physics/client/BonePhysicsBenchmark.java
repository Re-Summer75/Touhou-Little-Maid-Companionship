package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.Converter;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.RawGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.tree.RawGeometryTree;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.GeoBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.laixia.maidintelligence.feature.physics.client.benchmark.CollisionBreakdownBenchmark;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import org.joml.Vector3f;

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Locale;

/**
 * Standalone, non-gating microbenchmark. Absolute time is intentionally only
 * reported; behavioral correctness remains the responsibility of
 * {@link BonePhysicsVerification}.
 */
public final class BonePhysicsBenchmark {
    private static final int WARMUP_FRAMES = 20_000;
    private static final int MEASURED_FRAMES = 20_000;
    /**
     * Measured frames are split into rounds and the best round is reported.
     *
     * <p>One long average is at the mercy of whatever else the machine did
     * during it, and on this workload that was worth twenty percent — enough
     * to hide or invent the effect of a change being evaluated. The fastest
     * round is the one that came closest to running undisturbed, so comparing
     * fastest rounds compares the code rather than the machine's mood.
     */
    private static final int MEASURED_ROUNDS = 5;
    private static final IdentityHashMap<BoneModelSnapshot.Bone, Vector3f>
            INITIAL_ROTATIONS = new IdentityHashMap<>();
    private static volatile double blackhole;

    private BonePhysicsBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        GeoModel geoModel = loadWinefoxGeoModel();
        BoneModelSnapshot referenceModel =
                BonePhysicsVerificationSupport.coreModel(geoModel);
        BoneModelSnapshot legacyModel =
                BonePhysicsVerificationSupport.coreModel(geoModel);
        BoneModelSnapshot constrainedModel =
                BonePhysicsVerificationSupport.coreModel(geoModel);
        PhysicsBoneSelectionPlan referencePlan =
                PhysicsBoneDiscoverer.discover(
                        "geckolib:winefox",
                        referenceModel,
                        PhysicsMetadata.EMPTY
                );
        PhysicsBoneSelectionPlan legacyPlan =
                PhysicsBoneDiscoverer.discover(
                        "geckolib:winefox",
                        legacyModel,
                        PhysicsMetadata.EMPTY
                );
        PhysicsBoneSelectionPlan constrainedPlan =
                PhysicsBoneDiscoverer.discover(
                        "geckolib:winefox",
                        constrainedModel,
                        PhysicsMetadata.EMPTY
                );
        ReferenceSpringBoneSolver reference =
                new ReferenceSpringBoneSolver(
                        referenceModel,
                        referencePlan
                );
        PhysicsSolverLayout legacyLayout =
                PhysicsSolverLayout.build(legacyModel, legacyPlan);
        PhysicsSolverLayout constrainedLayout =
                PhysicsSolverLayout.build(
                        constrainedModel,
                        constrainedPlan
                );
        SpringBoneSolver legacy =
                new SpringBoneSolver(legacyLayout, false);
        SpringBoneSolver constrained =
                new SpringBoneSolver(constrainedLayout);

        warmReference(referenceModel, reference, WARMUP_FRAMES);
        warmOptimized(legacyModel, legacy, WARMUP_FRAMES);
        warmOptimized(constrainedModel, constrained, WARMUP_FRAMES);
        AllocationMeter allocations = AllocationMeter.create();
        BenchmarkResult baseline = measureReference(
                referenceModel,
                reference,
                allocations,
                MEASURED_FRAMES
        );
        BenchmarkResult activeLegacy = measureOptimized(
                legacyModel,
                legacy,
                allocations,
                MEASURED_FRAMES
        );
        BenchmarkResult activeConstrained = measureOptimized(
                constrainedModel,
                constrained,
                allocations,
                MEASURED_FRAMES
        );

        System.out.printf(
                Locale.ROOT,
                "Bone physics benchmark (winefox, %,d measured frames)%n",
                MEASURED_FRAMES
        );
        report(
                "recursive-full",
                baseline,
                legacyLayout.fullBoneCount(),
                legacyLayout.fullBoneCount()
        );
        report(
                "iterative-active-legacy",
                activeLegacy,
                legacyLayout.activeNodeCount(),
                legacyLayout.fullBoneCount()
        );
        report(
                "iterative-active-constrained",
                activeConstrained,
                constrainedLayout.activeNodeCount(),
                constrainedLayout.fullBoneCount()
        );
        System.out.printf(
                Locale.ROOT,
                "legacy-equivalent solver speedup: %.2fx%n",
                baseline.nanosecondsPerFrame()
                        / activeLegacy.nanosecondsPerFrame()
        );
        System.out.printf(
                Locale.ROOT,
                "constraint-layer cost: %.2fx legacy-active%n",
                activeConstrained.nanosecondsPerFrame()
                        / activeLegacy.nanosecondsPerFrame()
        );
        System.out.printf(
                Locale.ROOT,
                "constrained collision proxies: %d over %d driven segments%n",
                totalProxies(constrainedLayout),
                constrainedLayout.drivenBoneCount()
        );
        CollisionBreakdownBenchmark.run();
    }

    private static int totalProxies(PhysicsSolverLayout layout) {
        int count = 0;
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (node.driven()) {
                count += node.constraint().collisionProxies().proxyCount();
            }
        }
        return count;
    }

    private static void warmReference(
            BoneModelSnapshot model,
            ReferenceSpringBoneSolver solver,
            int frames
    ) {
        Vector3f acceleration = new Vector3f();
        for (int frame = 0; frame < frames; frame++) {
            resetPose(model, frame);
            motion(frame, acceleration);
            solver.solve(
                    acceleration,
                    yawRate(frame),
                    deltaSeconds(frame),
                    false
            );
        }
    }

    private static void warmOptimized(
            BoneModelSnapshot model,
            SpringBoneSolver solver,
            int frames
    ) {
        Vector3f acceleration = new Vector3f();
        for (int frame = 0; frame < frames; frame++) {
            resetPose(model, frame);
            motion(frame, acceleration);
            solver.solve(
                    acceleration,
                    yawRate(frame),
                    deltaSeconds(frame),
                    false
            );
        }
    }

    private static BenchmarkResult measureReference(
            BoneModelSnapshot model,
            ReferenceSpringBoneSolver solver,
            AllocationMeter allocations,
            int frames
    ) {
        Vector3f acceleration = new Vector3f();
        long allocatedBytes = allocations.available() ? 0L : -1L;
        double best = Double.POSITIVE_INFINITY;
        double checksum = 0.0D;
        int perRound = roundLength(frames);
        int frame = 0;
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            long solveNanos = 0L;
            for (int step = 0; step < perRound; step++, frame++) {
                resetPose(model, frame);
                motion(frame, acceleration);
                long allocatedBefore = allocations.currentThreadBytes();
                long start = System.nanoTime();
                solver.solve(
                        acceleration,
                        yawRate(frame),
                        deltaSeconds(frame),
                        false
                );
                solveNanos += System.nanoTime() - start;
                allocatedBytes = allocations.accumulate(
                        allocatedBytes,
                        allocatedBefore,
                        allocations.currentThreadBytes()
                );
                checksum += model.topLevelBones().get(0).getRotationX();
            }
            best = Math.min(best, solveNanos / (double) perRound);
        }
        blackhole = checksum;
        return BenchmarkResult.of(best, frame, allocatedBytes);
    }

    private static BenchmarkResult measureOptimized(
            BoneModelSnapshot model,
            SpringBoneSolver solver,
            AllocationMeter allocations,
            int frames
    ) {
        Vector3f acceleration = new Vector3f();
        long allocatedBytes = allocations.available() ? 0L : -1L;
        double best = Double.POSITIVE_INFINITY;
        double checksum = 0.0D;
        int perRound = roundLength(frames);
        int frame = 0;
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            long solveNanos = 0L;
            for (int step = 0; step < perRound; step++, frame++) {
                resetPose(model, frame);
                motion(frame, acceleration);
                long allocatedBefore = allocations.currentThreadBytes();
                long start = System.nanoTime();
                solver.solve(
                        acceleration,
                        yawRate(frame),
                        deltaSeconds(frame),
                        false
                );
                solveNanos += System.nanoTime() - start;
                allocatedBytes = allocations.accumulate(
                        allocatedBytes,
                        allocatedBefore,
                        allocations.currentThreadBytes()
                );
                checksum += solver.lastPeakDeflection();
            }
            best = Math.min(best, solveNanos / (double) perRound);
        }
        blackhole = checksum;
        return BenchmarkResult.of(best, frame, allocatedBytes);
    }

    private static int roundLength(int frames) {
        return Math.max(1, frames / MEASURED_ROUNDS);
    }

    private static void resetPose(BoneModelSnapshot model, int frame) {
        int ordinal = 0;
        for (BoneModelSnapshot.Bone root : model.topLevelBones()) {
            ordinal = resetPose(root, frame, ordinal);
        }
    }

    private static int resetPose(
            BoneModelSnapshot.Bone bone,
            int frame,
            int ordinal
    ) {
        Vector3f initial = initialRotation(bone);
        float wave = (float) Math.sin(
                frame * 0.071D + ordinal * 0.193D
        );
        bone.setRotationX(initial.x + wave * 0.018F);
        bone.setRotationY(initial.y - wave * 0.011F);
        bone.setRotationZ(initial.z + wave * 0.009F);
        bone.setPositionX(0.0F);
        bone.setPositionY(0.0F);
        bone.setPositionZ(0.0F);
        int next = ordinal + 1;
        for (BoneModelSnapshot.Bone child : bone.children()) {
            next = resetPose(child, frame, next);
        }
        return next;
    }

    private static Vector3f initialRotation(BoneModelSnapshot.Bone bone) {
        Vector3f rotation = INITIAL_ROTATIONS.get(bone);
        if (rotation == null) {
            BoneModelSnapshot.RestPose initial = bone.getInitialSnapshot();
            rotation = new Vector3f(
                    initial.rotationValueX,
                    initial.rotationValueY,
                    initial.rotationValueZ
            );
            INITIAL_ROTATIONS.put(bone, rotation);
        }
        return rotation;
    }

    private static void motion(int frame, Vector3f output) {
        output.set(
                (float) Math.sin(frame * 0.12D) * 0.20F,
                0.0F,
                (float) Math.cos(frame * 0.08D) * 0.16F
        );
    }

    private static float yawRate(int frame) {
        return (float) Math.sin(frame * 0.05D) * 0.35F;
    }

    private static float deltaSeconds(int frame) {
        return frame % 4 == 0 ? 1.0F / 30.0F : 1.0F / 60.0F;
    }

    private static GeoModel loadWinefoxGeoModel() throws Exception {
        Path modelPath = Path.of(
                "geckolib_model_reference",
                "models",
                "entity",
                "winefox.json"
        );
        if (!Files.isRegularFile(modelPath)) {
            throw new IllegalStateException(
                    "Tracked winefox fixture is missing"
            );
        }
        RawGeoModel raw;
        try (InputStream input = Files.newInputStream(modelPath)) {
            raw = Converter.fromInputStream(input);
        }
        return GeoBuilder.getGeoBuilder().constructGeoModel(
                RawGeometryTree.parseHierarchy(raw)
        );
    }

    private static void report(
            String label,
            BenchmarkResult result,
            int visitedNodes,
            int fullNodes
    ) {
        String allocation = result.allocatedBytesPerFrame() < 0.0D
                ? "n/a"
                : String.format(
                        Locale.ROOT,
                        "%.2f B/frame",
                        result.allocatedBytesPerFrame()
                );
        System.out.printf(
                Locale.ROOT,
                "%s: %.1f ns/frame, nodes=%d/%d, allocation=%s%n",
                label,
                result.nanosecondsPerFrame(),
                visitedNodes,
                fullNodes,
                allocation
        );
    }

    private record BenchmarkResult(
            double nanosecondsPerFrame,
            double allocatedBytesPerFrame
    ) {
        private static BenchmarkResult of(
                double bestRoundNanosPerFrame,
                int frames,
                long allocatedBytes
        ) {
            return new BenchmarkResult(
                    bestRoundNanosPerFrame,
                    allocatedBytes < 0L
                            ? -1.0D
                            : allocatedBytes / (double) frames
            );
        }
    }

    private static final class AllocationMeter {
        private final com.sun.management.ThreadMXBean bean;
        private final long threadId;

        private AllocationMeter(
                com.sun.management.ThreadMXBean bean,
                long threadId
        ) {
            this.bean = bean;
            this.threadId = threadId;
        }

        private static AllocationMeter create() {
            java.lang.management.ThreadMXBean platform =
                    ManagementFactory.getThreadMXBean();
            if (!(platform instanceof com.sun.management.ThreadMXBean bean)
                    || !bean.isThreadAllocatedMemorySupported()) {
                return new AllocationMeter(null, -1L);
            }
            if (!bean.isThreadAllocatedMemoryEnabled()) {
                bean.setThreadAllocatedMemoryEnabled(true);
            }
            return new AllocationMeter(
                    bean,
                    Thread.currentThread().getId()
            );
        }

        private long currentThreadBytes() {
            return bean == null
                    ? -1L
                    : bean.getThreadAllocatedBytes(threadId);
        }

        private boolean available() {
            return bean != null;
        }

        private long accumulate(long total, long before, long after) {
            return total < 0L || before < 0L || after < 0L
                    ? -1L
                    : total + after - before;
        }
    }
}
