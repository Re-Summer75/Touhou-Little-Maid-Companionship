package com.laixia.maidintelligence.feature.physics.client.benchmark;

import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySet;

import static com.laixia.maidintelligence.feature.physics.client.benchmark.CollisionBenchmarkFixture.Kind;

final class CollisionBenchmarkAssertions {
    private CollisionBenchmarkAssertions() {
    }

    static void verify(
            Kind kind,
            int schema,
            PhysicsSolverLayout layout
    ) {
        require(schema == 2 || schema == 3, "Unsupported benchmark schema");
        if (kind == Kind.HEAD) {
            verifyHead(schema, layout);
        } else {
            verifySkirt(schema, layout);
        }
    }

    private static void verifyHead(
            int schema,
            PhysicsSolverLayout layout
    ) {
        int head = indexOf(layout, "Head");
        int first = drivenIndex(layout, "HairA");
        int second = drivenIndex(layout, "HairB");
        CollisionProxySet firstSet = proxies(layout, first);
        CollisionProxySet secondSet = proxies(layout, second);
        require(head >= 0 && head < first,
                "Head fixture lost its ordered Head reference");
        require(
                firstSet.proxyCount() == 0
                        && secondSet.proxyCount() == 0
                        && proxySegments(layout) == 0
                        && totalProxies(layout) == 0,
                "Automatic Head collision remained enabled for schema "
                        + schema
        );
    }

    private static void verifySkirt(
            int schema,
            PhysicsSolverLayout layout
    ) {
        int first = drivenIndex(layout, "SkirtA");
        int second = drivenIndex(layout, "SkirtB");
        if (schema == 2) {
            require(proxies(layout, first).proxyCount() == 0
                            && proxies(layout, second).proxyCount() == 0
                            && totalProxies(layout) == 0,
                    "schema2 SKIRT unexpectedly enabled Body collision");
            return;
        }
        int body = indexOf(layout, "Body");
        int left = indexOf(layout, "LeftLeg");
        int right = indexOf(layout, "RightLeg");
        require(body >= 0 && left < 0 && right < 0,
                "schema3 SKIRT retained disabled Leg references");
        requireBodySet(proxies(layout, first), body, "SkirtA");
        requireBodySet(proxies(layout, second), body, "SkirtB");
        require(proxySegments(layout) == 2 && totalProxies(layout) == 2,
                "schema3 SKIRT did not retain Body-only proxies");
    }

    private static void requireBodySet(
            CollisionProxySet proxies,
            int body,
            String segment
    ) {
        require(proxies.proxyCount() == 1
                        && proxies.proxy(0).kind()
                        == CollisionProxyKind.CAPSULE
                        && proxies.proxy(0).referenceNodeIndex() == body,
                segment + " does not have exactly one Body Capsule");
    }

    private static int drivenIndex(
            PhysicsSolverLayout layout,
            String name
    ) {
        int index = indexOf(layout, name);
        require(index >= 0 && layout.node(index).driven(),
                "Missing driven benchmark segment " + name);
        return index;
    }

    private static int indexOf(PhysicsSolverLayout layout, String name) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (name.equals(layout.node(index).bone().getName())) return index;
        }
        return -1;
    }

    private static CollisionProxySet proxies(
            PhysicsSolverLayout layout,
            int index
    ) {
        return layout.node(index).constraint().collisionProxies();
    }

    private static int proxySegments(PhysicsSolverLayout layout) {
        int count = 0;
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).driven()
                    && proxies(layout, index).proxyCount() > 0) count++;
        }
        return count;
    }

    private static int totalProxies(PhysicsSolverLayout layout) {
        int count = 0;
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).driven()) {
                count += proxies(layout, index).proxyCount();
            }
        }
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
