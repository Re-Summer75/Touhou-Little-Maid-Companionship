package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneRestPose;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySet;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Unified post-flatten baker for one driven bone's proxy set.
 */
public final class CollisionProxyComposer {
    private final PhysicsBoneGeometry.Analysis geometry;
    private final BodyCollisionGeometry bodyGeometry;
    private final IdentityHashMap<AnimatedGeoBone, Integer> indices;
    private final IdentityHashMap<AnimatedGeoBone, BoneRestPose> restPoses;

    public CollisionProxyComposer(
            PhysicsBoneGeometry.Analysis geometry,
            BodyCollisionGeometry bodyGeometry,
            IdentityHashMap<AnimatedGeoBone, Integer> indices,
            IdentityHashMap<AnimatedGeoBone, BoneRestPose> restPoses
    ) {
        this.geometry = geometry;
        this.bodyGeometry = bodyGeometry;
        this.indices = indices;
        this.restPoses = restPoses;
    }

    public CollisionProxySet compose(
            CollisionProxyPlan plan,
            PhysicsSolverLayout.Node node,
            BoneRestPose boneRestPose,
            Vector3f pivotModel
    ) {
        if (plan == null) {
            return CollisionProxySet.EMPTY;
        }
        CollisionBakeContext context = new CollisionBakeContext(
                node,
                geometry.node(node.bone()),
                boneRestPose,
                pivotModel,
                indices,
                restPoses
        );
        List<CollisionProxy> proxies = new ArrayList<>();
        AutomaticCollisionProxyBaker.append(
                plan,
                context,
                bodyGeometry,
                proxies
        );
        ExplicitCollisionProxyBaker.append(plan, context, proxies);
        return proxies.isEmpty()
                ? CollisionProxySet.EMPTY
                : new CollisionProxySet(
                proxies.toArray(CollisionProxy[]::new)
        );
    }
}
