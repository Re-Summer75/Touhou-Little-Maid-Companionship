package com.laixia.maidintelligence.feature.physics.client.discovery;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneClassifier;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneSelectionPlan;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class DiscoveryMath {
    static final double EPSILON = 1.0E-5D;

    private DiscoveryMath() {
    }

    static PhysicsBoneSelectionPlan.PartType partType(
            PhysicsBoneClassifier.ChainType type
    ) {
        return switch (type) {
            case HAIR -> PhysicsBoneSelectionPlan.PartType.HAIR;
            case TAIL -> PhysicsBoneSelectionPlan.PartType.TAIL;
            case EAR -> PhysicsBoneSelectionPlan.PartType.EAR;
            case SKIRT -> PhysicsBoneSelectionPlan.PartType.SKIRT;
            case RIBBON -> PhysicsBoneSelectionPlan.PartType.RIBBON;
            case CAPE -> PhysicsBoneSelectionPlan.PartType.CAPE;
            case WING -> PhysicsBoneSelectionPlan.PartType.WING;
            case NONE -> PhysicsBoneSelectionPlan.PartType.GENERIC;
        };
    }

    static String autoChainId(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneSelectionPlan.PartType type
    ) {
        PhysicsBoneGeometry.Node root = node;
        while (root.parent() != null
                && root.parent().bone().children().size() == 1
                && root.parent().hasGeometry()) {
            root = root.parent();
        }
        return "auto_" + type.name().toLowerCase(Locale.ROOT)
                + "_" + root.path().replace('/', '_');
    }

    static List<String> tokens(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String separated = name
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_");
        return Arrays.stream(separated.split("_+"))
                .filter(token -> !token.isBlank())
                .map(token -> token.replaceFirst("\\d+$", ""))
                .toList();
    }

    static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    static double square(double value) {
        return value * value;
    }

    static double ratio(double first, double second) {
        double smaller = Math.max(Math.min(first, second), EPSILON);
        return Math.max(first, second) / smaller;
    }
}
