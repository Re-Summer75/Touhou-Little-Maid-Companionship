package com.laixia.maidintelligence.feature.interaction.client;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class FaceBoneClassifier {
    private static final Set<String> EXCLUDED_TOKENS = Set.of(
            "hair",
            "bang",
            "bangs",
            "fringe",
            "braid",
            "ponytail",
            "sidehair",
            "backhair",
            "longhair",
            "hairtail",
            "hat",
            "helmet",
            "hood",
            "headdress",
            "headwear",
            "crown",
            "cap",
            "bonnet",
            "veil"
    );
    private static final Set<String> FEATURE_TOKENS = Set.of(
            "eye",
            "eyes",
            "eyelid",
            "brow",
            "mouth",
            "lip",
            "blush",
            "emoji",
            "expression"
    );

    private FaceBoneClassifier() {
    }

    static int anchorPriority(String name) {
        NameParts parts = parse(name);
        if (parts.excluded() || parts.compact().endsWith("locator")) {
            return -1;
        }
        return switch (parts.compact()) {
            case "head" -> 100;
            case "bipedhead" -> 95;
            case "mainhead", "headbone" -> 90;
            case "mhead" -> 85;
            case "allhead" -> 80;
            case "face", "facemesh" -> 60;
            default -> {
                if (parts.compact().matches("head\\d+")) {
                    yield 70;
                }
                yield parts.tokens().contains("head") ? 65 : -1;
            }
        };
    }

    static Role classify(String name, Role inheritedRole) {
        if (name == null || name.isBlank()) {
            return inheritedRole;
        }

        NameParts parts = parse(name);
        if (parts.excluded()) {
            return Role.EXCLUDED;
        }
        if (parts.tokens().contains("blink")
                || parts.tokens().contains("bink")
                || parts.compact().startsWith("blink")
                || parts.compact().startsWith("bink")) {
            return Role.BLINK;
        }
        if (parts.tokens().contains("face")
                || parts.compact().equals("facemesh")) {
            return Role.FACE;
        }
        if (parts.tokens().stream().anyMatch(FEATURE_TOKENS::contains)) {
            return Role.FEATURE;
        }
        if (anchorPriority(name) >= 70) {
            return Role.HEAD;
        }
        return switch (inheritedRole) {
            case FACE, BLINK -> inheritedRole;
            case HEAD, NEUTRAL, FEATURE -> Role.NEUTRAL;
            case EXCLUDED -> Role.EXCLUDED;
        };
    }

    private static NameParts parse(String name) {
        String separated = name == null
                ? ""
                : name.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "_");
        List<String> tokens = Arrays.stream(separated.split("_+"))
                .filter(token -> !token.isBlank())
                .toList();
        String compact = String.join("", tokens);
        boolean excluded = tokens.stream().anyMatch(EXCLUDED_TOKENS::contains)
                || EXCLUDED_TOKENS.contains(compact);
        return new NameParts(tokens, compact, excluded);
    }

    enum Role {
        HEAD,
        FACE,
        BLINK,
        FEATURE,
        NEUTRAL,
        EXCLUDED
    }

    private record NameParts(
            List<String> tokens,
            String compact,
            boolean excluded
    ) {
    }
}
