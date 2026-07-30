package com.laixia.maidintelligence.feature.advancement.domain;

import java.util.Objects;
import java.util.Set;

/**
 * 哪些进度算女仆的。默认是「玩家有什么女仆就有什么」，只排除讲的本来就是玩家的那些。
 * <p>
 * 车万女仆本体那一整套（{@code touhou_little_maid})讲的是玩家怎么和女仆相处
 * ——召唤女仆、驯服女仆、给女仆拍照、拿走女仆的经验，女仆自己去完成毫无意义。
 * 而且其中有几条用的是原版判定，桥接会真的把它们判给女仆；另有隐藏条目直接发物品，
 * 判给女仆会造成重复奖励。
 */
public final class MaidAdvancementScope {
    private static final Set<String> EXCLUDED_NAMESPACES =
            Set.of("touhou_little_maid");

    private MaidAdvancementScope() {
    }

    public static boolean includes(ResourceId advancement) {
        Objects.requireNonNull(advancement, "advancement");
        return !EXCLUDED_NAMESPACES.contains(advancement.namespace());
    }
}
