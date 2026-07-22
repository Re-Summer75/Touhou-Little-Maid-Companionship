package com.laixia.maidintelligence.feature.interaction.client;

import com.laixia.maidintelligence.feature.interaction.domain.MouthTargetRegion;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class MaidFacePlane {
    private static final double MIN_AXIS_LENGTH = 1.0E-5D;
    private static final double MIN_RAY_DENOMINATOR = 1.0E-5D;

    private final List<Vec3> vertices;
    private final Vec3 bottomCenter;
    private final Vec3 horizontal;
    private final Vec3 up;
    private final Vec3 normal;
    private final double width;
    private final double height;

    private MaidFacePlane(
            List<Vec3> vertices,
            Vec3 bottomCenter,
            Vec3 horizontal,
            Vec3 up,
            Vec3 normal,
            double width,
            double height
    ) {
        this.vertices = vertices;
        this.bottomCenter = bottomCenter;
        this.horizontal = horizontal;
        this.up = up;
        this.normal = normal;
        this.width = width;
        this.height = height;
    }

    static Optional<MaidFacePlane> fromVertices(
            List<Vec3> vertices,
            Vec3 expectedUp
    ) {
        if (vertices.size() != 4 || expectedUp.lengthSqr() <= 1.0E-10D) {
            return Optional.empty();
        }

        Vec3 upReference = expectedUp.normalize();
        List<Vec3> sorted = new ArrayList<>(vertices);
        sorted.sort(Comparator.comparingDouble(vertex -> vertex.dot(upReference)));

        Vec3 bottomCenter = average(sorted.get(0), sorted.get(1));
        Vec3 topCenter = average(sorted.get(2), sorted.get(3));
        Vec3 upSpan = topCenter.subtract(bottomCenter);
        double height = upSpan.length();
        if (height <= MIN_AXIS_LENGTH) {
            return Optional.empty();
        }
        Vec3 up = upSpan.scale(1.0D / height);

        Vec3 horizontalSpan = sorted.get(1).subtract(sorted.get(0));
        horizontalSpan = horizontalSpan.subtract(up.scale(horizontalSpan.dot(up)));
        double width = horizontalSpan.length();
        if (width <= MIN_AXIS_LENGTH) {
            return Optional.empty();
        }
        Vec3 horizontal = horizontalSpan.scale(1.0D / width);
        Vec3 normal = horizontal.cross(up);
        if (normal.lengthSqr() <= 1.0E-10D) {
            return Optional.empty();
        }

        return Optional.of(new MaidFacePlane(
                List.copyOf(vertices),
                bottomCenter,
                horizontal,
                up,
                normal.normalize(),
                width,
                height
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
        return bottomCenter
                .add(horizontal.scale(width * (u - 0.5D)))
                .add(up.scale(height * v));
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

        double distance = bottomCenter
                .subtract(rayStart)
                .dot(normal) / denominator;
        if (distance < 0.0D || distance > maxDistance) {
            return Optional.empty();
        }

        Vec3 localHit = rayStart
                .add(direction.scale(distance))
                .subtract(bottomCenter);
        float u = (float) (localHit.dot(horizontal) / width + 0.5D);
        float v = (float) (localHit.dot(up) / height);
        if (!MouthTargetRegion.contains(u, v)) {
            return Optional.empty();
        }
        return Optional.of(new TargetHit(u, v));
    }

    private static Vec3 average(Vec3 first, Vec3 second) {
        return first.add(second).scale(0.5D);
    }

    record TargetHit(float u, float v) {
    }
}
