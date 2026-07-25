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
            "hairs",
            "bang",
            "bangs",
            "fringe",
            "ponytail",
            "twintail",
            "pigtail",
            "pigtails",
            "braid",
            "sidehair",
            "backhair",
            "fronthair",
            "rearhair",
            "lefthair",
            "righthair",
            "longhair",
            "tophair",
            "hairtail",
            "sideburn",
            "sideburns",
            "ahoge",
            // Japanese romanisation
            "kami",
            "maegami",
            "ushirogami",
            "yokogami",
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
    private static final Set<String> FRINGE_TOKENS = Set.of(
            "bang", "bangs", "fringe",
            "fronthair", "hairfront", "frontbang", "frontbangs",
            "liuhai", "qianfa", "maegami"
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
            "qunzi",
            "frontskirt",
            "backskirt",
            "innerskirt",
            "outerskirt",
            "longerskirt",
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
            "pendant",
            "tassel",
            "charm",
            "guashi",
            "liusu",
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
    // Concrete facial features are hard exclusions even when their name also
    // contains a hair token or they sit below a Hair anchor.
    private static final Set<String> FACIAL_FEATURE_TOKENS = Set.of(
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
            "cheek",
            "blush",
            "nose",
            "emoji",
            "expression",
            // Pinyin and common model-pack expression aliases.
            "biaoqing",
            "tushetou",
            "zui",
            "zuiba",
            "zuibu",
            "meimao",
            "mei",
            "yanbu",
            "saihong",
            "lianhong",
            "hongyun",
            "xiao",
            "weixiao",
            "jingya",
            "kongju",
            "yinchang",
            "maozuixiao",
            "huyaxiao",
            "huaixiao",
            "shaxiao",
            "luchixiao",
            "zhangzui",
            "duzui"
    );
    // Generic face containers stop inherited hair semantics, but a deliberately
    // named child such as Face_Bangs may still provide its own hair hint.
    private static final Set<String> BREAKER_CONTAINER_TOKENS = Set.of(
            "face",
            "lian"
    );
    private static final List<String> CJK_HAIR_MARKERS = List.of(
            "头发", "頭髮", "髮", "髪",
            "刘海", "瀏海", "前发", "前髮", "前髪",
            "后发", "後髮", "後髪", "侧发", "側髮", "横髪",
            "马尾", "馬尾", "双马尾", "雙馬尾",
            "辫子", "辮子", "呆毛", "アホ毛"
    );
    private static final List<String> CJK_SKIRT_MARKERS = List.of(
            "裙", "裙摆", "裙擺", "围裙", "圍裙", "スカート", "치마"
    );
    private static final List<String> CJK_RIBBON_MARKERS = List.of(
            "挂饰", "掛飾", "吊坠", "吊墜", "流苏", "流蘇",
            "丝带", "絲帶", "蝴蝶结", "蝴蝶結", "リボン", "태슬"
    );
    private static final List<String> CJK_FACIAL_FEATURE_MARKERS = List.of(
            "眼", "眉", "嘴", "口", "表情", "舌", "牙", "鼻", "腮", "瞳",
            "脸红", "臉紅", "红晕", "紅暈"
    );
    private static final List<String> CJK_BREAKER_CONTAINER_MARKERS = List.of(
            "脸", "臉"
    );
    private static final List<String> CJK_FRINGE_MARKERS = List.of(
            "刘海", "瀏海", "前发", "前髮", "前髪"
    );

    private PhysicsBoneClassifier() {
    }

    public static Classification classify(String name) {
        if (name == null || name.isBlank() || isPivot(name)) {
            return Classification.NONE;
        }
        return classifyTokens(name);
    }

    /**
     * Visible geometry occasionally uses the same leading {@code M} naming
     * convention as an empty pivot. Once geometry proves that the node itself
     * is renderable, the prefix may be ignored without turning empty anchors
     * into simulated bones.
     */
    public static Classification classifyVisibleGeometry(String name) {
        Classification direct = classify(name);
        if (direct.isPhysical() || !isPivot(name)) {
            return direct;
        }
        return classifyTokens(name.substring(1));
    }

    public static boolean isFringeHint(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String candidate = name;
        if (isPivot(candidate)
                || (candidate.length() >= 2
                && candidate.charAt(0) == 'F'
                && Character.isUpperCase(candidate.charAt(1)))) {
            candidate = candidate.substring(1);
        }
        if (containsAny(candidate, CJK_FRINGE_MARKERS)) {
            return true;
        }
        List<String> tokens = parse(candidate);
        for (String token : tokens) {
            if (FRINGE_TOKENS.contains(stripTrailingDigits(token))) {
                return true;
            }
        }
        for (int index = 0; index + 1 < tokens.size(); index++) {
            String combined = stripTrailingDigits(tokens.get(index))
                    + stripTrailingDigits(tokens.get(index + 1));
            if (FRINGE_TOKENS.contains(combined)) {
                return true;
            }
        }
        return false;
    }

    private static Classification classifyTokens(String name) {
        if (containsAny(name, CJK_HAIR_MARKERS)) {
            return new Classification(
                    ChainType.HAIR,
                    depthFromTrailingDigits(name)
            );
        }
        if (containsAny(name, CJK_SKIRT_MARKERS)) {
            return new Classification(
                    ChainType.SKIRT,
                    depthFromTrailingDigits(name)
            );
        }
        if (containsAny(name, CJK_RIBBON_MARKERS)) {
            return new Classification(
                    ChainType.RIBBON,
                    depthFromTrailingDigits(name)
            );
        }
        List<String> tokens = parse(name);
        for (int index = 0; index + 1 < tokens.size(); index++) {
            String first = stripTrailingDigits(tokens.get(index));
            String second = stripTrailingDigits(tokens.get(index + 1));
            ChainType combined = chainOf(first + second);
            if (combined != ChainType.NONE) {
                return new Classification(
                        combined,
                        Math.max(
                                depthFromToken(tokens.get(index), first),
                                depthFromToken(tokens.get(index + 1), second)
                        )
                );
            }
        }
        for (String token : tokens) {
            String base = stripTrailingDigits(token);
            ChainType chain = chainOf(base);
            if (chain != ChainType.NONE) {
                return new Classification(
                        chain,
                        depthFromToken(token, base)
                );
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
        if (isFacialFeature(name)
                || containsAny(name, CJK_BREAKER_CONTAINER_MARKERS)) {
            return true;
        }
        return containsTokenOrPair(name, BREAKER_CONTAINER_TOKENS);
    }

    public static boolean isFacialFeature(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (containsAny(name, CJK_FACIAL_FEATURE_MARKERS)
                || containsTokenOrPair(name, FACIAL_FEATURE_TOKENS)) {
            return true;
        }
        return !isFringeHint(name)
                && (containsAny(name, CJK_BREAKER_CONTAINER_MARKERS)
                || containsTokenOrPair(name, BREAKER_CONTAINER_TOKENS));
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

    private static int depthFromToken(String token, String base) {
        return depthFromDigits(token.substring(base.length()));
    }

    private static int depthFromTrailingDigits(String value) {
        int start = value.length();
        while (start > 0 && Character.isDigit(value.charAt(start - 1))) {
            start--;
        }
        return depthFromDigits(value.substring(start));
    }

    private static boolean containsAny(
            String value,
            List<String> markers
    ) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTokenOrPair(
            String name,
            Set<String> candidates
    ) {
        if (name == null || name.isBlank()) {
            return false;
        }
        List<String> tokens = parse(name);
        for (int index = 0; index < tokens.size(); index++) {
            String first = stripTrailingDigits(tokens.get(index));
            if (candidates.contains(first)) {
                return true;
            }
            if (index + 1 < tokens.size()) {
                String second = stripTrailingDigits(tokens.get(index + 1));
                if (candidates.contains(first + second)) {
                    return true;
                }
            }
        }
        return false;
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
