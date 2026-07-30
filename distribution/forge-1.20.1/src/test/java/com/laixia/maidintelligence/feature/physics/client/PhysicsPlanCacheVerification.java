package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;

import java.util.IdentityHashMap;
import java.util.Map;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.bonesByGeoBone;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.collectParents;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.forEachBone;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadWinefoxGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class PhysicsPlanCacheVerification {
    private PhysicsPlanCacheVerification() {
    }

    static void run() throws Exception {
        GeoModel geoModel = loadWinefoxGeoModel();
        AnimatedGeoModel firstModel = new AnimatedGeoModel(geoModel);
        AnimatedGeoModel secondModel = new AnimatedGeoModel(geoModel);

        PhysicsBonePlanCache.clear();
        PhysicsBoneSelectionPlan first = PhysicsBonePlanCache.getOrCompute(
                "geckolib:winefox",
                firstModel
        );
        PhysicsBoneSelectionPlan second = PhysicsBonePlanCache.getOrCompute(
                "geckolib:winefox",
                secondModel
        );
        require(first != second, "Template binding reused an instance plan");
        require(
                PhysicsBonePlanCache.fullDiscoveryCount() == 1,
                "A shared GeoModel ran full discovery more than once"
        );
        require(
                PhysicsBonePlanCache.templateBindingCount() == 1,
                "A second animated skeleton did not bind the GeoModel template"
        );

        Map<GeoBone, AnimatedGeoBone> secondBones =
                bonesByGeoBone(secondModel);
        forEachBone(firstModel, firstBone -> {
            AnimatedGeoBone secondBone = secondBones.get(
                    firstBone.geoBone()
            );
            require(secondBone != null, "Template binding lost a live bone");
            require(
                    first.decision(firstBone).equals(
                            second.decision(secondBone)
                    ),
                    "Template binding changed decision for "
                            + first.path(firstBone)
            );
            require(
                    first.path(firstBone).equals(second.path(secondBone)),
                    "Template binding changed path for "
                            + first.path(firstBone)
            );
            if (first.isDriven(firstBone)) {
                require(
                        second.kinematics(secondBone) != null,
                        "Template binding lost static kinematics for "
                                + first.path(firstBone)
                );
            }
        });

        PhysicsSolverLayout layout =
                PhysicsSolverLayout.build(secondModel, second);
        require(
                layout.activeNodeCount() < layout.fullBoneCount(),
                "Active layout did not prune any inactive branch"
        );
        require(
                layout.drivenBoneCount() > 0,
                "Active layout contains no driven bones"
        );
        verifyMinimalActiveLayout(secondModel, second, layout);

        AnimatedGeoModel otherIdModel = new AnimatedGeoModel(geoModel);
        PhysicsBonePlanCache.getOrCompute(
                "verification:winefox-other-id",
                otherIdModel
        );
        require(
                PhysicsBonePlanCache.fullDiscoveryCount() == 2,
                "GeoModel templates were not partitioned by model id"
        );
        PhysicsBonePlanCache.clear();
        PhysicsBonePlanCache.getOrCompute(
                "geckolib:winefox",
                new AnimatedGeoModel(geoModel)
        );
        require(
                PhysicsBonePlanCache.fullDiscoveryCount() == 1
                        && PhysicsBonePlanCache.templateBindingCount() == 0,
                "Cache clear retained a stale GeoModel template"
        );
        PhysicsBonePlanCache.clear();
    }

    private static void verifyMinimalActiveLayout(
            AnimatedGeoModel model,
            PhysicsBoneSelectionPlan plan,
            PhysicsSolverLayout layout
    ) {
        IdentityHashMap<AnimatedGeoBone, AnimatedGeoBone> parents =
                new IdentityHashMap<>();
        for (AnimatedGeoBone root : model.topLevelBones()) {
            collectParents(root, null, parents);
        }
        IdentityHashMap<AnimatedGeoBone, Boolean> expected =
                new IdentityHashMap<>();
        forEachBone(model, bone -> {
            if (!plan.isDriven(bone)) {
                return;
            }
            AnimatedGeoBone cursor = bone;
            while (cursor != null) {
                expected.put(cursor, Boolean.TRUE);
                cursor = parents.get(cursor);
            }
        });
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (!node.driven()) {
                continue;
            }
            addReferencePath(
                    layout,
                    node.constraint().referenceNodeIndex(),
                    parents,
                    expected
            );
            for (int proxyIndex = 0;
                 proxyIndex
                         < node.constraint().collisionProxies().proxyCount();
                 proxyIndex++) {
                addReferencePath(
                        layout,
                        node.constraint().collisionProxies()
                                .proxy(proxyIndex)
                                .referenceNodeIndex(),
                        parents,
                        expected
                );
            }
        }
        require(
                expected.size() == layout.activeNodeCount(),
                "Active layout is not the exact driven/reference ancestor union"
        );
        IdentityHashMap<AnimatedGeoBone, Integer> indices =
                new IdentityHashMap<>();
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            require(
                    expected.containsKey(node.bone()),
                    "Active layout retained an unrelated branch: "
                            + node.path()
            );
            require(
                    node.driven() == plan.isDriven(node.bone()),
                    "Active layout assigned the wrong node role: "
                            + node.path()
            );
            AnimatedGeoBone parent = parents.get(node.bone());
            int expectedParent = parent == null
                    ? -1
                    : indices.getOrDefault(parent, -2);
            require(
                    node.parentIndex() == expectedParent,
                    "Active layout has an invalid parent index: "
                            + node.path()
            );
            indices.put(node.bone(), index);
        }
    }

    private static void addReferencePath(
            PhysicsSolverLayout layout,
            int referenceIndex,
            Map<AnimatedGeoBone, AnimatedGeoBone> parents,
            IdentityHashMap<AnimatedGeoBone, Boolean> expected
    ) {
        if (referenceIndex < 0) {
            return;
        }
        require(
                referenceIndex < layout.activeNodeCount(),
                "Constraint reference index escaped the active layout"
        );
        AnimatedGeoBone cursor = layout.node(referenceIndex).bone();
        while (cursor != null) {
            expected.put(cursor, Boolean.TRUE);
            cursor = parents.get(cursor);
        }
    }
}
