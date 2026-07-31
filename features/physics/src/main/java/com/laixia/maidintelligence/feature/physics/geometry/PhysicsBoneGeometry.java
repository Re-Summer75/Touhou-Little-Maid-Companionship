package com.laixia.maidintelligence.feature.physics.geometry;

import com.laixia.maidintelligence.shared.geometry.Bounds3d;
import com.laixia.maidintelligence.shared.geometry.Vec3d;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rest-pose measurements used by automatic soft-part discovery. All positions
 * are normalised Gecko model units (JSON units divided by sixteen).
 */
public final class PhysicsBoneGeometry {
    private static final float EPSILON = 1.0E-5F;

    private PhysicsBoneGeometry() {
    }

    public static Analysis analyze(BoneModelSnapshot model) {
        return PhysicsBoneGeometryAnalyzer.analyze(model);
    }

    public record Analysis(
            List<Node> nodes,
            IdentityHashMap<BoneModelSnapshot.Bone, Node> byBone,
            Map<String, Node> byPath,
            Map<String, List<Node>> byName,
            Bounds modelBounds,
            Node head,
            Bounds headBounds,
            Node body,
            Bounds bodyBounds
    ) {
        public Node node(BoneModelSnapshot.Bone bone) {
            return byBone.get(bone);
        }

        public List<Node> resolve(String reference) {
            if (reference == null || reference.isBlank()) {
                return List.of();
            }
            Node path = byPath.get(reference);
            if (path != null) {
                return List.of(path);
            }
            String normalised = reference.replace('\\', '/');
            path = byPath.get(normalised);
            if (path != null) {
                return List.of(path);
            }
            String suffix = "/" + normalised;
            List<Node> suffixMatches = byPath.entrySet().stream()
                    .filter(entry -> entry.getKey().endsWith(suffix))
                    .map(Map.Entry::getValue)
                    .toList();
            if (suffixMatches.size() == 1) {
                return suffixMatches;
            }
            List<Node> nameMatches = byName.getOrDefault(
                    reference.toLowerCase(Locale.ROOT),
                    List.of()
            );
            return nameMatches.size() == 1 ? nameMatches : List.of();
        }

        public boolean isInHeadSubtree(Node node) {
            if (head == null || node == null) {
                return false;
            }
            Node cursor = node;
            while (cursor != null) {
                if (cursor == head) {
                    return true;
                }
                cursor = cursor.parent();
            }
            return false;
        }
    }

    public record Node(
            BoneModelSnapshot.Bone bone,
            Node parent,
            String path,
            int depth,
            Vector3f pivot,
            Bounds bounds,
            Bounds subtreeBounds,
            int maxChainDepth,
            List<CubeBox> cubeBoxes
    ) {
        public boolean hasGeometry() {
            return bone.geometry().cubes().getCubeCount() > 0
                    && !bounds.isEmpty();
        }

        public Vector3f center() {
            return bounds.isEmpty() ? new Vector3f(pivot) : bounds.center();
        }

        public Vector3f size() {
            return bounds.size();
        }

        public double thinRatio() {
            Vector3f size = size();
            double maximum = Math.max(size.x, Math.max(size.y, size.z));
            double minimum = Math.min(size.x, Math.min(size.y, size.z));
            return maximum <= EPSILON ? 1.0D : minimum / maximum;
        }

        public boolean isDescendantOf(Node ancestor) {
            Node cursor = this;
            while (cursor != null) {
                if (cursor == ancestor) {
                    return true;
                }
                cursor = cursor.parent;
            }
            return false;
        }
    }

    /**
     * One rest-pose cube as an oriented box in model space.
     */
    public record CubeBox(
            Vector3f center,
            Vector3f axisX,
            Vector3f axisY,
            Vector3f axisZ,
            Vector3f half
    ) {
        public Vector3f corner(int index, Vector3f output) {
            return output.set(center)
                    .fma((index & 1) == 0 ? -half.x : half.x, axisX)
                    .fma((index & 2) == 0 ? -half.y : half.y, axisY)
                    .fma((index & 4) == 0 ? -half.z : half.z, axisZ);
        }

        /** Conservative axis-aligned half extents, used for cheap culling. */
        public Vector3f axisAlignedHalf(Vector3f output) {
            return output.set(
                    Math.abs(axisX.x) * half.x
                            + Math.abs(axisY.x) * half.y
                            + Math.abs(axisZ.x) * half.z,
                    Math.abs(axisX.y) * half.x
                            + Math.abs(axisY.y) * half.y
                            + Math.abs(axisZ.y) * half.z,
                    Math.abs(axisX.z) * half.x
                            + Math.abs(axisY.z) * half.y
                            + Math.abs(axisZ.z) * half.z
            );
        }

        public double volume() {
            return 8.0D * half.x * half.y * half.z;
        }
    }

    public static final class Bounds {
        static final Bounds EMPTY = new Bounds(null);

        private final Bounds3d value;

        private Bounds(Bounds3d value) {
            this.value = value;
        }

        public boolean isEmpty() {
            return value == null;
        }

        public Bounds include(Vector3f point) {
            Vec3d position = new Vec3d(point.x, point.y, point.z);
            return isEmpty()
                    ? new Bounds(new Bounds3d(position, position))
                    : new Bounds(value.include(position));
        }

        public Bounds union(Bounds other) {
            if (other == null || other.isEmpty()) {
                return this;
            }
            return isEmpty() ? other : new Bounds(value.union(other.value));
        }

        public Vector3f center() {
            if (isEmpty()) {
                return new Vector3f();
            }
            Vec3d center = value.center();
            return new Vector3f(
                    (float) center.x,
                    (float) center.y,
                    (float) center.z
            );
        }

        public Vector3f size() {
            if (isEmpty()) {
                return new Vector3f();
            }
            Vec3d size = value.size();
            return new Vector3f(
                    (float) size.x,
                    (float) size.y,
                    (float) size.z
            );
        }

        public double minX() {
            return isEmpty() ? Double.POSITIVE_INFINITY : value.minimum().x;
        }

        public double minY() {
            return isEmpty() ? Double.POSITIVE_INFINITY : value.minimum().y;
        }

        public double minZ() {
            return isEmpty() ? Double.POSITIVE_INFINITY : value.minimum().z;
        }

        public double maxX() {
            return isEmpty() ? Double.NEGATIVE_INFINITY : value.maximum().x;
        }

        public double maxY() {
            return isEmpty() ? Double.NEGATIVE_INFINITY : value.maximum().y;
        }

        public double maxZ() {
            return isEmpty() ? Double.NEGATIVE_INFINITY : value.maximum().z;
        }

        public double sizeY() {
            return isEmpty() ? 0.0D : value.size().y;
        }

        public double volume() {
            return isEmpty() ? 0.0D : value.volume();
        }

        public boolean contains(Bounds other, double margin) {
            return !isEmpty() && other != null && !other.isEmpty()
                    && value.contains(other.value, margin);
        }
    }
}
