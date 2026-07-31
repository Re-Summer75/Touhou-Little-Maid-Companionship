package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import org.joml.Vector3f;

/**
 * Fixed-size prepared proxy list for one active node. Only the colliders the
 * endpoint can reach this frame enter the relaxation loop.
 */
public final class PreparedCollisionProxySet implements CollisionProjection {
    /**
     * Colliders one endpoint may be held by at once. Three faces meet at a cube
     * corner, and a hem pinched between a torso and both legs already wants
     * more than that before any layered clothing is counted. Ranking drops
     * whatever exceeds this, so too low a limit reads as the endpoint ignoring
     * one surface while it resolves another. The relaxation loop exits as soon
     * as a pass changes nothing, so an unused slot costs one reject.
     */
    private static final int ACTIVE_LIMIT = 6;
    public static final PreparedCollisionProxySet EMPTY =
            new PreparedCollisionProxySet(0);

    final PreparedCollisionProxy[] proxies;
    final CollisionSpatialState spatial;
    final ContactOwnerState contactOwner = new ContactOwnerState();
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
    final Vector3f meshHalf = new Vector3f();
    final Vector3f meshAxisX = new Vector3f(1.0F, 0.0F, 0.0F);
    final Vector3f meshAxisY = new Vector3f(0.0F, 1.0F, 0.0F);
    final Vector3f meshAxisZ = new Vector3f(0.0F, 0.0F, 1.0F);
    private final SwingCone cone = new SwingCone();
    final Vector3f passStart = new Vector3f();
    final Vector3f pairBase = new Vector3f();
    final Vector3f pairTangent = new Vector3f();

    public PreparedCollisionProxySet(int proxyCount) {
        proxies = new PreparedCollisionProxy[Math.max(0, proxyCount)];
        for (int index = 0; index < proxies.length; index++) {
            proxies[index] = new PreparedCollisionProxy();
        }
        spatial = new CollisionSpatialState(proxies.length, ACTIVE_LIMIT);
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
        PreparedCollisionSpatialGroups.build(proxies, spatial);
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
        spatial.beginBinding();
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
        for (int group = 0; group < spatial.groupCount; group++) {
            int reference = spatial.groupReference[group];
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
            int end = spatial.groupStart[group + 1];
            for (int cursor = spatial.groupStart[group];
                 cursor < end;
                 cursor++) {
                int slot = spatial.grouped[cursor];
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
        for (int index = 0; index < spatial.activeCount; index++) {
            PreparedCollisionProxy proxy =
                    proxies[spatial.active[index]];
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

    @Override
    public boolean project(
            Vector3f direction,
            CollisionScratch scratch,
            int maxPasses
    ) {
        return PreparedCollisionProjectionLoop.project(
                this, direction, scratch, maxPasses
        );
    }

    /**
     * Starts one alternating swing/collision projection series.
     *
     * <p>The series may call {@link #project(Vector3f, CollisionScratch, int)}
     * several times. A later stable collision pass must not erase the responder
     * recorded by an earlier pass, because recurring-contact suppression runs
     * only after the whole series has settled.
     */
    @Override
    public void beginProjectionSeries() {
        contactOwner.beginSeries();
    }

    /**
     * Suppresses colliders the latest responder pushed the segment into.
     *
     * <p>The responder itself remains authoritative. Silencing it instead merely
     * hands the segment to the opposing collider, which pushes it back and starts
     * the same cycle with the roles reversed. Competing overlaps are allowed to
     * remain visually steady until the segment fully exits them.
     */
    @Override
    public boolean resolveRecurringContact(
            boolean recurringContact,
            Vector3f projectedDirection,
            CollisionScratch scratch
    ) {
        return PreparedCollisionProjectionLoop.resolveRecurringContact(
                this, recurringContact, projectedDirection, scratch
        );
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

    void applyMeshExtent(CollisionScratch scratch) {
        scratch.setMeshExtent(meshHalf, meshAxisX, meshAxisY, meshAxisZ);
    }

    public ContactOwnerState contactOwnerState() {
        return contactOwner;
    }

    void resetFrame() {
        spatial.reset();
        contactOwner.reset();
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
        Vector3f restCenter = spatial.groupCenter[group];
        if (restCenter == null) {
            return true;
        }
        frames.affineDelta(spatial.groupReference[group])
                .transformPosition(
                        restCenter,
                        spatial.transformedGroupCenter
                );
        float margin = spatial.groupRadius[group]
                * Math.max(0.0F, colliderScale)
                + spatial.groupHitRadius[group]
                * Math.max(0.0F, endpointScale);
        return cone.slackToSweep(
                runtimePivotModel,
                spatial.transformedGroupCenter,
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
        int limit = spatial.active.length;
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
            spatial.activeSlack[position] =
                    spatial.activeSlack[position - 1];
            position--;
        }
        spatial.active[position] = slot;
        spatial.activeSlack[position] = slack;
    }

    /**
     * Colliders in play. A set the solver never bound this frame, such as one
     * configured directly by a test, keeps all of its proxies live.
     */
    int liveCount() {
        return spatial.liveCount(proxies.length);
    }

    PreparedCollisionProxy liveProxy(int index) {
        return proxies[spatial.proxyIndex(index)];
    }

    private void advancePoseTime(float dt) {
        float step = Float.isFinite(dt)
                ? Math.max(0.0F, Math.min(dt, 0.1F))
                : 0.0F;
        poseTime += step;
    }

}
