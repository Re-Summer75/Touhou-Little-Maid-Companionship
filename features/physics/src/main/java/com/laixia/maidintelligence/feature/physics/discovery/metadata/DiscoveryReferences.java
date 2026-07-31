package com.laixia.maidintelligence.feature.physics.discovery.metadata;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;


import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class DiscoveryReferences {
    private DiscoveryReferences() {
    }

    static List<PhysicsBoneGeometry.Node> match(
            PhysicsBoneGeometry.Analysis geometry,
            String reference
    ) {
        if (reference == null || reference.isBlank()) {
            return List.of();
        }
        if (!reference.contains("*") && !reference.contains("?")) {
            return geometry.resolve(reference);
        }
        Pattern pattern = Pattern.compile(
                globToRegex(reference),
                Pattern.CASE_INSENSITIVE
        );
        return geometry.nodes().stream()
                .filter(node -> pattern.matcher(node.path()).matches()
                        || pattern.matcher(node.bone().getName()).matches())
                .toList();
    }

    static void addSubtree(
            PhysicsBoneGeometry.Node root,
            Set<BoneModelSnapshot.Bone> output
    ) {
        ArrayDeque<BoneModelSnapshot.Bone> stack = new ArrayDeque<>();
        stack.push(root.bone());
        while (!stack.isEmpty()) {
            BoneModelSnapshot.Bone bone = stack.pop();
            if (!output.add(bone)) {
                continue;
            }
            bone.children().forEach(stack::push);
        }
    }

    static void collectDescendants(
            PhysicsBoneGeometry.Node root,
            PhysicsBoneGeometry.Analysis geometry,
            List<PhysicsBoneGeometry.Node> output
    ) {
        ArrayDeque<BoneModelSnapshot.Bone> stack =
                new ArrayDeque<>(root.bone().children());
        Set<BoneModelSnapshot.Bone> seen =
                Collections.newSetFromMap(new IdentityHashMap<>());
        while (!stack.isEmpty()) {
            BoneModelSnapshot.Bone bone = stack.pop();
            if (!seen.add(bone)) {
                continue;
            }
            PhysicsBoneGeometry.Node node = geometry.node(bone);
            if (node != null) {
                output.add(node);
            }
            bone.children().forEach(stack::push);
        }
    }

    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < glob.length(); index++) {
            char current = glob.charAt(index);
            switch (current) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '(', ')', '[', ']', '$', '^', '{', '}', '+', '|', '\\' ->
                        regex.append('\\').append(current);
                default -> regex.append(current);
            }
        }
        return regex.append('$').toString();
    }
}
