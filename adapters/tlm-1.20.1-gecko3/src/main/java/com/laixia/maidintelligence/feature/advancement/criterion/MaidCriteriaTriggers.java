package com.laixia.maidintelligence.feature.advancement.criterion;

import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.advancements.CriteriaTriggers;

/**
 * 本模块自带的 criterion 触发器。必须在数据包解析进度之前注册好，
 * 否则引用它们的 advancement JSON 会直接报未知触发器。
 */
public final class MaidCriteriaTriggers {
    /** 玩家亲手喂食，条件比的是累计喂食次数。 */
    public static final MaidFedTrigger MAID_FED = new MaidFedTrigger(ModResources.id("maid_fed"));
    /** 女仆等级，升级时与定时对账时都会重放。 */
    public static final MaidLevelTrigger MAID_LEVEL = new MaidLevelTrigger(ModResources.id("maid_level"));
    /** 女仆好感等级。 */
    public static final MaidLevelTrigger MAID_FAVORABILITY_LEVEL =
            new MaidLevelTrigger(ModResources.id("maid_favorability_level"));
    /** 累计获得的等级经验。 */
    public static final MaidExperienceTrigger MAID_EXPERIENCE =
            new MaidExperienceTrigger(ModResources.id("maid_experience"));

    private MaidCriteriaTriggers() {
    }

    public static void register() {
        CriteriaTriggers.register(MAID_FED);
        CriteriaTriggers.register(MAID_LEVEL);
        CriteriaTriggers.register(MAID_FAVORABILITY_LEVEL);
        CriteriaTriggers.register(MAID_EXPERIENCE);
    }
}
