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
        requireMeshSet(layout, first, "SkirtA");
        requireMeshSet(layout, second, "SkirtB");
        require(proxySegments(layout) == 2,
                "schema3 SKIRT did not keep collision on both segments");
    }

    /**
     * Selection is no longer capped, so the contract is coverage rather than
     * a fixed count: every reachable cube of the torso and both legs.
     */
    private static void requireMeshSet(
            PhysicsSolverLayout layout,
            int node,
            String segment
    ) {
        CollisionProxySet proxies = proxies(layout, node);
        require(proxies.proxyCount() > 0, segment + " lost mesh collision");
        boolean body = false;
        boolean left = false;
        boolean right = false;
        for (int index = 0; index < proxies.proxyCount(); index++) {
            require(proxies.proxy(index).kind() == CollisionProxyKind.BOX,
                    segment + " received a fitted shape instead of the mesh");
            String reference = layout
                    .node(proxies.proxy(index).referenceNodeIndex())
                    .bone()
                    .getName();
            body |= "Body".equals(reference);
            left |= "LeftLeg".equals(reference);
            right |= "RightLeg".equals(reference);
        }
        require(body && left && right,
                segment + " does not have torso plus both legs");
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
