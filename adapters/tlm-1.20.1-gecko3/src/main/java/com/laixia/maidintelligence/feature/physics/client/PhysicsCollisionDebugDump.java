package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProxyDebugData;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.Locale;

@OnlyIn(Dist.CLIENT)
final class PhysicsCollisionDebugDump {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PhysicsCollisionDebugDump() {
    }

    static Summary dump(EntityMaid maid) {
        SpringBoneSolver solver = MaidBonePhysics.lastSolver(maid);
        if (solver == null) {
            LOGGER.info("Collision runtime dump unavailable: no solver");
            return new Summary(0, 0);
        }
        LOGGER.info("=== Runtime collision dump ===");
        PhysicsSolverLayout layout = solver.layout();
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        Vector3f pivot = new Vector3f();
        Vector3f tip = new Vector3f();
        int proxyTotal = 0;
        int penetratingTotal = 0;
        for (int nodeIndex = 0;
             nodeIndex < layout.activeNodeCount();
             nodeIndex++) {
            PhysicsSolverLayout.Node node = layout.node(nodeIndex);
            if (!node.driven()) {
                continue;
            }
            boolean segmentReady = solver.copyRuntimePivot(nodeIndex, pivot)
                    && solver.copyRuntimeTip(nodeIndex, tip);
            int proxyCount = solver.preparedProxyCount(nodeIndex);
            LOGGER.info(
                    "collision node={} path={} slot={} runtime={} proxyCount={}",
                    nodeIndex,
                    node.path(),
                    node.drivenSlot(),
                    segmentReady ? vector(pivot) + " -> " + vector(tip)
                            : "unavailable",
                    proxyCount
            );
            proxyTotal += proxyCount;
            for (int proxyIndex = 0;
                 proxyIndex < proxyCount;
                 proxyIndex++) {
                if (!solver.copyPreparedCollisionProxy(
                        nodeIndex, proxyIndex, data
                )) {
                    LOGGER.info("  proxy={} unavailable", proxyIndex);
                    continue;
                }
                if (data.penetrating) {
                    penetratingTotal++;
                }
                LOGGER.info(
                        "  proxy={} kind={} source={} reference={}({}) origin={} pivot={}"
                                + " {} hitRadius={} leverArm={} clearance={}{}",
                        proxyIndex,
                        data.kind,
                        data.source,
                        data.referenceNodeIndex,
                        referenceName(layout, data.referenceNodeIndex),
                        vector(data.referenceOrigin),
                        vector(data.runtimePivot),
                        geometry(data),
                        decimal(data.scaledHitRadius),
                        decimal(data.leverArm),
                        decimal(data.clearance),
                        data.penetrating ? " PENETRATING" : ""
                );
            }
        }
        LOGGER.info(
                "=== End runtime collision dump: {} proxies, {} penetrating ===",
                proxyTotal,
                penetratingTotal
        );
        return new Summary(proxyTotal, penetratingTotal);
    }

    private static String referenceName(
            PhysicsSolverLayout layout,
            int referenceIndex
    ) {
        if (referenceIndex == -1) {
            return "MODEL";
        }
        if (referenceIndex < 0
                || referenceIndex >= layout.activeNodeCount()) {
            return "INVALID";
        }
        return layout.node(referenceIndex).bone().getName();
    }

    private static String geometry(CollisionProxyDebugData data) {
        return switch (data.kind) {
            case PLANE -> "point=" + vector(data.planePoint)
                    + " normal=" + vector(data.planeNormal);
            case SPHERE -> "center=" + vector(data.sphereCenter)
                    + " radius=" + decimal(data.sphereRadius)
                    + " effectiveRadius="
                    + decimal(data.sphereRadius + data.scaledHitRadius);
            case CAPSULE -> "start=" + vector(data.capsuleStart)
                    + " end=" + vector(data.capsuleEnd)
                    + " radius=" + decimal(data.capsuleRadius)
                    + " effectiveRadius="
                    + decimal(data.capsuleRadius + data.scaledHitRadius);
            case BOX -> "center=" + vector(data.boxCenter)
                    + " half=" + vector(data.boxHalfExtents)
                    + " axisY=" + vector(data.boxAxisY)
                    + " margin=" + decimal(data.scaledHitRadius);
        };
    }

    private static String vector(Vector3f value) {
        return String.format(
                Locale.ROOT,
                "(%.4f,%.4f,%.4f)",
                value.x,
                value.y,
                value.z
        );
    }

    private static String decimal(float value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    record Summary(int proxyCount, int penetratingCount) {
    }
}
