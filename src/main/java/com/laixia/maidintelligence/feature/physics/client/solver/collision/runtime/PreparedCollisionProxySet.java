package com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime;

import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionScratch;
import com.laixia.maidintelligence.feature.physics.client.solver.spring.RuntimeCollisionFrames;
import org.joml.Vector3f;

/**
 * Fixed-size prepared proxy list for one active node. Only the colliders the
 * endpoint can reach this frame enter the relaxation loop.
 */
public final class PreparedCollisionProxySet {
    private static final int MAX_PASSES = 96;
    /**
     * Faces an endpoint sphere can touch at once. Three meet at a cube corner;
     * this leaves room for layered clothing on top of that.
     */
    private static final int ACTIVE_LIMIT = 4;
    public static final PreparedCollisionProxySet EMPTY =
            new PreparedCollisionProxySet(0);

    private final PreparedCollisionProxy[] proxies;
    private final int[] active;
    private final float[] activeSlack;
    /** Proxy slots ordered so one reference bone occupies one run. */
    private int[] grouped = new int[0];
    private int[] groupReference = new int[0];
    private int[] groupStart = new int[0];
    private Vector3f[] groupCenter = new Vector3f[0];
    private float[] groupRadius = new float[0];
    private float[] groupHitRadius = new float[0];
    private int groupCount;
    private int activeCount;
    private boolean bound;
    private boolean calibrationPending = true;
    private double poseTime;
    private final SwingCone cone = new SwingCone();
    private final Vector3f boundsCenter = new Vector3f();
    private final Vector3f passStart = new Vector3f();
    private final Vector3f pairBase = new Vector3f();
    private final Vector3f pairTangent = new Vector3f();

    public PreparedCollisionProxySet(int proxyCount) {
        proxies = new PreparedCollisionProxy[Math.max(0, proxyCount)];
        for (int index = 0; index < proxies.length; index++) {
            proxies[index] = new PreparedCollisionProxy();
        }
        active = new int[Math.min(proxies.length, ACTIVE_LIMIT)];
        activeSlack = new float[active.length];
    }

    /**
     * Buckets the proxies by reference bone once the shapes are known.
     */
    void groupByReference() {
        grouped = new int[proxies.length];
        groupReference = new int[proxies.length];
        groupStart = new int[proxies.length + 1];
        groupCenter = new Vector3f[proxies.length];
        groupRadius = new float[proxies.length];
        groupHitRadius = new float[proxies.length];
        groupCount = 0;
        int written = 0;
        for (int slot = 0; slot < proxies.length; slot++) {
            int reference = proxies[slot].referenceNodeIndex();
            if (contains(groupReference, groupCount, reference)) {
                continue;
            }
            groupReference[groupCount] = reference;
            groupStart[groupCount] = written;
            for (int scan = slot; scan < proxies.length; scan++) {
                if (proxies[scan].referenceNodeIndex() == reference) {
                    grouped[written++] = scan;
                }
            }
            groupCount++;
        }
        groupStart[groupCount] = written;
        measureGroups();
    }

    /**
     * One group is rigid: all of its colliders ride the same bone, so a rest
     * bounding sphere stays valid under animation once its centre is carried
     * by that bone's transform. That turns a whole bone's worth of cubes into
     * a single test.
     */
    private void measureGroups() {
        for (int group = 0; group < groupCount; group++) {
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            float hit = 0.0F;
            boolean bounded = true;
            for (int cursor = groupStart[group];
                 cursor < groupStart[group + 1];
                 cursor++) {
                PreparedCollisionProxy proxy = proxies[grouped[cursor]];
                PreparedCollisionShape shape = proxy.shape();
                float extent = shape.restCullRadius();
                hit = Math.max(hit, proxy.restHitRadius());
                if (!Float.isFinite(extent)) {
                    bounded = false;
                    break;
                }
                Vector3f center = shape.restCenter();
                minX = Math.min(minX, center.x - extent);
                minY = Math.min(minY, center.y - extent);
                minZ = Math.min(minZ, center.z - extent);
                maxX = Math.max(maxX, center.x + extent);
                maxY = Math.max(maxY, center.y + extent);
                maxZ = Math.max(maxZ, center.z + extent);
            }
            if (!bounded) {
                groupCenter[group] = null;
                continue;
            }
            float sizeX = maxX - minX;
            float sizeY = maxY - minY;
            float sizeZ = maxZ - minZ;
            groupCenter[group] = new Vector3f(
                    minX + sizeX * 0.5F,
                    minY + sizeY * 0.5F,
                    minZ + sizeZ * 0.5F
            );
            groupRadius[group] = 0.5F * (float) Math.sqrt(
                    (double) sizeX * sizeX
                            + (double) sizeY * sizeY
                            + (double) sizeZ * sizeZ
            );
            groupHitRadius[group] = hit;
        }
    }

    public int proxyCount() {
        return proxies.length;
    }

    public PreparedCollisionProxy proxy(int index) {
        return proxies[index];
    }

    /**
     * Binds every collider to this frame's pose and keeps the reachable ones.
     */
    void bindFrame(
            Vector3f runtimePivotModel,
            float endpointScale,
            float runtimeLeverArm,
            float maximumSwing,
            RuntimeCollisionFrames frames,
            Vector3f restDirection,
            float dt,
            CollisionScratch scratch
    ) {
        activeCount = 0;
        bound = true;
        advancePoseTime(dt);
        boolean stillPending = false;
        cone.set(restDirection, maximumSwing);
        for (int group = 0; group < groupCount; group++) {
            int reference = groupReference[group];
            /*
             * Rest overlap has to be measured on the first frame even for a
             * collider that is out of reach right now, otherwise a later
             * approach would calibrate against an already deflected pose.
             */
            float colliderScale = frames.maxBasisScale(reference);
            if (!calibrationPending
                    && !groupReachable(
                    group,
                    frames,
                    colliderScale,
                    runtimePivotModel,
                    runtimeLeverArm,
                    endpointScale
            )) {
                continue;
            }
            int end = groupStart[group + 1];
            for (int cursor = groupStart[group]; cursor < end; cursor++) {
                int slot = grouped[cursor];
                PreparedCollisionProxy proxy = proxies[slot];
                float slack = proxy.slack(
                        runtimePivotModel,
                        runtimeLeverArm,
                        endpointScale,
                        cone
                );
                if (slack > 0.0F && !calibrationPending) {
                    continue;
                }
                proxy.bindFrame(
                        runtimePivotModel,
                        colliderScale,
                        endpointScale,
                        runtimeLeverArm
                );
                if (calibrationPending && proxy.needsCalibration()) {
                    proxy.allowInitialRestPose(restDirection, scratch);
                    stillPending = true;
                }
                if (slack <= 0.0F) {
                    insert(slot, slack);
                }
            }
        }
        /*
         * Only the proxies that survived ranking can constrain this frame, so
         * only they need their authored depth re-measured. Each carries its
         * own sample time, so one that drops out and returns later releases by
         * the elapsed time rather than by a single frame.
         */
        for (int index = 0; index < activeCount; index++) {
            proxies[active[index]].trackAnimationPose(
                    restDirection,
                    poseTime,
                    scratch
            );
        }
        calibrationPending = stillPending;
    }

    public boolean project(
            Vector3f direction,
            CollisionScratch scratch,
            int maxPasses
    ) {
        int count = liveCount();
        if (count == 0) {
            return false;
        }
        boolean corrected = false;
        int passes = count == 1
                ? 1
                : Math.max(1, Math.min(MAX_PASSES, maxPasses));
        for (int pass = 0; pass < passes; pass++) {
            passStart.set(direction);
            boolean passCorrected = false;
            for (int slot = 0; slot < count; slot++) {
                passCorrected |= liveProxy(slot).project(direction, scratch);
            }
            if (passCorrected && count == 2) {
                passCorrected |= PreparedPlanePairProjector.project(
                        liveProxy(0),
                        liveProxy(1),
                        direction,
                        scratch,
                        pairBase,
                        pairTangent
                );
            }
            corrected |= passCorrected;
            if (!passCorrected
                    || direction.distanceSquared(passStart) <= 1.0E-12F) {
                break;
            }
        }
        return corrected;
    }

    public float clearance(
            int proxyIndex,
            Vector3f direction,
            CollisionScratch scratch
    ) {
        return proxies[proxyIndex].clearance(direction, scratch);
    }

    void resetFrame() {
        activeCount = 0;
        bound = false;
        calibrationPending = true;
        poseTime = 0.0D;
        for (PreparedCollisionProxy proxy : proxies) {
            proxy.resetRestAllowance();
        }
    }

    /** Whether any collider on this bone is within the endpoint's sweep. */
    private boolean groupReachable(
            int group,
            RuntimeCollisionFrames frames,
            float colliderScale,
            Vector3f runtimePivotModel,
            float runtimeLeverArm,
            float endpointScale
    ) {
        Vector3f restCenter = groupCenter[group];
        if (restCenter == null) {
            return true;
        }
        frames.affineDelta(groupReference[group])
                .transformPosition(restCenter, boundsCenter);
        float margin = groupRadius[group] * Math.max(0.0F, colliderScale)
                + groupHitRadius[group] * Math.max(0.0F, endpointScale);
        return cone.slackToSweep(
                runtimePivotModel,
                boundsCenter,
                runtimeLeverArm,
                margin
        ) <= 0.0F;
    }

    /**
     * Keeps the nearest colliders in slack order. An endpoint sphere can only
     * rest against a handful of faces at once, and the relaxation loop revisits
     * its whole set on every pass, so enforcing the closest ones each frame
     * costs a fraction of enforcing all of them and converges to the same
     * pose. Nothing is dropped permanently: the ranking is redone every frame
     * from the live pose.
     */
    private void insert(int slot, float slack) {
        int limit = active.length;
        int position = activeCount < limit ? activeCount++ : limit;
        if (position == limit) {
            if (slack >= activeSlack[limit - 1]) {
                return;
            }
            position = limit - 1;
        }
        while (position > 0 && activeSlack[position - 1] > slack) {
            active[position] = active[position - 1];
            activeSlack[position] = activeSlack[position - 1];
            position--;
        }
        active[position] = slot;
        activeSlack[position] = slack;
    }

    /**
     * Colliders in play. A set the solver never bound this frame, such as one
     * configured directly by a test, keeps all of its proxies live.
     */
    int liveCount() {
        return bound ? activeCount : proxies.length;
    }

    PreparedCollisionProxy liveProxy(int index) {
        return bound ? proxies[active[index]] : proxies[index];
    }

    private void advancePoseTime(float dt) {
        float step = Float.isFinite(dt)
                ? Math.max(0.0F, Math.min(dt, 0.1F))
                : 0.0F;
        poseTime += step;
    }

    private static boolean contains(int[] values, int count, int value) {
        for (int index = 0; index < count; index++) {
            if (values[index] == value) {
                return true;
            }
        }
        return false;
    }
}
