package com.laixia.maidintelligence.feature.physics.engine.collision.bake;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.layout.BoneRestPose;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySet;
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
    private final IdentityHashMap<BoneModelSnapshot.Bone, Integer> indices;
    private final IdentityHashMap<BoneModelSnapshot.Bone, BoneRestPose> restPoses;

    public CollisionProxyComposer(
            PhysicsBoneGeometry.Analysis geometry,
            BodyCollisionGeometry bodyGeometry,
            IdentityHashMap<BoneModelSnapshot.Bone, Integer> indices,
            IdentityHashMap<BoneModelSnapshot.Bone, BoneRestPose> restPoses
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
