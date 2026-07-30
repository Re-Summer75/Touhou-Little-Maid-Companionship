package com.laixia.maidintelligence.feature.interaction.domain;

import com.laixia.maidintelligence.shared.geometry.Vec3d;

import java.util.List;
import java.util.Optional;

/**
 * Selected render-space face plane with stable local coordinates.
 */
public final class MaidFacePlane {
    private static final double MIN_AXIS_LENGTH = 1.0E-5D;
    private static final double MIN_RAY_DENOMINATOR = 1.0E-5D;

    private final List<Vec3d> vertices;
    private final Vec3d origin;
    private final Vec3d bottomCenter;
    private final Vec3d horizontal;
    private final Vec3d up;
    private final Vec3d rightSpan;
    private final Vec3d upSpan;
    private final Vec3d normal;
    private final double width;
    private final double height;
    private final double gramA;
    private final double gramB;
    private final double gramC;
    private final double gramDeterminant;

    private MaidFacePlane(
            List<Vec3d> vertices,
            Vec3d origin,
            Vec3d bottomCenter,
            Vec3d horizontal,
            Vec3d up,
            Vec3d rightSpan,
            Vec3d upSpan,
            Vec3d normal,
            double width,
            double height,
            double gramA,
            double gramB,
            double gramC,
            double gramDeterminant
    ) {
        this.vertices = vertices;
        this.origin = origin;
        this.bottomCenter = bottomCenter;
        this.horizontal = horizontal;
        this.up = up;
        this.rightSpan = rightSpan;
        this.upSpan = upSpan;
        this.normal = normal;
        this.width = width;
        this.height = height;
        this.gramA = gramA;
        this.gramB = gramB;
        this.gramC = gramC;
        this.gramDeterminant = gramDeterminant;
    }

    public static Optional<MaidFacePlane> fromVertices(
            List<Vec3d> vertices,
            FaceGeometry.Frame frame
    ) {
        return FaceGeometry.orderQuad(vertices, frame)
                .flatMap(MaidFacePlane::fromOrderedQuad);
    }

    public static Optional<MaidFacePlane> fromOrderedQuad(
            FaceGeometry.OrderedQuad quad
    ) {
        List<Vec3d> vertices = quad.vertices();
        Vec3d origin = vertices.get(0);
        Vec3d rightSpan = vertices.get(1).subtract(origin);
        Vec3d upSpan = vertices.get(3).subtract(origin);
        double width = rightSpan.length();
        double height = upSpan.length();
        if (width <= MIN_AXIS_LENGTH
                || height <= MIN_AXIS_LENGTH) {
            return Optional.empty();
        }

        double gramA = rightSpan.dot(rightSpan);
        double gramB = rightSpan.dot(upSpan);
        double gramC = upSpan.dot(upSpan);
        double gramDeterminant = gramA * gramC
                - gramB * gramB;
        if (gramDeterminant
                <= MIN_AXIS_LENGTH * MIN_AXIS_LENGTH) {
            return Optional.empty();
        }

        return Optional.of(new MaidFacePlane(
                vertices,
                origin,
                origin.add(rightSpan.scale(0.5D)),
                rightSpan.scale(1.0D / width),
                upSpan.scale(1.0D / height),
                rightSpan,
                upSpan,
                quad.normal(),
                width,
                height,
                gramA,
                gramB,
                gramC,
                gramDeterminant
        ));
    }

    public List<Vec3d> vertices() {
        return vertices;
    }

    public Vec3d bottomCenter() {
        return bottomCenter;
    }

    public Vec3d horizontal() {
        return horizontal;
    }

    public Vec3d up() {
        return up;
    }

    public Vec3d normal() {
        return normal;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public Vec3d point(float u, float v) {
        return origin
                .add(rightSpan.scale(u))
                .add(upSpan.scale(v));
    }

    public Optional<TargetHit> intersectTarget(
            Vec3d rayStart,
            Vec3d rayDirection,
            double maxDistance
    ) {
        Vec3d direction = rayDirection.normalize();
        double denominator = direction.dot(normal);
        if (Math.abs(denominator) <= MIN_RAY_DENOMINATOR) {
            return Optional.empty();
        }

        double distance = origin
                .subtract(rayStart)
                .dot(normal) / denominator;
        if (distance < 0.0D || distance > maxDistance) {
            return Optional.empty();
        }

        Vec3d localHit = rayStart
                .add(direction.scale(distance))
                .subtract(origin);
        double localRight = localHit.dot(rightSpan);
        double localUp = localHit.dot(upSpan);
        float u = (float) (
                (localRight * gramC - localUp * gramB)
                        / gramDeterminant
        );
        float v = (float) (
                (localUp * gramA - localRight * gramB)
                        / gramDeterminant
        );
        if (!MouthTargetRegion.contains(u, v)) {
            return Optional.empty();
        }
        return Optional.of(new TargetHit(u, v));
    }

    public record TargetHit(float u, float v) {
    }
}
