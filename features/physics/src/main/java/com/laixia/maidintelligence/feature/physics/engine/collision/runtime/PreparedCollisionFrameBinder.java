package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySource;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import org.joml.Vector3f;

/**
 * Binds one prepared proxy set to an animation frame.
 *
 * <p>Traversal, ranking and delayed expensive binding form one hot-path
 * lifecycle, so they stay together here while projection remains on the set.
 */
final class PreparedCollisionFrameBinder {
    private static final float COHERENCE_BONUS = 1.0F / 256.0F;

    private PreparedCollisionFrameBinder() {
    }

    static void bind(
            PreparedCollisionProxySet set,
            Vector3f runtimePivotModel,
            float endpointScale,
            float runtimeLeverArm,
            float maximumSwing,
            RuntimeCollisionFrames frames,
            Vector3f restDirection,
            float dt,
            CollisionScratch scratch
    ) {
        set.spatial.beginBinding();
        set.nearestLayerSlot = -1;
        set.nearestLayerSlack = Float.POSITIVE_INFINITY;
        set.frameIndex++;
        advancePoseTime(set, dt);
        scratch.setMeshExtent(
                set.meshHalf, set.meshAxisX, set.meshAxisY, set.meshAxisZ
        );
        set.cone.set(restDirection, maximumSwing);
        prepareCullMotion(
                set,
                runtimePivotModel,
                restDirection,
                endpointScale,
                runtimeLeverArm,
                maximumSwing
        );

        boolean stillPending = set.calibrationPending
                && calibrateAll(
                set,
                runtimePivotModel,
                endpointScale,
                runtimeLeverArm,
                frames,
                restDirection,
                scratch
        );
        if (!set.calibrationPending) {
            traverseGroups(
                    set,
                    runtimePivotModel,
                    endpointScale,
                    runtimeLeverArm,
                    frames,
                    restDirection,
                    scratch
            );
        }
        appendReservedLayer(set);
        bindSelected(
                set,
                runtimePivotModel,
                endpointScale,
                runtimeLeverArm,
                frames,
                restDirection,
                scratch
        );
        set.calibrationPending = stillPending;
        scratch.clearMeshExtent();
    }

    /**
     * Initial overlap must be measured for every pair, even when its BVH leaf
     * is currently unreachable. Otherwise a later approach could calibrate
     * against an already deflected pose.
     */
    private static boolean calibrateAll(
            PreparedCollisionProxySet set,
            Vector3f runtimePivotModel,
            float endpointScale,
            float runtimeLeverArm,
            RuntimeCollisionFrames frames,
            Vector3f restDirection,
            CollisionScratch scratch
    ) {
        boolean stillPending = false;
        for (int slot = 0; slot < set.proxies.length; slot++) {
            PreparedCollisionProxy proxy = set.proxies[slot];
            if (!visible(proxy, frames)) {
                continue;
            }
            float colliderScale =
                    frames.maxBasisScale(proxy.referenceNodeIndex());
            float slack = proxy.slack(
                    runtimePivotModel,
                    runtimeLeverArm,
                    endpointScale,
                    set.cone
            );
            proxy.bindFrame(
                    runtimePivotModel,
                    colliderScale,
                    endpointScale,
                    runtimeLeverArm,
                    set.frameIndex
            );
            if (proxy.needsCalibration()) {
                proxy.allowInitialRestPose(restDirection, scratch);
                stillPending = true;
            }
            if (slack <= 0.0F) {
                rememberLayer(set, slot, slack);
                insert(set, slot, slack);
            }
        }
        return stillPending;
    }

    /**
     * Rejects each compact rigid group before testing its individual proxies.
     */
    private static void traverseGroups(
            PreparedCollisionProxySet set,
            Vector3f runtimePivotModel,
            float endpointScale,
            float runtimeLeverArm,
            RuntimeCollisionFrames frames,
            Vector3f restDirection,
            CollisionScratch scratch
    ) {
        CollisionSpatialState spatial = set.spatial;
        for (int group = 0; group < spatial.groupCount; group++) {
            int reference = spatial.groupReference[group];
            float colliderScale = frames.maxBasisScale(reference);
            boolean reachable = groupReachable(
                    set,
                    group,
                    frames,
                    colliderScale,
                    runtimePivotModel,
                    runtimeLeverArm,
                    endpointScale
            );
            for (int cursor = spatial.groupStart[group];
                 cursor < spatial.groupStart[group + 1];
                 cursor++) {
                int slot = spatial.grouped[cursor];
                PreparedCollisionProxy proxy = set.proxies[slot];
                if (!visible(proxy, frames)) {
                    continue;
                }
                boolean previous = spatial.wasActive(slot);
                if (!reachable && !previous) {
                    continue;
                }
                if (proxy.needsCalibration()) {
                    proxy.bindFrame(
                            runtimePivotModel,
                            colliderScale,
                            endpointScale,
                            runtimeLeverArm,
                            set.frameIndex
                    );
                    proxy.allowInitialRestPose(restDirection, scratch);
                }
                float slack = proxy.coherentSlack(
                        runtimePivotModel,
                        runtimeLeverArm,
                        endpointScale,
                        set.cone,
                        set.frameIndex,
                        set.cullQueryMotion,
                        set.cullEndpointScaleDelta,
                        previous
                );
                if (slack > 0.0F && previous) {
                    slack = PreparedCollisionProxyMotion.sweptSlack(
                            set.proxies[slot],
                            slack,
                            runtimePivotModel,
                            runtimeLeverArm,
                            endpointScale
                    );
                }
                if (slack <= 0.0F) {
                    float ranked = slack
                            - (previous ? COHERENCE_BONUS : 0.0F);
                    rememberLayer(set, slot, ranked);
                    insert(set, slot, ranked);
                }
            }
        }
    }

    private static boolean groupReachable(
            PreparedCollisionProxySet set,
            int group,
            RuntimeCollisionFrames frames,
            float colliderScale,
            Vector3f runtimePivotModel,
            float runtimeLeverArm,
            float endpointScale
    ) {
        CollisionSpatialState spatial = set.spatial;
        Vector3f restCenter = spatial.groupCenter[group];
        if (restCenter == null) {
            return true;
        }
        frames.affineDelta(spatial.groupReference[group]).transformPosition(
                restCenter,
                spatial.transformedGroupCenter
        );
        float margin = spatial.groupRadius[group]
                * Math.max(0.0F, colliderScale)
                + spatial.groupHitRadius[group]
                * Math.max(0.0F, endpointScale);
        return set.cone.reachesSweep(
                runtimePivotModel,
                spatial.transformedGroupCenter,
                runtimeLeverArm,
                margin
        );
    }

    private static void prepareCullMotion(
            PreparedCollisionProxySet set,
            Vector3f pivot,
            Vector3f restDirection,
            float endpointScale,
            float leverArm,
            float maximumSwing
    ) {
        float directionLengthSquared = restDirection.lengthSquared();
        boolean finite = Float.isFinite(directionLengthSquared)
                && directionLengthSquared > 1.0E-6F
                && Float.isFinite(pivot.x)
                && Float.isFinite(pivot.y)
                && Float.isFinite(pivot.z)
                && Float.isFinite(endpointScale)
                && Float.isFinite(leverArm)
                && Float.isFinite(maximumSwing);
        if (!set.cullPoseValid || !finite) {
            set.cullQueryMotion = Float.POSITIVE_INFINITY;
            set.cullEndpointScaleDelta = Float.POSITIVE_INFINITY;
        } else {
            float inverseLength =
                    1.0F / (float) Math.sqrt(directionLengthSquared);
            float directionDx = restDirection.x * inverseLength
                    - set.previousCullDirection.x;
            float directionDy = restDirection.y * inverseLength
                    - set.previousCullDirection.y;
            float directionDz = restDirection.z * inverseLength
                    - set.previousCullDirection.z;
            float maxLever = Math.max(
                    Math.abs(leverArm), Math.abs(set.previousCullLeverArm)
            );
            set.cullQueryMotion = pivot.distance(set.previousCullPivot)
                    + Math.abs(leverArm - set.previousCullLeverArm)
                    + maxLever * (float) Math.sqrt(
                    directionDx * directionDx
                            + directionDy * directionDy
                            + directionDz * directionDz
            )
                    + maxLever * Math.abs(
                    maximumSwing - set.previousCullSwing
            );
            set.cullEndpointScaleDelta = Math.abs(
                    endpointScale - set.previousCullEndpointScale
            );
        }
        set.previousCullPivot.set(pivot);
        if (finite) {
            set.previousCullDirection.set(restDirection).normalize();
        } else {
            set.previousCullDirection.zero();
        }
        set.previousCullLeverArm = leverArm;
        set.previousCullSwing = maximumSwing;
        set.previousCullEndpointScale = endpointScale;
        set.cullPoseValid = finite;
    }

    /**
     * Expensive scale, allowance and authored-depth work is deferred until
     * Top-K is known. Each proxy carries its own sample time, so a culled pair
     * releases allowance by elapsed time when it returns.
     */
    private static void bindSelected(
            PreparedCollisionProxySet set,
            Vector3f runtimePivotModel,
            float endpointScale,
            float runtimeLeverArm,
            RuntimeCollisionFrames frames,
            Vector3f restDirection,
            CollisionScratch scratch
    ) {
        for (int index = 0; index < set.spatial.activeCount; index++) {
            PreparedCollisionProxy proxy =
                    set.proxies[set.spatial.active[index]];
            proxy.bindFrame(
                    runtimePivotModel,
                    frames.maxBasisScale(proxy.referenceNodeIndex()),
                    endpointScale,
                    runtimeLeverArm,
                    set.frameIndex
            );
            proxy.trackAnimationPose(restDirection, set.poseTime, scratch);
        }
    }

    private static void insert(
            PreparedCollisionProxySet set,
            int slot,
            float slack
    ) {
        CollisionSpatialState spatial = set.spatial;
        int limit = set.rankedLimit;
        int position = spatial.activeCount < limit
                ? spatial.activeCount++
                : limit;
        if (position == limit) {
            if (slack >= spatial.activeSlack[limit - 1]) {
                return;
            }
            position = limit - 1;
        }
        while (position > 0
                && spatial.activeSlack[position - 1] > slack) {
            spatial.active[position] = spatial.active[position - 1];
            spatial.activeSlack[position] = spatial.activeSlack[position - 1];
            position--;
        }
        spatial.active[position] = slot;
        spatial.activeSlack[position] = slack;
    }

    private static void rememberLayer(
            PreparedCollisionProxySet set,
            int slot,
            float slack
    ) {
        if (set.proxies[slot].source != CollisionProxySource.LAYER
                || slack > set.nearestLayerSlack
                || (slack == set.nearestLayerSlack
                && slot >= set.nearestLayerSlot)) {
            return;
        }
        set.nearestLayerSlot = slot;
        set.nearestLayerSlack = slack;
    }

    private static void appendReservedLayer(PreparedCollisionProxySet set) {
        if (set.nearestLayerSlot < 0) {
            return;
        }
        CollisionSpatialState spatial = set.spatial;
        for (int index = 0; index < spatial.activeCount; index++) {
            if (spatial.active[index] == set.nearestLayerSlot) {
                return;
            }
        }
        if (spatial.activeCount >= spatial.active.length) {
            return;
        }
        int position = spatial.activeCount++;
        while (position > 0
                && spatial.activeSlack[position - 1]
                > set.nearestLayerSlack) {
            spatial.active[position] = spatial.active[position - 1];
            spatial.activeSlack[position] = spatial.activeSlack[position - 1];
            position--;
        }
        spatial.active[position] = set.nearestLayerSlot;
        spatial.activeSlack[position] = set.nearestLayerSlack;
    }

    private static void advancePoseTime(
            PreparedCollisionProxySet set,
            float dt
    ) {
        float step = Float.isFinite(dt)
                ? Math.max(0.0F, Math.min(dt, 0.1F))
                : 0.0F;
        set.poseTime += step;
    }

    private static boolean visible(
            PreparedCollisionProxy proxy,
            RuntimeCollisionFrames frames
    ) {
        return proxy.source == CollisionProxySource.EXPLICIT
                || frames.geometryVisible(proxy.referenceNodeIndex());
    }
}
