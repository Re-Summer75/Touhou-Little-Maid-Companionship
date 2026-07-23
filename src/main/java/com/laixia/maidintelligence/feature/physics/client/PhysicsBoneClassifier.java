package com.laixia.maidintelligence.feature.physics.client;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Classifies a Gecko bone name into a secondary-motion chain (tail, hair,
 * ear) and its 0-based depth along that chain, so the physics layer can drive
 * only free-swinging appendages and skip everything else. Names are parsed
 * with the same camelCase/underscore normalisation as
 * {@code FaceBoneClassifier}. Bones that follow Touhou Little Maid's
 * {@code M}-prefixed pivot convention (e.g. {@code MTail}, {@code MBangs}) are
 * treated as {@link ChainType#NONE}: those are the parent locators, and the
 * physics must move the real geometry bone beneath them, never the pivot.
 */
final class PhysicsBoneClassifier {
    private static final Set<String> HAIR_TOKENS = Set.of(
            "hair",
            "bang",
            "bangs",
            "fringe",
            "ponytail",
            "braid",
            "sidehair",
            "backhair",
            "longhair",
            "hairtail"
    );
    private static final Set<String> EAR_TOKENS = Set.of(
            "ear",
            "ears"
    );

    private PhysicsBoneClassifier() {
    }

    static Classification classify(String name) {
        if (name == null || name.isBlank() || isPivot(name)) {
            return Classification.NONE;
        }

        NameParts parts = parse(name);

        String tailDigits = trailingDigits(parts.compact(), "tail");
        if (tailDigits != null) {
            return new Classification(ChainType.TAIL, depthFromDigits(tailDigits));
        }

        if (parts.tokens().stream().anyMatch(EAR_TOKENS::contains)) {
            return new Classification(ChainType.EAR, 0);
        }

        if (parts.tokens().stream().anyMatch(HAIR_TOKENS::contains)
                || HAIR_TOKENS.contains(parts.compact())) {
            return new Classification(ChainType.HAIR, trailingDepth(parts.compact()));
        }

        return Classification.NONE;
    }

    /**
     * Touhou Little Maid names each animated bone's parent locator with a
     * leading {@code M} followed by an uppercase letter ({@code MTail},
     * {@code MRightSideHair}). Those carry the animation offset and must not be
     * driven directly.
     */
    private static boolean isPivot(String name) {
        return name.length() >= 2
                && name.charAt(0) == 'M'
                && Character.isUpperCase(name.charAt(1));
    }

    /**
     * Returns the digits following {@code prefix} when {@code compact} is
     * exactly {@code prefix} optionally followed by a number (so {@code tail}
     * yields "", {@code tail4} yields "4"), or {@code null} when it is a
     * different bone.
     */
    private static String trailingDigits(String compact, String prefix) {
        if (!compact.startsWith(prefix)) {
            return null;
        }
        String remainder = compact.substring(prefix.length());
        return remainder.chars().allMatch(Character::isDigit) ? remainder : null;
    }

    private static int trailingDepth(String compact) {
        int index = compact.length();
        while (index > 0 && Character.isDigit(compact.charAt(index - 1))) {
            index--;
        }
        return depthFromDigits(compact.substring(index));
    }

    private static int depthFromDigits(String digits) {
        if (digits.isEmpty()) {
            return 0;
        }
        int parsed = Integer.parseInt(digits);
        // Tail=0, Tail2=1, Tail3=2 ... first segment carries no suffix.
        return Math.max(parsed - 1, 0);
    }

    private static NameParts parse(String name) {
        String separated = name
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_");
        List<String> tokens = Arrays.stream(separated.split("_+"))
                .filter(token -> !token.isBlank())
                .toList();
        String compact = String.join("", tokens);
        return new NameParts(tokens, compact);
    }

    enum ChainType {
        TAIL,
        HAIR,
        EAR,
        NONE
    }

    record Classification(ChainType type, int depth) {
        static final Classification NONE = new Classification(ChainType.NONE, 0);

        boolean isPhysical() {
            return type != ChainType.NONE;
        }
    }

    private record NameParts(List<String> tokens, String compact) {
    }
}
