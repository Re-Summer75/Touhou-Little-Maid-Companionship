package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import org.joml.Vector3f;

/**
 * Fixed-size prepared proxy list for one active node. Only the colliders the
 * endpoint can reach this frame enter the relaxation loop.
 */
public final class PreparedCollisionProxySet implements CollisionProjection {
    private static final float CLEARANCE_EPSILON = 1.0E-5F;
    /**
     * Colliders one endpoint may be held by at once. Three faces meet at a cube
     * corner, and a hem pinched between a torso and both legs already wants
     * more than that before any layered clothing is counted. Ranking drops
     * whatever exceeds this, so too low a limit reads as the endpoint ignoring
     * one surface while it resolves another. The relaxation loop exits as soon
     * as a pass changes nothing, so an unused slot costs one reject.
     */
    private static final int ACTIVE_LIMIT = 6;
    private static final int LAYER_RESERVE = 1;
    public static final PreparedCollisionProxySet EMPTY =
            new PreparedCollisionProxySet(0);

    final PreparedCollisionProxy[] proxies;
    final CollisionSpatialState spatial;
    final int rankedLimit;
    final ContactOwnerState contactOwner = new ContactOwnerState();
    boolean calibrationPending = true;
    double poseTime;
    /**
     * Counts frames so a pairing can tell whether it was bound on the
     * previous one. A pairing culled for a frame has nothing charged against
     * its measured gaps that frame, so those gaps stop being trustworthy, and
     * this is cheaper than walking the culled ones to say so.
     */
    int frameIndex;
    int nearestLayerSlot = -1;
    float nearestLayerSlack = Float.POSITIVE_INFINITY;
    final Vector3f previousCullPivot = new Vector3f();
    final Vector3f previousCullDirection = new Vector3f();
    float previousCullLeverArm;
    float previousCullSwing;
    float previousCullEndpointScale;
    float cullQueryMotion = Float.POSITIVE_INFINITY;
    float cullEndpointScaleDelta = Float.POSITIVE_INFINITY;
    boolean cullPoseValid;
    /**
     * The driven sheet's own box, in its rest frame. Supplied to the projections
     * so a contact can be padded by the sheet's reach along that one contact
     * normal instead of by a scalar in every direction.
     */
    final Vector3f meshHalf = new Vector3f();
    final Vector3f meshAxisX = new Vector3f(1.0F, 0.0F, 0.0F);
    final Vector3f meshAxisY = new Vector3f(0.0F, 1.0F, 0.0F);
    final Vector3f meshAxisZ = new Vector3f(0.0F, 0.0F, 1.0F);
    final SwingCone cone = new SwingCone();
    final Vector3f passStart = new Vector3f();
    final Vector3f pairBase = new Vector3f();
    final Vector3f pairTangent = new Vector3f();
    final PreparedCollisionSweepScratch sweepScratch =
            new PreparedCollisionSweepScratch();

    public PreparedCollisionProxySet(int proxyCount) {
        this(proxyCount, false);
    }

    PreparedCollisionProxySet(int proxyCount, boolean reserveLayer) {
        int count = Math.max(0, proxyCount);
        proxies = new PreparedCollisionProxy[count];
        for (int index = 0; index < proxies.length; index++) {
            proxies[index] = new PreparedCollisionProxy();
        }
        rankedLimit = Math.min(count, ACTIVE_LIMIT);
        int activeCapacity = Math.min(
                count,
                rankedLimit + (reserveLayer ? LAYER_RESERVE : 0)
        );
        spatial = new CollisionSpatialState(count, activeCapacity);
    }

    /**
     * Buckets proxies by reference bone and spatially subdivides wide groups
     * after all shared shapes are known.
     *
     * <p>A wide reference bone can carry cubes across a whole head, so compact
     * buckets preserve useful reach rejection without changing projection.
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
        PreparedCollisionFrameBinder.bind(
                this,
                runtimePivotModel,
                endpointScale,
                runtimeLeverArm,
                maximumSwing,
                frames,
                restDirection,
                dt,
                scratch
        );
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

    @Override
    public boolean isClear(
            Vector3f direction,
            CollisionScratch scratch
    ) {
        return isClear(direction, CLEARANCE_EPSILON, scratch);
    }

    @Override
    public boolean isClear(
            Vector3f direction,
            float penetrationTolerance,
            CollisionScratch scratch
    ) {
        int count = liveCount();
        if (count == 0) {
            return true;
        }
        float tolerance = Float.isFinite(penetrationTolerance)
                ? Math.max(CLEARANCE_EPSILON, penetrationTolerance)
                : CLEARANCE_EPSILON;
        applyMeshExtent(scratch);
        for (int index = 0; index < count; index++) {
            if (liveProxy(index).clearance(direction, scratch)
                    < -tolerance) {
                scratch.clearMeshExtent();
                return false;
            }
        }
        scratch.clearMeshExtent();
        return true;
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
        cullPoseValid = false;
        cullQueryMotion = Float.POSITIVE_INFINITY;
        cullEndpointScaleDelta = Float.POSITIVE_INFINITY;
        for (PreparedCollisionProxy proxy : proxies) {
            proxy.resetRestAllowance();
        }
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

}
