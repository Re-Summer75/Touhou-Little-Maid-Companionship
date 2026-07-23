package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.snapshot.BoneSnapshot;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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

    public static Analysis analyze(AnimatedGeoModel model) {
        IdentityHashMap<AnimatedGeoBone, MutableNode> mutable = new IdentityHashMap<>();
        List<MutableNode> ordered = new ArrayList<>();
        Map<String, Integer> pathCounts = new LinkedHashMap<>();
        for (AnimatedGeoBone topLevel : model.topLevelBones()) {
            collect(
                    topLevel,
                    null,
                    "",
                    0,
                    new Matrix4f(),
                    mutable,
                    ordered,
                    pathCounts
            );
        }
        for (int index = ordered.size() - 1; index >= 0; index--) {
            MutableNode node = ordered.get(index);
            Bounds subtree = node.bounds;
            int maxChainDepth = 0;
            for (AnimatedGeoBone childBone : node.bone.children()) {
                MutableNode child = mutable.get(childBone);
                if (child == null) {
                    continue;
                }
                subtree = subtree.union(child.subtreeBounds);
                maxChainDepth = Math.max(maxChainDepth, 1 + child.maxChainDepth);
            }
            node.subtreeBounds = subtree;
            node.maxChainDepth = maxChainDepth;
        }

        IdentityHashMap<AnimatedGeoBone, Node> nodes = new IdentityHashMap<>();
        List<Node> nodeList = new ArrayList<>(ordered.size());
        Map<String, Node> paths = new LinkedHashMap<>();
        Map<String, List<Node>> names = new LinkedHashMap<>();
        for (MutableNode mutableNode : ordered) {
            Node parent = mutableNode.parent == null
                    ? null
                    : nodes.get(mutableNode.parent.bone);
            Node node = new Node(
                    mutableNode.bone,
                    parent,
                    mutableNode.path,
                    mutableNode.depth,
                    mutableNode.pivot,
                    mutableNode.bounds,
                    mutableNode.subtreeBounds,
                    mutableNode.maxChainDepth
            );
            nodes.put(node.bone(), node);
            nodeList.add(node);
            paths.put(node.path(), node);
            names.computeIfAbsent(
                    node.bone().getName().toLowerCase(Locale.ROOT),
                    ignored -> new ArrayList<>()
            ).add(node);
        }

        Bounds modelBounds = Bounds.EMPTY;
        for (Node node : nodeList) {
            modelBounds = modelBounds.union(node.bounds());
        }
        Node head = model.head() == null ? null : nodes.get(model.head());
        if (head == null) {
            head = inferHead(nodeList, modelBounds);
        }
        Bounds headBounds = head == null ? Bounds.EMPTY : head.bounds();
        if (head != null && headBounds.isEmpty()) {
            headBounds = nearestSolidDescendantBounds(head, nodes);
        }
        Node body = firstNamed(names, "body", "upperbody", "upbody", "torso");
        if (body == null) {
            body = inferBody(nodeList, modelBounds, head);
        }
        Bounds bodyBounds = body == null ? Bounds.EMPTY : body.bounds();
        if (body != null && bodyBounds.isEmpty()) {
            bodyBounds = nearestSolidDescendantBounds(body, nodes);
        }
        return new Analysis(
                List.copyOf(nodeList),
                nodes,
                Map.copyOf(paths),
                copyNameMap(names),
                modelBounds,
                head,
                headBounds,
                body,
                bodyBounds
        );
    }

    private static void collect(
            AnimatedGeoBone bone,
            MutableNode parent,
            String parentPath,
            int depth,
            Matrix4f parentTransform,
            IdentityHashMap<AnimatedGeoBone, MutableNode> nodes,
            List<MutableNode> ordered,
            Map<String, Integer> pathCounts
    ) {
        String basePath = parentPath.isEmpty()
                ? bone.getName()
                : parentPath + "/" + bone.getName();
        int duplicate = pathCounts.merge(basePath, 1, Integer::sum);
        String path = duplicate == 1 ? basePath : basePath + "#" + duplicate;

        Matrix4f transform = new Matrix4f(parentTransform);
        BoneSnapshot initial = bone.getInitialSnapshot();
        transform.translate(
                -initial.positionOffsetX / 16.0F,
                initial.positionOffsetY / 16.0F,
                initial.positionOffsetZ / 16.0F
        );
        float pivotX = bone.getPivotX() / 16.0F;
        float pivotY = bone.getPivotY() / 16.0F;
        float pivotZ = bone.getPivotZ() / 16.0F;
        transform.translate(pivotX, pivotY, pivotZ);
        transform.rotate(new Quaternionf().rotateZYX(
                initial.rotationValueZ,
                initial.rotationValueY,
                initial.rotationValueX
        ));
        transform.scale(
                initial.scaleValueX,
                initial.scaleValueY,
                initial.scaleValueZ
        );
        transform.translate(-pivotX, -pivotY, -pivotZ);

        Vector3f pivot = transform.transformPosition(
                new Vector3f(pivotX, pivotY, pivotZ)
        );
        Bounds bounds = measure(bone.geoBone().cubes(), transform);
        MutableNode node = new MutableNode(
                bone,
                parent,
                path,
                depth,
                pivot,
                bounds
        );
        nodes.put(bone, node);
        ordered.add(node);
        for (AnimatedGeoBone child : bone.children()) {
            collect(
                    child,
                    node,
                    path,
                    depth + 1,
                    transform,
                    nodes,
                    ordered,
                    pathCounts
            );
        }
    }

    private static Bounds measure(GeoMesh mesh, Matrix4f transform) {
        Bounds bounds = Bounds.EMPTY;
        for (int cube = 0; cube < mesh.getCubeCount(); cube++) {
            Vector3f position = mesh.position(cube);
            Vector3f dx = mesh.dx(cube);
            Vector3f dy = mesh.dy(cube);
            Vector3f dz = mesh.dz(cube);
            for (int corner = 0; corner < 8; corner++) {
                Vector3f point = new Vector3f(position);
                if ((corner & 1) != 0) {
                    point.add(dx);
                }
                if ((corner & 2) != 0) {
                    point.add(dy);
                }
                if ((corner & 4) != 0) {
                    point.add(dz);
                }
                transform.transformPosition(point);
                bounds = bounds.include(point);
            }
        }
        return bounds;
    }

    private static Node inferHead(List<Node> nodes, Bounds modelBounds) {
        if (modelBounds.isEmpty()) {
            return null;
        }
        double minimumY = modelBounds.minY()
                + Math.max(modelBounds.sizeY() * 0.55D, 0.0D);
        Node best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Node node : nodes) {
            if (!node.hasGeometry() || node.center().y < minimumY) {
                continue;
            }
            Vector3f size = node.size();
            double maximum = Math.max(size.x, Math.max(size.y, size.z));
            double minimum = Math.min(size.x, Math.min(size.y, size.z));
            double cubeQuality = maximum <= EPSILON ? 0.0D : minimum / maximum;
            double score = node.bounds().volume() * (0.4D + 0.6D * cubeQuality)
                    + node.center().y * 0.01D;
            if (score > bestScore) {
                best = node;
                bestScore = score;
            }
        }
        return best;
    }

    private static Node inferBody(
            List<Node> nodes,
            Bounds modelBounds,
            Node head
    ) {
        Node best = null;
        double bestVolume = Double.NEGATIVE_INFINITY;
        double maximumCenterY = modelBounds.minY() + modelBounds.sizeY() * 0.70D;
        for (Node node : nodes) {
            if (!node.hasGeometry() || node == head || node.center().y > maximumCenterY) {
                continue;
            }
            double volume = node.bounds().volume();
            if (volume > bestVolume) {
                best = node;
                bestVolume = volume;
            }
        }
        return best;
    }

    private static Node firstNamed(
            Map<String, List<Node>> names,
            String... candidates
    ) {
        for (String candidate : candidates) {
            List<Node> matches = names.get(candidate);
            if (matches != null && !matches.isEmpty()) {
                return matches.get(0);
            }
        }
        return null;
    }

    private static Bounds nearestSolidDescendantBounds(
            Node root,
            IdentityHashMap<AnimatedGeoBone, Node> nodes
    ) {
        List<Node> frontier = List.of(root);
        while (!frontier.isEmpty()) {
            Bounds levelBounds = Bounds.EMPTY;
            List<Node> next = new ArrayList<>();
            for (Node node : frontier) {
                for (AnimatedGeoBone childBone : node.bone().children()) {
                    Node child = nodes.get(childBone);
                    if (child == null) {
                        continue;
                    }
                    if (child.hasGeometry()) {
                        levelBounds = levelBounds.union(child.bounds());
                    } else {
                        next.add(child);
                    }
                }
            }
            if (!levelBounds.isEmpty()) {
                return levelBounds;
            }
            frontier = next;
        }
        return root.subtreeBounds();
    }

    private static Map<String, List<Node>> copyNameMap(Map<String, List<Node>> names) {
        Map<String, List<Node>> copy = new LinkedHashMap<>();
        names.forEach((name, nodes) -> copy.put(name, List.copyOf(nodes)));
        return Map.copyOf(copy);
    }

    public record Analysis(
            List<Node> nodes,
            IdentityHashMap<AnimatedGeoBone, Node> byBone,
            Map<String, Node> byPath,
            Map<String, List<Node>> byName,
            Bounds modelBounds,
            Node head,
            Bounds headBounds,
            Node body,
            Bounds bodyBounds
    ) {
        public Node node(AnimatedGeoBone bone) {
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
            List<Node> nameMatches =
                    byName.getOrDefault(reference.toLowerCase(Locale.ROOT), List.of());
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
            AnimatedGeoBone bone,
            Node parent,
            String path,
            int depth,
            Vector3f pivot,
            Bounds bounds,
            Bounds subtreeBounds,
            int maxChainDepth
    ) {
        public boolean hasGeometry() {
            return bone.geoBone().cubes().getCubeCount() > 0 && !bounds.isEmpty();
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

    public record Bounds(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        private static final Bounds EMPTY = new Bounds(
                Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        );

        public boolean isEmpty() {
            return minX > maxX || minY > maxY || minZ > maxZ;
        }

        public Bounds include(Vector3f point) {
            if (isEmpty()) {
                return new Bounds(point.x, point.y, point.z, point.x, point.y, point.z);
            }
            return new Bounds(
                    Math.min(minX, point.x),
                    Math.min(minY, point.y),
                    Math.min(minZ, point.z),
                    Math.max(maxX, point.x),
                    Math.max(maxY, point.y),
                    Math.max(maxZ, point.z)
            );
        }

        public Bounds union(Bounds other) {
            if (other == null || other.isEmpty()) {
                return this;
            }
            if (isEmpty()) {
                return other;
            }
            return new Bounds(
                    Math.min(minX, other.minX),
                    Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ),
                    Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY),
                    Math.max(maxZ, other.maxZ)
            );
        }

        public Vector3f center() {
            if (isEmpty()) {
                return new Vector3f();
            }
            return new Vector3f(
                    (float) ((minX + maxX) * 0.5D),
                    (float) ((minY + maxY) * 0.5D),
                    (float) ((minZ + maxZ) * 0.5D)
            );
        }

        public Vector3f size() {
            if (isEmpty()) {
                return new Vector3f();
            }
            return new Vector3f(
                    (float) (maxX - minX),
                    (float) (maxY - minY),
                    (float) (maxZ - minZ)
            );
        }

        public double sizeY() {
            return isEmpty() ? 0.0D : maxY - minY;
        }

        public double volume() {
            Vector3f size = size();
            return size.x * size.y * size.z;
        }

        public boolean contains(Bounds other, double margin) {
            return !isEmpty() && other != null && !other.isEmpty()
                    && minX <= other.minX + margin
                    && minY <= other.minY + margin
                    && minZ <= other.minZ + margin
                    && maxX >= other.maxX - margin
                    && maxY >= other.maxY - margin
                    && maxZ >= other.maxZ - margin;
        }
    }

    private static final class MutableNode {
        private final AnimatedGeoBone bone;
        private final MutableNode parent;
        private final String path;
        private final int depth;
        private final Vector3f pivot;
        private final Bounds bounds;
        private Bounds subtreeBounds = Bounds.EMPTY;
        private int maxChainDepth;

        private MutableNode(
                AnimatedGeoBone bone,
                MutableNode parent,
                String path,
                int depth,
                Vector3f pivot,
                Bounds bounds
        ) {
            this.bone = bone;
            this.parent = parent;
            this.path = path;
            this.depth = depth;
            this.pivot = pivot;
            this.bounds = bounds;
        }
    }
}
