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
     * Colliders one endpoint may be held by at once. Three faces meet at a cube
     * corner, and a hem pinched between a torso and both legs already wants
     * more than that before any layered clothing is counted. Ranking drops
     * whatever exceeds this, so too low a limit reads as the endpoint ignoring
     * one surface while it resolves another. The relaxation loop exits as soon
     * as a pass changes nothing, so an unused slot costs one reject.
     */
    private static final int ACTIVE_LIMIT = 6;
    /**
     * Colliders per cull bucket.
     *
     * <p>Balanced by measurement, and the balance does not sit where it looks
     * like it should. A tighter bucket is a better filter, so the obvious move is
     * to subdivide until the spheres hug their contents — but every bucket costs
     * an affine transform of its centre and a sweep test whether it rejects or
     * not, and those fixed costs are paid for the whole tree while only the
     * surviving buckets pay for their contents. At eight per bucket this model's
     * 3234 colliders make several hundred buckets and the constraint layer runs
     * at 8.4 times the unconstrained solver; at thirty-two it is 7.7, and beyond
     * that the curve is flat, since by then the per-bucket overhead is gone and
     * only the looser filter remains. Culling accuracy is unaffected either way:
     * a bucket that survives is still tested collider by collider.
     */
    private static final int MAX_GROUP_SIZE = 32;
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
    /**
     * Counts frames so a pairing can tell whether it was bound on the
     * previous one. A pairing culled for a frame has nothing charged against
     * its measured gaps that frame, so those gaps stop being trustworthy, and
     * this is cheaper than walking the culled ones to say so.
     */
    private int frameIndex;
    /**
     * The driven sheet's own box, in its rest frame. Supplied to the projections
     * so a contact can be padded by the sheet's reach along that one contact
     * normal instead of by a scalar in every direction.
     */
    private final Vector3f meshHalf = new Vector3f();
    private final Vector3f meshAxisX = new Vector3f(1.0F, 0.0F, 0.0F);
    private final Vector3f meshAxisY = new Vector3f(0.0F, 1.0F, 0.0F);
    private final Vector3f meshAxisZ = new Vector3f(0.0F, 0.0F, 1.0F);
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
     * Buckets the proxies by reference bone, then splits each bucket in space
     * once the shapes are known.
     *
     * <p>A bucket is cheap to reject as a whole only while it is compact, and
     * per-bone buckets are not: one hair bone can carry sixty cubes spread over
     * the entire head, giving a bounding sphere so large that every endpoint
     * passes it and then pays for all sixty. Splitting on the longest axis
     * until the buckets are small gives the frame loop tight spheres to reject
     * against, which is where most of the per-frame cost of full-mesh collision
     * goes. Culling is all this affects; whatever survives is projected exactly
     * as before.
     */
    void groupByReference() {
        grouped = new int[proxies.length];
        groupReference = new int[proxies.length];
        groupStart = new int[proxies.length + 1];
        groupCenter = new Vector3f[proxies.length];
        groupRadius = new float[proxies.length];
        groupHitRadius = new float[proxies.length];
        groupCount = 0;
        boolean[] bucketed = new boolean[proxies.length];
        int written = 0;
        for (int slot = 0; slot < proxies.length; slot++) {
            if (bucketed[slot]) {
                continue;
            }
            int reference = proxies[slot].referenceNodeIndex();
            int start = written;
            for (int scan = slot; scan < proxies.length; scan++) {
                if (proxies[scan].referenceNodeIndex() == reference) {
                    grouped[written++] = scan;
                    bucketed[scan] = true;
                }
            }
            subdivide(reference, start, written);
        }
        groupStart[groupCount] = written;
        measureGroups();
    }

    /**
     * Registers {@code grouped[from, to)} as one group, or splits it and
     * recurses. The left half is always registered first so group starts stay
     * ascending, which is what lets {@code groupStart[group + 1]} serve as the
     * end of a group.
     */
    private void subdivide(int reference, int from, int to) {
        if (to - from <= MAX_GROUP_SIZE) {
            groupReference[groupCount] = reference;
            groupStart[groupCount] = from;
            groupCount++;
            return;
        }
        sortByLongestAxis(from, to);
        int middle = from + (to - from) / 2;
        subdivide(reference, from, middle);
        subdivide(reference, middle, to);
    }

    /**
     * Orders the slots along whichever axis the group is most spread out on.
     * Insertion sort because this runs once per model load over a handful of
     * cubes, not per frame.
     */
    private void sortByLongestAxis(int from, int to) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int cursor = from; cursor < to; cursor++) {
            Vector3f center = proxies[grouped[cursor]].shape().restCenter();
            minX = Math.min(minX, center.x);
            minY = Math.min(minY, center.y);
            minZ = Math.min(minZ, center.z);
            maxX = Math.max(maxX, center.x);
            maxY = Math.max(maxY, center.y);
            maxZ = Math.max(maxZ, center.z);
        }
        float spanX = maxX - minX;
        float spanY = maxY - minY;
        float spanZ = maxZ - minZ;
        int axis = spanX >= spanY && spanX >= spanZ
                ? 0
                : (spanY >= spanZ ? 1 : 2);
        for (int cursor = from + 1; cursor < to; cursor++) {
            int slot = grouped[cursor];
            float key = axisValue(slot, axis);
            int scan = cursor - 1;
            while (scan >= from && axisValue(grouped[scan], axis) > key) {
                grouped[scan + 1] = grouped[scan];
                scan--;
            }
            grouped[scan + 1] = slot;
        }
    }

    private float axisValue(int slot, int axis) {
        Vector3f center = proxies[slot].shape().restCenter();
        return switch (axis) {
            case 0 -> center.x;
            case 1 -> center.y;
            default -> center.z;
        };
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

    /**
     * Records the driven sheet's extent. Left at zero for a node whose mesh is
     * not sheet-like, which keeps the projections point-based as before.
     */
    public void setMeshExtent(
            Vector3f half,
            Vector3f axisX,
            Vector3f axisY,
            Vector3f axisZ
    ) {
        meshHalf.set(half);
        meshAxisX.set(axisX);
        meshAxisY.set(axisY);
        meshAxisZ.set(axisZ);
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
        frameIndex++;
        advancePoseTime(dt);
        /*
         * Set for the whole of binding, because calibration measures the
         * authored pose's depth here and the projection enforces against the
         * same figure. A gap measured without the sheet's width would license
         * exactly that width back again, and a settled pose would move on its
         * first solved frame.
         */
        scratch.setMeshExtent(meshHalf, meshAxisX, meshAxisY, meshAxisZ);
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
                if (calibrationPending) {
                    proxy.bindFrame(
                            runtimePivotModel,
                            colliderScale,
                            endpointScale,
                            runtimeLeverArm,
                            frameIndex
                    );
                    if (proxy.needsCalibration()) {
                        proxy.allowInitialRestPose(restDirection, scratch);
                        stillPending = true;
                    }
                }
                if (slack <= 0.0F) {
                    insert(slot, slack);
                }
            }
        }
        /*
         * Binding is what costs: it rescales the collider, both allowances and
         * the radii derived from them. Ranking needs none of that — it orders
         * on the reach test alone — so binding waits until the set is known
         * and is paid for the handful that survived rather than for every
         * collider the sweep happened to overlap.
         *
         * Re-measuring the authored depth waits with it, for the same reason.
         * Each proxy carries its own sample time, so one that drops out and
         * returns later releases by the elapsed time rather than by a frame.
         */
        for (int index = 0; index < activeCount; index++) {
            PreparedCollisionProxy proxy = proxies[active[index]];
            proxy.bindFrame(
                    runtimePivotModel,
                    frames.maxBasisScale(proxy.referenceNodeIndex()),
                    endpointScale,
                    runtimeLeverArm,
                    frameIndex
            );
            proxy.trackAnimationPose(restDirection, poseTime, scratch);
        }
        calibrationPending = stillPending;
        scratch.clearMeshExtent();
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
        scratch.setMeshExtent(meshHalf, meshAxisX, meshAxisY, meshAxisZ);
        boolean corrected = false;
        int passes = count == 1
                ? 1
                : Math.max(1, Math.min(MAX_PASSES, maxPasses));
        boolean settled = true;
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
            /*
             * Still moving on the final pass, so relaxation never converged.
             * One collider is satisfied in a single pass and stays satisfied;
             * two that want opposite things take turns undoing each other for
             * as many passes as they are given.
             */
            settled = pass < passes - 1;
        }
        /*
         * The pose the last pass reached is handed back as it stands, even when
         * the passes never agreed. Averaging the poses they visited was tried, on
         * the reasoning that which one the count stops on is arbitrary — single
         * passes here move the direction by up to 1.03 rad, and one slot reported
         * an identical 0.606 pass after pass. It is arbitrary, but the mean is
         * worse: it satisfies no bound exactly, so cloth settles further inside
         * every collider it is caught between. Contacts rose across the models
         * and the buzz it was aimed at did not move at all.
         */
        if (!settled) {
            scratch.setUnresolved(true);
        }
        scratch.clearMeshExtent();
        return corrected;
    }

    public float clearance(
            int proxyIndex,
            Vector3f direction,
            CollisionScratch scratch
    ) {
        scratch.setMeshExtent(meshHalf, meshAxisX, meshAxisY, meshAxisZ);
        float result = proxies[proxyIndex].clearance(direction, scratch);
        scratch.clearMeshExtent();
        return result;
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

}
