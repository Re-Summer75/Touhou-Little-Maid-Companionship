package com.laixia.maidintelligence.feature.physics.geometry;

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
 * Bake-time traversal and landmark inference for the public geometry facade.
 */
final class PhysicsBoneGeometryAnalyzer {
    private static final float EPSILON = 1.0E-5F;

    private PhysicsBoneGeometryAnalyzer() {
    }

    static PhysicsBoneGeometry.Analysis analyze(BoneModelSnapshot model) {
        IdentityHashMap<BoneModelSnapshot.Bone, MutableNode> mutable =
                new IdentityHashMap<>();
        List<MutableNode> ordered = new ArrayList<>();
        Map<String, Integer> pathCounts = new LinkedHashMap<>();
        for (BoneModelSnapshot.Bone topLevel : model.topLevelBones()) {
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
            PhysicsBoneGeometry.Bounds subtree = node.bounds;
            int maxChainDepth = 0;
            for (BoneModelSnapshot.Bone childBone : node.bone.children()) {
                MutableNode child = mutable.get(childBone);
                if (child == null) {
                    continue;
                }
                subtree = subtree.union(child.subtreeBounds);
                maxChainDepth = Math.max(
                        maxChainDepth,
                        1 + child.maxChainDepth
                );
            }
            node.subtreeBounds = subtree;
            node.maxChainDepth = maxChainDepth;
        }

        IdentityHashMap<BoneModelSnapshot.Bone, PhysicsBoneGeometry.Node> nodes =
                new IdentityHashMap<>();
        List<PhysicsBoneGeometry.Node> nodeList =
                new ArrayList<>(ordered.size());
        Map<String, PhysicsBoneGeometry.Node> paths = new LinkedHashMap<>();
        Map<String, List<PhysicsBoneGeometry.Node>> names =
                new LinkedHashMap<>();
        for (MutableNode mutableNode : ordered) {
            PhysicsBoneGeometry.Node parent = mutableNode.parent == null
                    ? null
                    : nodes.get(mutableNode.parent.bone);
            PhysicsBoneGeometry.Node node = new PhysicsBoneGeometry.Node(
                    mutableNode.bone,
                    parent,
                    mutableNode.path,
                    mutableNode.depth,
                    mutableNode.pivot,
                    mutableNode.bounds,
                    mutableNode.subtreeBounds,
                    mutableNode.maxChainDepth,
                    mutableNode.cubeBoxes
            );
            nodes.put(node.bone(), node);
            nodeList.add(node);
            paths.put(node.path(), node);
            names.computeIfAbsent(
                    node.bone().getName().toLowerCase(Locale.ROOT),
                    ignored -> new ArrayList<>()
            ).add(node);
        }

        PhysicsBoneGeometry.Bounds modelBounds =
                PhysicsBoneGeometry.Bounds.EMPTY;
        for (PhysicsBoneGeometry.Node node : nodeList) {
            modelBounds = modelBounds.union(node.bounds());
        }
        PhysicsBoneGeometry.Node head =
                model.head() == null ? null : nodes.get(model.head());
        if (head == null) {
            head = inferHead(nodeList, modelBounds);
        }
        PhysicsBoneGeometry.Bounds headBounds = head == null
                ? PhysicsBoneGeometry.Bounds.EMPTY
                : head.bounds();
        if (head != null && headBounds.isEmpty()) {
            headBounds = nearestSolidDescendantBounds(head, nodes);
        }
        PhysicsBoneGeometry.Node body =
                firstNamed(names, "body", "upperbody", "upbody", "torso");
        if (body == null) {
            body = inferBody(nodeList, modelBounds, head);
        }
        PhysicsBoneGeometry.Bounds bodyBounds = body == null
                ? PhysicsBoneGeometry.Bounds.EMPTY
                : body.bounds();
        if (body != null && bodyBounds.isEmpty()) {
            bodyBounds = nearestSolidDescendantBounds(body, nodes);
        }
        return new PhysicsBoneGeometry.Analysis(
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
            BoneModelSnapshot.Bone bone,
            MutableNode parent,
            String parentPath,
            int depth,
            Matrix4f parentTransform,
            IdentityHashMap<BoneModelSnapshot.Bone, MutableNode> nodes,
            List<MutableNode> ordered,
            Map<String, Integer> pathCounts
    ) {
        String basePath = parentPath.isEmpty()
                ? bone.getName()
                : parentPath + "/" + bone.getName();
        int duplicate = pathCounts.merge(basePath, 1, Integer::sum);
        String path = duplicate == 1 ? basePath : basePath + "#" + duplicate;

        Matrix4f transform = new Matrix4f(parentTransform);
        BoneModelSnapshot.RestPose initial = bone.getInitialSnapshot();
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
        List<PhysicsBoneGeometry.CubeBox> cubeBoxes =
                measureCubes(bone.geometry().cubes(), transform);
        MutableNode node = new MutableNode(
                bone,
                parent,
                path,
                depth,
                pivot,
                measure(cubeBoxes),
                cubeBoxes
        );
        nodes.put(bone, node);
        ordered.add(node);
        for (BoneModelSnapshot.Bone child : bone.children()) {
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

    /**
     * Each cube keeps its own edge frame, so a rotated or thin piece stays an
     * exact box instead of collapsing into a loose axis-aligned hull.
     */
    private static List<PhysicsBoneGeometry.CubeBox> measureCubes(
            BoneModelSnapshot.Mesh mesh,
            Matrix4f transform
    ) {
        List<PhysicsBoneGeometry.CubeBox> boxes =
                new ArrayList<>(mesh.getCubeCount());
        for (int cube = 0; cube < mesh.getCubeCount(); cube++) {
            Vector3f corner = transform.transformPosition(
                    new Vector3f(mesh.position(cube))
            );
            Vector3f dx = transform.transformDirection(
                    new Vector3f(mesh.dx(cube))
            );
            Vector3f dy = transform.transformDirection(
                    new Vector3f(mesh.dy(cube))
            );
            Vector3f dz = transform.transformDirection(
                    new Vector3f(mesh.dz(cube))
            );
            Vector3f center = new Vector3f(corner)
                    .fma(0.5F, dx)
                    .fma(0.5F, dy)
                    .fma(0.5F, dz);
            boxes.add(new PhysicsBoneGeometry.CubeBox(
                    center,
                    axis(dx, 1.0F, 0.0F, 0.0F),
                    axis(dy, 0.0F, 1.0F, 0.0F),
                    axis(dz, 0.0F, 0.0F, 1.0F),
                    new Vector3f(
                            dx.length() * 0.5F,
                            dy.length() * 0.5F,
                            dz.length() * 0.5F
                    )
            ));
        }
        return List.copyOf(boxes);
    }

    private static Vector3f axis(
            Vector3f edge,
            float fallbackX,
            float fallbackY,
            float fallbackZ
    ) {
        float length = edge.length();
        return length <= EPSILON
                ? new Vector3f(fallbackX, fallbackY, fallbackZ)
                : new Vector3f(edge).div(length);
    }

    private static PhysicsBoneGeometry.Bounds measure(
            List<PhysicsBoneGeometry.CubeBox> cubeBoxes
    ) {
        PhysicsBoneGeometry.Bounds bounds = PhysicsBoneGeometry.Bounds.EMPTY;
        Vector3f point = new Vector3f();
        for (PhysicsBoneGeometry.CubeBox box : cubeBoxes) {
            for (int corner = 0; corner < 8; corner++) {
                box.corner(corner, point);
                bounds = bounds.include(point);
            }
        }
        return bounds;
    }

    private static PhysicsBoneGeometry.Node inferHead(
            List<PhysicsBoneGeometry.Node> nodes,
            PhysicsBoneGeometry.Bounds modelBounds
    ) {
        if (modelBounds.isEmpty()) {
            return null;
        }
        double minimumY = modelBounds.minY()
                + Math.max(modelBounds.sizeY() * 0.55D, 0.0D);
        PhysicsBoneGeometry.Node best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (PhysicsBoneGeometry.Node node : nodes) {
            if (!node.hasGeometry() || node.center().y < minimumY) {
                continue;
            }
            Vector3f size = node.size();
            double maximum = Math.max(size.x, Math.max(size.y, size.z));
            double minimum = Math.min(size.x, Math.min(size.y, size.z));
            double cubeQuality = maximum <= EPSILON
                    ? 0.0D
                    : minimum / maximum;
            double score = node.bounds().volume()
                    * (0.4D + 0.6D * cubeQuality)
                    + node.center().y * 0.01D;
            if (score > bestScore) {
                best = node;
                bestScore = score;
            }
        }
        return best;
    }

    private static PhysicsBoneGeometry.Node inferBody(
            List<PhysicsBoneGeometry.Node> nodes,
            PhysicsBoneGeometry.Bounds modelBounds,
            PhysicsBoneGeometry.Node head
    ) {
        PhysicsBoneGeometry.Node best = null;
        double bestVolume = Double.NEGATIVE_INFINITY;
        double maximumCenterY =
                modelBounds.minY() + modelBounds.sizeY() * 0.70D;
        for (PhysicsBoneGeometry.Node node : nodes) {
            if (!node.hasGeometry()
                    || node == head
                    || node.center().y > maximumCenterY) {
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

    private static PhysicsBoneGeometry.Node firstNamed(
            Map<String, List<PhysicsBoneGeometry.Node>> names,
            String... candidates
    ) {
        for (String candidate : candidates) {
            List<PhysicsBoneGeometry.Node> matches = names.get(candidate);
            if (matches != null && !matches.isEmpty()) {
                return matches.get(0);
            }
        }
        return null;
    }

    private static PhysicsBoneGeometry.Bounds nearestSolidDescendantBounds(
            PhysicsBoneGeometry.Node root,
            IdentityHashMap<
                    BoneModelSnapshot.Bone,
                    PhysicsBoneGeometry.Node
                    > nodes
    ) {
        List<PhysicsBoneGeometry.Node> frontier = List.of(root);
        while (!frontier.isEmpty()) {
            PhysicsBoneGeometry.Bounds levelBounds =
                    PhysicsBoneGeometry.Bounds.EMPTY;
            List<PhysicsBoneGeometry.Node> next = new ArrayList<>();
            for (PhysicsBoneGeometry.Node node : frontier) {
                for (BoneModelSnapshot.Bone childBone
                        : node.bone().children()) {
                    PhysicsBoneGeometry.Node child = nodes.get(childBone);
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

    private static Map<String, List<PhysicsBoneGeometry.Node>> copyNameMap(
            Map<String, List<PhysicsBoneGeometry.Node>> names
    ) {
        Map<String, List<PhysicsBoneGeometry.Node>> copy =
                new LinkedHashMap<>();
        names.forEach((name, nodes) -> copy.put(name, List.copyOf(nodes)));
        return Map.copyOf(copy);
    }

    private static final class MutableNode {
        private final BoneModelSnapshot.Bone bone;
        private final MutableNode parent;
        private final String path;
        private final int depth;
        private final Vector3f pivot;
        private final PhysicsBoneGeometry.Bounds bounds;
        private final List<PhysicsBoneGeometry.CubeBox> cubeBoxes;
        private PhysicsBoneGeometry.Bounds subtreeBounds =
                PhysicsBoneGeometry.Bounds.EMPTY;
        private int maxChainDepth;

        private MutableNode(
                BoneModelSnapshot.Bone bone,
                MutableNode parent,
                String path,
                int depth,
                Vector3f pivot,
                PhysicsBoneGeometry.Bounds bounds,
                List<PhysicsBoneGeometry.CubeBox> cubeBoxes
        ) {
            this.bone = bone;
            this.parent = parent;
            this.path = path;
            this.depth = depth;
            this.pivot = pivot;
            this.bounds = bounds;
            this.cubeBoxes = cubeBoxes;
        }
    }
}
