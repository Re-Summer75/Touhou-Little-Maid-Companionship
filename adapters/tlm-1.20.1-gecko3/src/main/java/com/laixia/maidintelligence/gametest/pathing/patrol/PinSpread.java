package com.laixia.maidintelligence.gametest.pathing.patrol;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;

import java.util.List;
import java.util.function.Consumer;

/**
 * 多连钉的**并行展开**：框架的 attempts 语义是串行重跑（N 次成功=N 轮真
 * 实驱动排队，一批五十秒的场要排上几百秒）。展开成 N 个独立实例同批并行
 * ——判据、驱动、判摔一字不动，语义仍是"N 次独立成功"，墙钟除以 N。副
 * 产品同样值钱：并行的 N 份采样把串行时代被"重试直到攒够"掩住的间歇病按
 * 真实失败率暴露出来。
 */
final class PinSpread {
    private PinSpread() {
    }

    /** 一条钉的 N 个并行实例，全部必过。 */
    static void spread(List<TestFunction> runs, String batch, int copies,
            int timeout, String name, Consumer<GameTestHelper> body) {
        for (int i = 1; i <= copies; i++) {
            runs.add(new TestFunction(batch, name + "_run" + i,
                    "minecraft:empty", Rotation.NONE,
                    timeout, 0L, true, 1, 1, body));
        }
    }
}
