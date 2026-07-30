package com.laixia.maidintelligence.feature.interaction.domain;

import com.laixia.maidintelligence.shared.geometry.Bounds3d;
import com.laixia.maidintelligence.shared.geometry.Quad3d;
import com.laixia.maidintelligence.shared.geometry.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Platform-neutral face geometry and selection result values.
 */
public final class FaceGeometry {
    private static final double MIN_AXIS_LENGTH = 1.0E-5D;

    private FaceGeometry() {
    }

    public static Optional<OrderedQuad> orderQuad(
            List<Vec3d> vertices,
            Frame frame
    ) {
        Objects.requireNonNull(vertices, "vertices");
        Objects.requireNonNull(frame, "frame");
        if (vertices.size() != 4
                || Bounds3d.enclosing(vertices).isEmpty()) {
            return Optional.empty();
        }
        return orderQuad(Quad3d.fromPositions(vertices), frame);
    }

    public static Optional<OrderedQuad> orderQuad(
            Quad3d geometry,
            Frame frame
    ) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(frame, "frame");
        if (Bounds3d.enclosing(geometry.positions()).isEmpty()) {
            return Optional.empty();
        }
        List<Vec3d> sorted = new ArrayList<>(geometry.positions());
        sorted.sort(Comparator.comparingDouble(
                vertex -> vertex.dot(frame.up())
        ));
        List<Vec3d> bottom = new ArrayList<>(sorted.subList(0, 2));
        List<Vec3d> top = new ArrayList<>(sorted.subList(2, 4));
        Comparator<Vec3d> leftToRight = Comparator.comparingDouble(
                vertex -> vertex.dot(frame.right())
        );
        bottom.sort(leftToRight);
        top.sort(leftToRight);

        Vec3d leftBottom = bottom.get(0);
        Vec3d rightBottom = bottom.get(1);
        Vec3d leftTop = top.get(0);
        Vec3d rightTop = top.get(1);
        Vec3d rightSpan = rightBottom
                .subtract(leftBottom)
                .add(rightTop.subtract(leftTop))
                .scale(0.5D);
        Vec3d upSpan = leftTop
                .subtract(leftBottom)
                .add(rightTop.subtract(rightBottom))
                .scale(0.5D);
        double width = rightSpan.length();
        double height = upSpan.length();
        if (width <= MIN_AXIS_LENGTH || height <= MIN_AXIS_LENGTH) {
            return Optional.empty();
        }

        Vec3d normal = rightSpan.cross(upSpan);
        if (normal.lengthSqr()
                <= MIN_AXIS_LENGTH * MIN_AXIS_LENGTH) {
            return Optional.empty();
        }
        normal = normal.normalize();
        if (normal.dot(frame.forward()) < 0.0D) {
            normal = normal.scale(-1.0D);
        }

        List<Vec3d> ordered = List.of(
                leftBottom,
                rightBottom,
                rightTop,
                leftTop
        );
        Vec3d center = average(ordered);
        double maximumPlaneError = 0.0D;
        for (Vec3d vertex : ordered) {
            maximumPlaneError = Math.max(
                    maximumPlaneError,
                    Math.abs(vertex.subtract(center).dot(normal))
            );
        }
        double diagonal = Math.sqrt(
                width * width + height * height
        );
        if (maximumPlaneError > diagonal * 0.02D) {
            return Optional.empty();
        }

        return Optional.of(new OrderedQuad(
                Quad3d.fromPositions(ordered),
                center,
                rightSpan,
                upSpan,
                normal,
                width,
                height,
                width * height
        ));
    }

    public static Vec3d average(List<Vec3d> vertices) {
        Objects.requireNonNull(vertices, "vertices");
        if (vertices.isEmpty()) {
            throw new IllegalArgumentException(
                    "cannot average empty geometry"
            );
        }
        Vec3d result = Vec3d.ZERO;
        for (Vec3d vertex : vertices) {
            result = result.add(vertex);
        }
        return result.scale(1.0D / vertices.size());
    }

    public static boolean isFinite(Vec3d vector) {
        return vector != null && vector.isFinite();
    }

    public enum Source {
        BEDROCK,
        GECKO,
        YSM
    }

    public enum FailureReason {
        NO_HEAD_ANCHOR,
        NO_GEOMETRY,
        INVALID_FRAME,
        LOW_CONFIDENCE,
        UNSUPPORTED_GEOMETRY_SOURCE
    }

    public record Frame(
            Vec3d origin,
            Vec3d right,
            Vec3d up,
            Vec3d forward
    ) {
        public Frame {
            if (!isValid(origin, right, up, forward)) {
                throw new IllegalArgumentException(
                        "Face frame basis must be finite and non-singular"
                );
            }
            right = right.normalize();
            up = up.normalize();
            forward = forward.normalize();
        }

        public static Optional<Frame> tryCreate(
                Vec3d origin,
                Vec3d right,
                Vec3d up,
                Vec3d forward
        ) {
            return isValid(origin, right, up, forward)
                    ? Optional.of(new Frame(
                            origin,
                            right,
                            up,
                            forward
                    ))
                    : Optional.empty();
        }

        private static boolean isValid(
                Vec3d origin,
                Vec3d right,
                Vec3d up,
                Vec3d forward
        ) {
            if (!isFinite(origin)
                    || !isFinite(right)
                    || !isFinite(up)
                    || !isFinite(forward)) {
                return false;
            }
            double rightLength = right.lengthSqr();
            double upLength = up.lengthSqr();
            double forwardLength = forward.lengthSqr();
            if (rightLength <= 1.0E-10D
                    || upLength <= 1.0E-10D
                    || forwardLength <= 1.0E-10D) {
                return false;
            }
            double determinant = right.cross(up).dot(forward);
            return determinant * determinant
                    > rightLength
                    * upLength
                    * forwardLength
                    * 1.0E-10D;
        }
    }

    public record Key(
            Source source,
            String bonePath,
            int cubeIndex,
            int faceIndex
    ) implements Comparable<Key> {
        public Key {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(bonePath, "bonePath");
        }

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
            int cubeOrder = Integer.compare(
                    cubeIndex,
                    other.cubeIndex
            );
            return cubeOrder != 0
                    ? cubeOrder
                    : Integer.compare(faceIndex, other.faceIndex);
        }
    }

    /**
     * {@code orderedQuad} may carry ordering computed with the same frame
     * later passed to selection, letting model discovery skip a second pass.
     */
    public record Candidate(
            Key key,
            FaceBoneClassifier.Role role,
            Quad3d geometry,
            Vec3d outward,
            Vec3d groupCenter,
            double groupWidth,
            double groupHeight,
            double groupDepth,
            boolean thin,
            OrderedQuad orderedQuad
    ) {
        public Candidate {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(geometry, "geometry");
            Objects.requireNonNull(outward, "outward");
            Objects.requireNonNull(groupCenter, "groupCenter");
        }

        public Candidate(
                Key key,
                FaceBoneClassifier.Role role,
                List<Vec3d> vertices,
                Vec3d outward,
                Vec3d groupCenter,
                double groupWidth,
                double groupHeight,
                double groupDepth,
                boolean thin,
                OrderedQuad orderedQuad
        ) {
            this(
                    key,
                    role,
                    Quad3d.fromPositions(vertices),
                    outward,
                    groupCenter,
                    groupWidth,
                    groupHeight,
                    groupDepth,
                    thin,
                    orderedQuad
            );
        }

        public Candidate(
                Key key,
                FaceBoneClassifier.Role role,
                List<Vec3d> vertices,
                Vec3d outward,
                Vec3d groupCenter,
                double groupWidth,
                double groupHeight,
                double groupDepth,
                boolean thin
        ) {
            this(
                    key,
                    role,
                    vertices,
                    outward,
                    groupCenter,
                    groupWidth,
                    groupHeight,
                    groupDepth,
                    thin,
                    null
            );
        }

        public List<Vec3d> vertices() {
            return geometry.positions();
        }
    }

    public record OrderedQuad(
            Quad3d geometry,
            Vec3d center,
            Vec3d rightSpan,
            Vec3d upSpan,
            Vec3d normal,
            double width,
            double height,
            double area
    ) {
        public OrderedQuad {
            Objects.requireNonNull(geometry, "geometry");
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(rightSpan, "rightSpan");
            Objects.requireNonNull(upSpan, "upSpan");
            Objects.requireNonNull(normal, "normal");
        }

        public List<Vec3d> vertices() {
            return geometry.positions();
        }
    }

    public record RankedCandidate(
            Candidate candidate,
            OrderedQuad quad,
            double score,
            double confidence
    ) {
    }

    public record Selection(
            List<RankedCandidate> ranked,
            FailureReason failureReason
    ) {
        public Selection {
            ranked = List.copyOf(ranked);
        }

        public boolean isAccepted() {
            return !ranked.isEmpty();
        }
    }
}
