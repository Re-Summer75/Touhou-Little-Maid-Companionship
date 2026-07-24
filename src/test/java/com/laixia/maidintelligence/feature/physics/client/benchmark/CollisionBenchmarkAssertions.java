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
        requireHeadPair(firstSet, head, "HairA");
        if (schema == 2) {
            require(secondSet.proxyCount() == 0,
                    "schema2 Head child unexpectedly received proxies");
            require(proxySegments(layout) == 1 && totalProxies(layout) == 2,
                    "schema2 Head no longer uses only its chain root");
        } else {
            requireHeadPair(secondSet, head, "HairB");
            require(proxySegments(layout) == 2 && totalProxies(layout) == 4,
                    "schema3 Head did not retain per-segment proxies");
        }
    }

    private static void requireHeadPair(
            CollisionProxySet proxies,
            int headIndex,
            String segment
    ) {
        require(
                proxies.proxyCount() == 2
                        && count(proxies, CollisionProxyKind.PLANE) == 1
                        && count(proxies, CollisionProxyKind.SPHERE) == 1
                        && count(proxies, CollisionProxyKind.CAPSULE) == 0,
                segment + " does not have exactly one Head Plane and Sphere"
        );
        for (int index = 0; index < proxies.proxyCount(); index++) {
            require(proxies.proxy(index).referenceNodeIndex() == headIndex,
                    segment + " proxy does not reference Head");
        }
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
        require(body >= 0 && left >= 0 && right >= 0,
                "schema3 SKIRT lost Body or reliable Leg references");
        requireSkirtSet(proxies(layout, first), body, left, right, "SkirtA");
        requireSkirtSet(proxies(layout, second), body, left, right, "SkirtB");
        require(proxySegments(layout) == 2 && totalProxies(layout) == 6,
                "schema3 SKIRT did not retain segmented Body/Leg proxies");
    }

    private static void requireSkirtSet(
            CollisionProxySet proxies,
            int body,
            int left,
            int right,
            String segment
    ) {
        require(proxies.proxyCount() == 3
                        && count(proxies, CollisionProxyKind.CAPSULE) == 3,
                segment + " does not have three Body/Leg Capsules");
        boolean bodySeen = false;
        boolean leftSeen = false;
        boolean rightSeen = false;
        for (int index = 0; index < proxies.proxyCount(); index++) {
            int reference = proxies.proxy(index).referenceNodeIndex();
            bodySeen |= reference == body;
            leftSeen |= reference == left;
            rightSeen |= reference == right;
        }
        require(bodySeen && leftSeen && rightSeen,
                segment + " does not reference Body and both Legs");
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

    private static int count(
            CollisionProxySet proxies,
            CollisionProxyKind kind
    ) {
        int count = 0;
        for (int index = 0; index < proxies.proxyCount(); index++) {
            if (proxies.proxy(index).kind() == kind) count++;
        }
        return count;
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
