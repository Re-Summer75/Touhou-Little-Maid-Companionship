package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionBand;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;

import java.util.Map;

/**
 * 一个动作的三个时刻：执行、取消、复验。
 *
 * <p>存在的理由是**把三个时刻绑在一起**。此前调度器是三条平行的 {@code if} 链，
 * 每新增一个行为要在同一个文件里改三处，而漏改一处不会有任何东西报错——只会表现为
 * "这个行为取消之后不干净"。战斗后她什么都做不了的那个 bug，症状就长在漏改的那一
 * 条 {@code cancel} 分支上。
 *
 * <p>取消和复验都有默认实现，因为大多数动作确实无事可做；但它们现在是**明确的
 * 弃权**，而不是"忘了写"。
 */
public interface IntentActionHandler {
    /**
     * 这件事属于哪一类。
     *
     * <p>没有默认值，所以新增一个行为必须回答它——而这正是许可矩阵要问的那一半。
     * 类别写在这里而不是推断出来，是因为推断只能靠包名或意图 id，两者都可能对不上。
     */
    CompanionBand band();

    ActionResult execute(
            EntityMaid maid,
            Map<String, String> parameters,
            long gameTime,
            int elapsedTicks
    );

    /** 放掉这个动作持有的东西。默认无事可放。 */
    default void cancel(EntityMaid maid, Map<String, String> parameters) {
    }

    /**
     * 两次 tick 之间世界变过之后，这一步还能不能继续。
     *
     * <p>默认答否：不认识"继续"这个概念的动作，每一步都从头开始。
     */
    default boolean revalidate(
            EntityMaid maid,
            Map<String, String> parameters,
            long gameTime
    ) {
        return false;
    }
}
