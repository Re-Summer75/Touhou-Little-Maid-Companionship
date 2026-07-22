package com.laixia.maidintelligence.feature.interaction.client;

import com.laixia.maidintelligence.feature.interaction.domain.MouthTargetRegion;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

final class MaidFacePlane {
    private static final double MIN_AXIS_LENGTH = 1.0E-5D;
    private static final double MIN_RAY_DENOMINATOR = 1.0E-5D;

    private final List<Vec3> vertices;
    private final Vec3 origin;
    private final Vec3 bottomCenter;
    private final Vec3 horizontal;
    private final Vec3 up;
    private final Vec3 rightSpan;
    private final Vec3 upSpan;
    private final Vec3 normal;
    private final double width;
    private final double height;
    private final double gramA;
    private final double gramB;
    private final double gramC;
    private final double gramDeterminant;

    private MaidFacePlane(
            List<Vec3> vertices,
            Vec3 origin,
            Vec3 bottomCenter,
            Vec3 horizontal,
            Vec3 up,
            Vec3 rightSpan,
            Vec3 upSpan,
            Vec3 normal,
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

    static Optional<MaidFacePlane> fromVertices(
            List<Vec3> vertices,
            FaceGeometry.Frame frame
    ) {
        return FaceGeometry.orderQuad(vertices, frame)
                .flatMap(MaidFacePlane::fromOrderedQuad);
    }

    static Optional<MaidFacePlane> fromOrderedQuad(FaceGeometry.OrderedQuad quad) {
        List<Vec3> vertices = quad.vertices();
        Vec3 origin = vertices.get(0);
        Vec3 rightSpan = vertices.get(1).subtract(origin);
        Vec3 upSpan = vertices.get(3).subtract(origin);
        double width = rightSpan.length();
        double height = upSpan.length();
        if (width <= MIN_AXIS_LENGTH || height <= MIN_AXIS_LENGTH) {
            return Optional.empty();
        }

        double gramA = rightSpan.dot(rightSpan);
        double gramB = rightSpan.dot(upSpan);
        double gramC = upSpan.dot(upSpan);
        double gramDeterminant = gramA * gramC - gramB * gramB;
        if (gramDeterminant <= MIN_AXIS_LENGTH * MIN_AXIS_LENGTH) {
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

    List<Vec3> vertices() {
        return vertices;
    }

    Vec3 bottomCenter() {
        return bottomCenter;
    }

    Vec3 horizontal() {
        return horizontal;
    }

    Vec3 up() {
        return up;
    }

    Vec3 normal() {
        return normal;
    }

    double width() {
        return width;
    }

    double height() {
        return height;
    }

    Vec3 point(float u, float v) {
        return origin
                .add(rightSpan.scale(u))
                .add(upSpan.scale(v));
    }

    Optional<TargetHit> intersectTarget(
            Vec3 rayStart,
            Vec3 rayDirection,
            double maxDistance
    ) {
        Vec3 direction = rayDirection.normalize();
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

        Vec3 localHit = rayStart
                .add(direction.scale(distance))
                .subtract(origin);
        double localRight = localHit.dot(rightSpan);
        double localUp = localHit.dot(upSpan);
        float u = (float) (
                (localRight * gramC - localUp * gramB) / gramDeterminant
        );
        float v = (float) (
                (localUp * gramA - localRight * gramB) / gramDeterminant
        );
        if (!MouthTargetRegion.contains(u, v)) {
            return Optional.empty();
        }
        return Optional.of(new TargetHit(u, v));
    }

    record TargetHit(float u, float v) {
    }
}
