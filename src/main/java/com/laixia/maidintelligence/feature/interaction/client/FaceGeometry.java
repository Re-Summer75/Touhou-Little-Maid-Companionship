package com.laixia.maidintelligence.feature.interaction.client;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class FaceGeometry {
    private static final double MIN_AXIS_LENGTH = 1.0E-5D;

    private FaceGeometry() {
    }

    static Optional<OrderedQuad> orderQuad(List<Vec3> vertices, Frame frame) {
        if (vertices.size() != 4
                || vertices.stream().anyMatch(vertex -> !isFinite(vertex))) {
            return Optional.empty();
        }

        List<Vec3> sorted = new ArrayList<>(vertices);
        sorted.sort(Comparator.comparingDouble(vertex -> vertex.dot(frame.up())));
        List<Vec3> bottom = new ArrayList<>(sorted.subList(0, 2));
        List<Vec3> top = new ArrayList<>(sorted.subList(2, 4));
        Comparator<Vec3> leftToRight = Comparator.comparingDouble(
                vertex -> vertex.dot(frame.right())
        );
        bottom.sort(leftToRight);
        top.sort(leftToRight);

        Vec3 leftBottom = bottom.get(0);
        Vec3 rightBottom = bottom.get(1);
        Vec3 leftTop = top.get(0);
        Vec3 rightTop = top.get(1);
        Vec3 rightSpan = rightBottom
                .subtract(leftBottom)
                .add(rightTop.subtract(leftTop))
                .scale(0.5D);
        Vec3 upSpan = leftTop
                .subtract(leftBottom)
                .add(rightTop.subtract(rightBottom))
                .scale(0.5D);
        double width = rightSpan.length();
        double height = upSpan.length();
        if (width <= MIN_AXIS_LENGTH || height <= MIN_AXIS_LENGTH) {
            return Optional.empty();
        }

        Vec3 normal = rightSpan.cross(upSpan);
        if (normal.lengthSqr() <= MIN_AXIS_LENGTH * MIN_AXIS_LENGTH) {
            return Optional.empty();
        }
        normal = normal.normalize();
        if (normal.dot(frame.forward()) < 0.0D) {
            normal = normal.scale(-1.0D);
        }

        List<Vec3> ordered = List.of(
                leftBottom,
                rightBottom,
                rightTop,
                leftTop
        );
        Vec3 center = average(ordered);
        double maximumPlaneError = 0.0D;
        for (Vec3 vertex : ordered) {
            maximumPlaneError = Math.max(
                    maximumPlaneError,
                    Math.abs(vertex.subtract(center).dot(normal))
            );
        }
        double diagonal = Math.sqrt(width * width + height * height);
        if (maximumPlaneError > diagonal * 0.02D) {
            return Optional.empty();
        }

        return Optional.of(new OrderedQuad(
                ordered,
                center,
                rightSpan,
                upSpan,
                normal,
                width,
                height,
                width * height
        ));
    }

    static Vec3 average(List<Vec3> vertices) {
        Vec3 result = Vec3.ZERO;
        for (Vec3 vertex : vertices) {
            result = result.add(vertex);
        }
        return result.scale(1.0D / vertices.size());
    }

    static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    enum Source {
        BEDROCK,
        GECKO,
        YSM
    }

    enum FailureReason {
        NO_HEAD_ANCHOR,
        NO_GEOMETRY,
        LOW_CONFIDENCE,
        UNSUPPORTED_GEOMETRY_SOURCE
    }

    record Frame(
            Vec3 origin,
            Vec3 right,
            Vec3 up,
            Vec3 forward
    ) {
        Frame {
            if (!isFinite(origin)
                    || !isFinite(right)
                    || !isFinite(up)
                    || !isFinite(forward)
                    || right.lengthSqr() <= 1.0E-10D
                    || up.lengthSqr() <= 1.0E-10D
                    || forward.lengthSqr() <= 1.0E-10D) {
                throw new IllegalArgumentException("Face frame axes must be finite and non-zero");
            }
            right = right.normalize();
            up = up.normalize();
            forward = forward.normalize();
        }
    }

    record Key(
            Source source,
            String bonePath,
            int cubeIndex,
            int faceIndex
    ) implements Comparable<Key> {
        @Override
        public int compareTo(Key other) {
            int sourceOrder = source.compareTo(other.source);
            if (sourceOrder != 0) {
                return sourceOrder;
            }
            int pathOrder = bonePath.compareTo(other.bonePath);
            if (pathOrder != 0) {
                return pathOrder;
            }
            int cubeOrder = Integer.compare(cubeIndex, other.cubeIndex);
            return cubeOrder != 0
                    ? cubeOrder
                    : Integer.compare(faceIndex, other.faceIndex);
        }
    }

    record Candidate(
            Key key,
            FaceBoneClassifier.Role role,
            List<Vec3> vertices,
            Vec3 outward,
            Vec3 groupCenter,
            double groupWidth,
            double groupHeight,
            double groupDepth,
            boolean thin
    ) {
        Candidate {
            vertices = List.copyOf(vertices);
        }
    }

    record OrderedQuad(
            List<Vec3> vertices,
            Vec3 center,
            Vec3 rightSpan,
            Vec3 upSpan,
            Vec3 normal,
            double width,
            double height,
            double area
    ) {
    }

    record RankedCandidate(
            Candidate candidate,
            OrderedQuad quad,
            double score,
            double confidence
    ) {
    }

    record Selection(
            List<RankedCandidate> ranked,
            FailureReason failureReason
    ) {
        Selection {
            ranked = List.copyOf(ranked);
        }

        boolean isAccepted() {
            return !ranked.isEmpty();
        }
    }
}
