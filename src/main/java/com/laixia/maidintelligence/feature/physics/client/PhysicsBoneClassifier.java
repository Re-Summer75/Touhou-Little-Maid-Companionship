package com.laixia.maidintelligence.feature.physics.client;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Provides optional semantic hints for a Gecko secondary-motion part. Geometry
 * and explicit metadata make the final selection; this classifier only raises
 * confidence and assigns a useful default profile when an author used a common
 * name.
 *
 * <p>Matching is per token: the name is split on camelCase and separators, each
 * token's trailing digits are stripped to a base word plus a chain index, and
 * the base word is looked up in the token sets. This uniformly catches numbered
 * chain segments regardless of which word carries the number — {@code Tail4},
 * {@code LongHair2}, {@code Ear2}, {@code Body_Tail3} all resolve — which the
 * earlier prefix/whole-word matching missed, losing every segment past the
 * first. The sets also carry common Chinese pinyin names (many Touhou Little
 * Maid model packs are authored in Chinese) as whole tokens; substring matching
 * is deliberately avoided so an expression bone like {@code weixiao} (smile) is
 * never mistaken for {@code wei} (tail).
 *
 * <p>Bones following the {@code M}-prefixed pivot convention ({@code MTail},
 * {@code MBangs}) receive no name hint. Empty pivots are rejected by geometry;
 * an unusual visible {@code M} bone can still be selected by metadata or strong
 * geometric evidence.
 */
public final class PhysicsBoneClassifier {
    private static final Set<String> TAIL_TOKENS = Set.of(
            "tail",
            // pinyin: 尾 / 尾巴 / 尾裙 / 尾曲
            "wei",
            "weiba",
            "weiqu",
            "weioqun"
    );
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
            "tophair",
            "hairtail",
            // pinyin: 头发 / 发 / 刘海 / 前发 / 后发 / 马尾 / 双马尾 / 辫子 / 辫
            "toufa",
            "liuhai",
            "qianfa",
            "houfa",
            "mawei",
            "shuangmawei",
            "danmawei",
            "bianzi",
            "bian"
    );
    private static final Set<String> EAR_TOKENS = Set.of(
            "ear",
            "ears",
            // pinyin: 耳朵 / 猫耳 / 兽耳
            "erduo",
            "maer",
            "shouer"
    );
    private static final Set<String> SKIRT_TOKENS = Set.of(
            "skirt",
            "dress",
            "hem",
            "apron",
            "cloth",
            "clothe",
            "robe",
            // pinyin: 裙 / 裙摆 / 围裙
            "qun",
            "qunbai",
            "weiqun",
            "weijin"
    );
    private static final Set<String> RIBBON_TOKENS = Set.of(
            "ribbon",
            "bow",
            "lace",
            "streamer",
            "tie",
            "band",
            // pinyin: 丝带 / 缎带 / 蝴蝶结 / 领带
            "sidai",
            "duandai",
            "hudiejie",
            "lingdai"
    );
    private static final Set<String> CAPE_TOKENS = Set.of(
            "cape",
            "cloak",
            "mantle",
            "shawl",
            "coat",
            // pinyin: 披风 / 斗篷
            "pifeng",
            "doupeng"
    );
    private static final Set<String> WING_TOKENS = Set.of(
            "wing",
            "wings",
            "feather",
            "plume",
            // pinyin: 翅 / 翅膀 / 羽翼
            "chi",
            "chibang",
            "yuyi"
    );
    // Tokens that stop chain inheritance: facial and expression parts must never
    // become physics even when they sit under a head/hair anchor.
    private static final Set<String> BREAKER_TOKENS = Set.of(
            "eye",
            "eyes",
            "eyelid",
            "eyebrow",
            "brow",
            "pupil",
            "blink",
            "iris",
            "mouth",
            "lip",
            "lips",
            "tongue",
            "teeth",
            "tooth",
            "face",
            "cheek",
            "blush",
            "nose",
            "emoji",
            "expression",
            // pinyin: 表情 / 吐舌头 / 脸 / 嘴
            "biaoqing",
            "tushetou",
            "lian",
            "zui"
    );

    private PhysicsBoneClassifier() {
    }

    public static Classification classify(String name) {
        if (name == null || name.isBlank() || isPivot(name)) {
            return Classification.NONE;
        }

        for (String token : parse(name)) {
            String base = stripTrailingDigits(token);
            ChainType chain = chainOf(base);
            if (chain != ChainType.NONE) {
                int depth = depthFromDigits(token.substring(base.length()));
                return new Classification(chain, depth);
            }
        }
        return Classification.NONE;
    }

    static boolean isPivot(String name) {
        return name != null
                && name.length() >= 2
                && name.charAt(0) == 'M'
                && Character.isUpperCase(name.charAt(1));
    }

    public static boolean isBreaker(String name) {
        for (String token : parse(name)) {
            if (BREAKER_TOKENS.contains(stripTrailingDigits(token))) {
                return true;
            }
        }
        return false;
    }

    private static ChainType chainOf(String base) {
        if (TAIL_TOKENS.contains(base)) {
            return ChainType.TAIL;
        }
        if (EAR_TOKENS.contains(base)) {
            return ChainType.EAR;
        }
        if (HAIR_TOKENS.contains(base)) {
            return ChainType.HAIR;
        }
        if (SKIRT_TOKENS.contains(base)) {
            return ChainType.SKIRT;
        }
        if (RIBBON_TOKENS.contains(base)) {
            return ChainType.RIBBON;
        }
        if (CAPE_TOKENS.contains(base)) {
            return ChainType.CAPE;
        }
        if (WING_TOKENS.contains(base)) {
            return ChainType.WING;
        }
        return ChainType.NONE;
    }

    private static String stripTrailingDigits(String token) {
        int end = token.length();
        while (end > 0 && Character.isDigit(token.charAt(end - 1))) {
            end--;
        }
        return token.substring(0, end);
    }

    private static int depthFromDigits(String digits) {
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(digits);
            // Tail=0, Tail2=1, Tail3=2 ... the first segment carries no suffix.
            return Math.max(parsed - 1, 0);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static List<String> parse(String name) {
        String separated = name
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_");
        return Arrays.stream(separated.split("_+"))
                .filter(token -> !token.isBlank())
                .toList();
    }

    public enum ChainType {
        TAIL,
        HAIR,
        EAR,
        SKIRT,
        RIBBON,
        CAPE,
        WING,
        NONE
    }

    public record Classification(ChainType type, int depth) {
        static final Classification NONE = new Classification(ChainType.NONE, 0);

        public boolean isPhysical() {
            return type != ChainType.NONE;
        }
    }
}
