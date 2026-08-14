package com.laixia.maidintelligence.feature.orchestration.tlm.errand.needs;

import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .BlockApproachTarget;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .MaintainProximityErrand;
import net.minecraft.core.BlockPos;

/**
 * 回到她被告知属于的那个地方。
 *
 * <p>家园模式关着时它什么都不答，所以意图那边不需要为此再写一个条件——没有家的
 * 女仆不可能离家太远。
 *
 * <p>归在 {@code needs} 包下，与地面食物、零食柜、三条求食同属生存 band。分类轴是
 * 中断 band，规则见 {@code docs/architecture/behavior-spec.md}。
 */
public final class ReturnHomeErrand {
    private ReturnHomeErrand() {
    }

    public static MaintainProximityErrand create() {
        return new MaintainProximityErrand("return_home", (maid, gameTime) -> {
            if (!maid.isHomeModeEnable()) {
                return null;
            }
            BlockPos home = maid.getRestrictCenter();
            return home == null || BlockPos.ZERO.equals(home)
                    ? null
                    : new BlockApproachTarget(home);
        });
    }
}
