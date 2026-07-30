package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Temporary diagnostic: how many mesh boxes exist and how they duplicate. */
public final class MeshColliderAudit {
    private MeshColliderAudit() {
    }

    public static void main(String[] args) throws Exception {
        List<Path> models;
        try (Stream<Path> stream = Files.list(
                BonePhysicsVerificationSupport.MODEL_DIRECTORY
        )) {
            models = stream.filter(path ->
                    path.getFileName().toString().endsWith(".json")).toList();
        }
        for (Path path : models) {
            audit(path);
        }
    }

    private static void audit(Path path) throws Exception {
        String name = path.getFileName().toString();
        BoneModelSnapshot model = BonePhysicsVerificationSupport.coreModel(
                BonePhysicsVerificationSupport.loadGeoModel(path)
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                BonePhysicsVerificationSupport.discover(
                        "audit:" + name, model, PhysicsMetadata.EMPTY
                )
        );
        PhysicsBoneGeometry.Analysis geometry =
                BonePhysicsVerificationSupport.analyze(model);
        int totalCubes = 0;
        for (PhysicsBoneGeometry.Node node : geometry.nodes()) {
            totalCubes += node.cubeBoxes().size();
        }
        int attached = 0;
        int maxPerSegment = 0;
        int segments = 0;
        Map<String, Integer> unique = new HashMap<>();
        Map<String, Integer> perReference = new TreeMap<>();
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (!node.driven()) {
                continue;
            }
            segments++;
            CollisionProxySet proxies = node.constraint().collisionProxies();
            int boxes = 0;
            for (int slot = 0; slot < proxies.proxyCount(); slot++) {
                CollisionProxy proxy = proxies.proxy(slot);
                if (proxy.kind() != CollisionProxyKind.BOX) {
                    continue;
                }
                boxes++;
                String reference = layout
                        .node(proxy.referenceNodeIndex()).bone().getName();
                perReference.merge(reference, 1, Integer::sum);
                unique.merge(
                        reference + "#" + MeshCollisionSupport
                                .restHalfExtents(proxy),
                        1,
                        Integer::sum
                );
            }
            attached += boxes;
            maxPerSegment = Math.max(maxPerSegment, boxes);
        }
        System.out.printf(
                Locale.ROOT,
                "%-28s cubes=%4d segments=%3d attached=%5d unique=%4d "
                        + "avg=%5.1f max=%3d%n",
                name,
                totalCubes,
                segments,
                attached,
                unique.size(),
                segments == 0 ? 0.0 : (double) attached / segments,
                maxPerSegment
        );
        System.out.println("    references: " + perReference);
    }
}
