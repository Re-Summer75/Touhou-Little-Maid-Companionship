package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySource;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime.CollisionProxyDebugData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * Draws colliders at their own geometry, not at the surface a segment is
 * actually held to. The endpoint radius is deliberately left off: the question
 * this overlay answers is whether a collider matches the cube it was derived
 * from, and adding the radius made every box read as a collider that had been
 * built a ring too large. Where a segment stops is that radius further out,
 * and for a cloth layer it has to be — the panel is held off by its own half
 * thickness so its surface, not its axis, lands on the sheet below.
 */
@OnlyIn(Dist.CLIENT)
final class CollisionDebugRenderer {
    private static final int CIRCLE_SEGMENTS = 24;
    private static final float CROSS_SIZE = 0.035F;
    private static final int[] SEGMENT = {40, 230, 230};
    private static final int[] PLANE = {80, 150, 255};
    private static final int[] SPHERE = {80, 255, 110};
    private static final int[] CAPSULE = {255, 190, 45};
    private static final int[] BOX = {190, 120, 255};
    /** Another driven panel, drawn apart from the rigid mesh it sits on. */
    private static final int[] LAYER = {255, 105, 180};
    private static final int[] PENETRATION = {255, 35, 35};
    private static final Vector3f X = new Vector3f(1.0F, 0.0F, 0.0F);
    private static final Vector3f Y = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final Vector3f Z = new Vector3f(0.0F, 0.0F, 1.0F);

    private CollisionDebugRenderer() { }

    static void render(PoseStack poseStack, VertexConsumer lines,
                       SpringBoneSolver solver) {
        if (solver == null) return;
        Matrix4f pose = poseStack.last().pose();
        PhysicsSolverLayout layout = solver.layout();
        Vector3f pivot = new Vector3f();
        Vector3f tip = new Vector3f();
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        // Dozens of segments share the same rigid collider; drawing it once
        // per segment turns the overlay into noise.
        Set<String> drawn = new HashSet<>();
        for (int nodeIndex = 0;
             nodeIndex < layout.activeNodeCount();
             nodeIndex++) {
            PhysicsSolverLayout.Node node = layout.node(nodeIndex);
            if (!node.driven()) continue;
            if (solver.copyRuntimePivot(nodeIndex, pivot)
                    && solver.copyRuntimeTip(nodeIndex, tip)) {
                line(lines, pose, pivot, tip, SEGMENT);
            }
            int proxyCount = solver.preparedProxyCount(nodeIndex);
            for (int proxyIndex = 0; proxyIndex < proxyCount; proxyIndex++) {
                if (solver.copyPreparedCollisionProxy(
                        nodeIndex, proxyIndex, data)
                        && (data.penetrating || drawn.add(identity(data)))) {
                    drawProxy(lines, pose, data);
                }
            }
        }
    }

    /**
     * Geometry only, matching what is drawn. The endpoint radius is left out
     * of both: it differs per segment against the same cube, so keying on it
     * would redraw one collider several times at the same size.
     */
    private static String identity(CollisionProxyDebugData data) {
        return switch (data.kind) {
            case BOX -> "B" + data.boxCenter + data.boxHalfExtents
                    + data.boxAxisY + data.boxOpenAxis;
            case PLANE -> "P" + data.planePoint + data.planeNormal;
            case SPHERE -> "S" + data.sphereCenter + round(data.sphereRadius);
            case CAPSULE -> "C" + data.capsuleStart + data.capsuleEnd
                    + round(data.capsuleRadius);
        };
    }

    private static int round(float value) {
        return Math.round(value * 512.0F);
    }

    private static void drawProxy(VertexConsumer lines, Matrix4f pose,
                                  CollisionProxyDebugData data) {
        int[] color;
        if (data.penetrating) {
            color = PENETRATION;
        } else if (data.source == CollisionProxySource.LAYER) {
            color = LAYER;
        } else {
            color = switch (data.kind) {
                case PLANE -> PLANE;
                case SPHERE -> SPHERE;
                case CAPSULE -> CAPSULE;
                case BOX -> BOX;
            };
        }
        cross(lines, pose, data.referenceOrigin, CROSS_SIZE, color);
        switch (data.kind) {
            case PLANE -> drawPlane(lines, pose, data, color);
            case SPHERE -> drawSphere(lines, pose, data, color);
            case CAPSULE -> drawCapsule(lines, pose, data, color);
            case BOX -> drawBox(lines, pose, data, color);
        }
    }

    private static void drawBox(
            VertexConsumer lines, Matrix4f pose,
            CollisionProxyDebugData data, int[] color
    ) {
        float hx = data.boxHalfExtents.x;
        float hy = data.boxHalfExtents.y;
        float hz = data.boxHalfExtents.z;
        Vector3f[] corners = new Vector3f[8];
        for (int index = 0; index < 8; index++) {
            corners[index] = new Vector3f(data.boxCenter)
                    .fma((index & 1) == 0 ? -hx : hx, data.boxAxisX)
                    .fma((index & 2) == 0 ? -hy : hy, data.boxAxisY)
                    .fma((index & 4) == 0 ? -hz : hz, data.boxAxisZ);
        }
        for (int index = 0; index < 8; index++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((index & bit) == 0) {
                    line(lines, pose, corners[index], corners[index | bit],
                            color);
                }
            }
        }
        drawOpenFace(lines, pose, data, color);
    }

    /**
     * A half-open box is drawn at its mesh extents, so the spike is the only
     * hint that it keeps going the other way. It marks the one closed face.
     */
    private static void drawOpenFace(
            VertexConsumer lines, Matrix4f pose,
            CollisionProxyDebugData data, int[] color
    ) {
        if (data.boxOpenAxis < 0) {
            return;
        }
        Vector3f axis = switch (data.boxOpenAxis) {
            case 0 -> data.boxAxisX;
            case 1 -> data.boxAxisY;
            default -> data.boxAxisZ;
        };
        float half = switch (data.boxOpenAxis) {
            case 0 -> data.boxHalfExtents.x;
            case 1 -> data.boxHalfExtents.y;
            default -> data.boxHalfExtents.z;
        };
        Vector3f face = new Vector3f(data.boxCenter).fma(half, axis);
        line(lines, pose, face, new Vector3f(face).fma(0.12F, axis), color);
    }

    private static void drawPlane(
            VertexConsumer lines, Matrix4f pose,
            CollisionProxyDebugData data, int[] color
    ) {
        Vector3f u = new Vector3f();
        Vector3f v = new Vector3f();
        basis(data.planeNormal, u, v);
        float size = Math.max(0.12F, data.scaledHitRadius);
        Vector3f a = new Vector3f(data.planePoint).fma(-size, u).fma(-size, v);
        Vector3f b = new Vector3f(data.planePoint).fma(size, u).fma(-size, v);
        Vector3f c = new Vector3f(data.planePoint).fma(size, u).fma(size, v);
        Vector3f d = new Vector3f(data.planePoint).fma(-size, u).fma(size, v);
        line(lines, pose, a, b, color);
        line(lines, pose, b, c, color);
        line(lines, pose, c, d, color);
        line(lines, pose, d, a, color);
        line(lines, pose, data.planePoint,
                new Vector3f(data.planePoint).fma(0.24F, data.planeNormal),
                color);
    }

    private static void drawSphere(
            VertexConsumer lines, Matrix4f pose,
            CollisionProxyDebugData data, int[] color
    ) {
        float radius = data.sphereRadius;
        circle(lines, pose, data.sphereCenter, X, Y, radius, color);
        circle(lines, pose, data.sphereCenter, X, Z, radius, color);
        circle(lines, pose, data.sphereCenter, Y, Z, radius, color);
    }

    private static void drawCapsule(
            VertexConsumer lines, Matrix4f pose,
            CollisionProxyDebugData data, int[] color
    ) {
        Vector3f axis = new Vector3f(data.capsuleEnd).sub(data.capsuleStart);
        Vector3f u = new Vector3f();
        Vector3f v = new Vector3f();
        basis(axis, u, v);
        float radius = data.capsuleRadius;
        line(lines, pose, data.capsuleStart, data.capsuleEnd, color);
        circle(lines, pose, data.capsuleStart, u, v, radius, color);
        circle(lines, pose, data.capsuleEnd, u, v, radius, color);
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        for (int side = 0; side < 4; side++) {
            float angle = side * (float) Math.PI * 0.5F;
            float cos = (float) Math.cos(angle) * radius;
            float sin = (float) Math.sin(angle) * radius;
            a.set(data.capsuleStart).fma(cos, u).fma(sin, v);
            b.set(data.capsuleEnd).fma(cos, u).fma(sin, v);
            line(lines, pose, a, b, color);
        }
    }

    private static void circle(
            VertexConsumer lines, Matrix4f pose, Vector3f center,
            Vector3f u, Vector3f v, float radius, int[] color
    ) {
        Vector3f previous = new Vector3f(center).fma(radius, u);
        Vector3f next = new Vector3f();
        for (int step = 1; step <= CIRCLE_SEGMENTS; step++) {
            float angle = (float) (Math.PI * 2.0D * step / CIRCLE_SEGMENTS);
            next.set(center)
                    .fma((float) Math.cos(angle) * radius, u)
                    .fma((float) Math.sin(angle) * radius, v);
            line(lines, pose, previous, next, color);
            previous.set(next);
        }
    }

    private static void basis(Vector3f normal, Vector3f u, Vector3f v) {
        Vector3f n = new Vector3f(normal);
        if (!n.isFinite() || n.lengthSquared() < 1.0E-8F) {
            n.set(Y);
        } else {
            n.normalize();
        }
        u.set(Math.abs(n.y) < 0.9F ? Y : X).cross(n).normalize();
        n.cross(u, v).normalize();
    }

    private static void cross(
            VertexConsumer lines, Matrix4f pose,
            Vector3f point, float size, int[] color
    ) {
        line(lines, pose, point.x - size, point.y, point.z,
                point.x + size, point.y, point.z, color);
        line(lines, pose, point.x, point.y - size, point.z,
                point.x, point.y + size, point.z, color);
        line(lines, pose, point.x, point.y, point.z - size,
                point.x, point.y, point.z + size, color);
    }

    private static void line(
            VertexConsumer lines, Matrix4f pose,
            Vector3f a, Vector3f b, int[] color
    ) {
        line(lines, pose, a.x, a.y, a.z, b.x, b.y, b.z, color);
    }

    private static void line(
            VertexConsumer lines, Matrix4f pose,
            float x1, float y1, float z1, float x2, float y2, float z2,
            int[] color
    ) {
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 1.0E-5F) {
            nx /= length;
            ny /= length;
            nz /= length;
        } else {
            nx = 0.0F;
            ny = 1.0F;
            nz = 0.0F;
        }
        lines.vertex(pose, x1, y1, z1).color(
                color[0], color[1], color[2], 255
        ).normal(nx, ny, nz).endVertex();
        lines.vertex(pose, x2, y2, z2).color(
                color[0], color[1], color[2], 255
        ).normal(nx, ny, nz).endVertex();
    }
}
