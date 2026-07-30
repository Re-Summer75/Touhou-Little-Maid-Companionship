package com.laixia.maidintelligence.feature.physics.client.benchmark;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySet;

import java.util.Locale;

import static com.laixia.maidintelligence.feature.physics.client.benchmark.CollisionBenchmarkFixture.Kind;
import static com.laixia.maidintelligence.feature.physics.client.benchmark.CollisionBenchmarkFixture.Scenario;
import static com.laixia.maidintelligence.feature.physics.client.benchmark.CollisionBenchmarkRunner.Result;

/**
 * Independent small-fixture collision cost breakdown.
 */
public final class CollisionBreakdownBenchmark {
    private CollisionBreakdownBenchmark() {
    }

    public static void run() {
        Scenario[] scenarios = {
                CollisionBenchmarkFixture.create(Kind.HEAD, 2),
                CollisionBenchmarkFixture.create(Kind.HEAD, 3),
                CollisionBenchmarkFixture.create(Kind.SKIRT, 2),
                CollisionBenchmarkFixture.create(Kind.SKIRT, 3)
        };
        System.out.printf(
                Locale.ROOT,
                "%nCollision breakdown benchmark "
                        + "(small Gecko fixtures, %,d warmup + %,d measured)%n",
                CollisionBenchmarkRunner.WARMUP_FRAMES,
                CollisionBenchmarkRunner.MEASURED_FRAMES
        );
        for (Scenario scenario : scenarios) {
            report(
                    scenario.label(),
                    CollisionBenchmarkRunner.run(scenario),
                    LayoutMetrics.from(scenario.layout())
            );
        }
    }

    private static void report(
            String label,
            Result result,
            LayoutMetrics metrics
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
                "%s: %.1f ns/frame, active/full nodes=%d/%d, "
                        + "driven segments with proxies=%d/%d, "
                        + "proxies=%d [Plane=%d, Sphere=%d, Capsule=%d, "
                        + "Box=%d], solve allocation=%s%n",
                label,
                result.nanosecondsPerFrame(),
                metrics.activeNodes(),
                metrics.fullNodes(),
                metrics.proxySegments(),
                metrics.drivenSegments(),
                metrics.totalProxies(),
                metrics.planes(),
                metrics.spheres(),
                metrics.capsules(),
                metrics.boxes(),
                allocation
        );
    }

    private record LayoutMetrics(
            int activeNodes,
            int fullNodes,
            int drivenSegments,
            int proxySegments,
            int totalProxies,
            int planes,
            int spheres,
            int capsules,
            int boxes
    ) {
        private static LayoutMetrics from(PhysicsSolverLayout layout) {
            int proxySegments = 0;
            int total = 0;
            int planes = 0;
            int spheres = 0;
            int capsules = 0;
            int boxes = 0;
            for (int node = 0; node < layout.activeNodeCount(); node++) {
                if (!layout.node(node).driven()) continue;
                CollisionProxySet proxies =
                        layout.node(node).constraint().collisionProxies();
                if (proxies.proxyCount() > 0) proxySegments++;
                total += proxies.proxyCount();
                for (int proxy = 0; proxy < proxies.proxyCount(); proxy++) {
                    CollisionProxyKind kind = proxies.proxy(proxy).kind();
                    planes += kind == CollisionProxyKind.PLANE ? 1 : 0;
                    spheres += kind == CollisionProxyKind.SPHERE ? 1 : 0;
                    capsules += kind == CollisionProxyKind.CAPSULE ? 1 : 0;
                    boxes += kind == CollisionProxyKind.BOX ? 1 : 0;
                }
            }
            return new LayoutMetrics(
                    layout.activeNodeCount(),
                    layout.fullBoneCount(),
                    layout.drivenBoneCount(),
                    proxySegments,
                    total,
                    planes,
                    spheres,
                    capsules,
                    boxes
            );
        }
    }
}
