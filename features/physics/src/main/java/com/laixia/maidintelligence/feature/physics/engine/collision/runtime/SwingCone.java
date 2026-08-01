package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;


import org.joml.Vector3f;

/**
 * The spherical cap a segment's endpoint can occupy this frame.
 *
 * <p>Swing limits keep the endpoint inside a narrow cone around the rest
 * direction, so a collider behind or beside the segment is out of reach even
 * when it sits at the right radius. Culling against the cap instead of the
 * whole shell is what keeps full-mesh collision affordable.
 */
public final class SwingCone {
    /**
     * Collision projection runs after the swing limiter inside one iteration,
     * so the committed direction can overshoot the cone by a little.
     */
    private static final float MARGIN = 0.35F;
    private static final float EPSILON = 1.0E-6F;

    private final Vector3f axis = new Vector3f(0.0F, -1.0F, 0.0F);
    private float cosLimit = -1.0F;
    private float sinLimit;

    public void set(Vector3f restDirection, float maximumSwing) {
        float lengthSquared = restDirection.lengthSquared();
        if (!Float.isFinite(lengthSquared) || lengthSquared <= EPSILON) {
            cosLimit = -1.0F;
            sinLimit = 0.0F;
            return;
        }
        axis.set(restDirection).div((float) Math.sqrt(lengthSquared));
        float limit = Float.isFinite(maximumSwing)
                ? Math.min((float) Math.PI, Math.max(0.0F, maximumSwing)
                + MARGIN)
                : (float) Math.PI;
        cosLimit = (float) Math.cos(limit);
        sinLimit = (float) Math.sin(limit);
    }

    /**
     * Distance from {@code target} to the nearest point the endpoint can
     * occupy, for an endpoint sweeping {@code leverArm} around {@code pivot}.
     */
    public float distanceToSweep(
            Vector3f pivot,
            Vector3f target,
            float leverArm
    ) {
        float dx = target.x - pivot.x;
        float dy = target.y - pivot.y;
        float dz = target.z - pivot.z;
        return sweep(dx, dy, dz, dx * dx + dy * dy + dz * dz, leverArm);
    }

    /**
     * How far a collider of radius {@code reach} sits from anything the
     * endpoint can occupy; negative means it is in play. The endpoint always
     * rides the shell of radius {@code leverArm}, so one squared distance
     * settles every collider that misses that shell outright, which on a
     * full-mesh model is most of them.
     */
    public float slackToSweep(
            Vector3f pivot,
            Vector3f target,
            float leverArm,
            float reach
    ) {
        float dx = target.x - pivot.x;
        float dy = target.y - pivot.y;
        float dz = target.z - pivot.z;
        float distanceSquared = dx * dx + dy * dy + dz * dz;
        float outer = leverArm + reach;
        if (distanceSquared > outer * outer) {
            return (float) Math.sqrt(distanceSquared) - outer;
        }
        float inner = leverArm - reach;
        if (inner > 0.0F && distanceSquared < inner * inner) {
            return inner - (float) Math.sqrt(distanceSquared);
        }
        return sweep(dx, dy, dz, distanceSquared, leverArm) - reach;
    }

    /**
     * Boolean group reject that preserves the squared shell fast path. Proxy
     * culling asks for finite slack so it can cache the gap; groups only need
     * an answer and should not pay a square root for an obvious radial miss.
     */
    public boolean reachesSweep(
            Vector3f pivot,
            Vector3f target,
            float leverArm,
            float reach
    ) {
        float dx = target.x - pivot.x;
        float dy = target.y - pivot.y;
        float dz = target.z - pivot.z;
        float distanceSquared = dx * dx + dy * dy + dz * dz;
        float outer = leverArm + reach;
        if (distanceSquared > outer * outer) {
            return false;
        }
        float inner = leverArm - reach;
        if (inner > 0.0F && distanceSquared < inner * inner) {
            return false;
        }
        return sweep(dx, dy, dz, distanceSquared, leverArm) <= reach;
    }

    private float sweep(
            float dx,
            float dy,
            float dz,
            float distanceSquared,
            float leverArm
    ) {
        if (distanceSquared <= EPSILON) {
            return leverArm;
        }
        float distance = (float) Math.sqrt(distanceSquared);
        float cosAngle = (dx * axis.x + dy * axis.y + dz * axis.z) / distance;
        if (cosAngle >= cosLimit) {
            // Inside the cone: the endpoint can turn straight at the target.
            return Math.abs(distance - leverArm);
        }
        // Outside: measure to the rim, the closest reachable direction.
        float sinAngle = (float) Math.sqrt(
                Math.max(0.0F, 1.0F - cosAngle * cosAngle)
        );
        float cosDelta = cosAngle * cosLimit + sinAngle * sinLimit;
        float squared = distanceSquared + leverArm * leverArm
                - 2.0F * distance * leverArm * cosDelta;
        return squared <= 0.0F ? 0.0F : (float) Math.sqrt(squared);
    }
}
