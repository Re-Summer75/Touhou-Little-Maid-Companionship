package com.laixia.maidintelligence.gametest.ambient;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.orchestration.tlm.ambient.TlmSprint;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 跑步动画跟着速度走，而且**只跟着速度走**。
 *
 * <p>这一组存在的理由是那个标志位有副作用：{@code LivingEntity.setSprinting} 会挂上
 * 原版 +30% 的速度修饰符，而本模组只要动画。所以除了"该跑的时候跑起来"，还必须钉住
 * "她的速度没被这段代码碰过"——后者比前者更容易在将来被人顺手改坏。
 *
 * <p>速度由夹具**直接挪她**产生，不走寻路。理由不是省事：走寻路的话这条测试同时依赖
 * 移动目标的坐标换算、路径能不能求出来、以及她愿不愿意走，其中任何一个坏了都长成
 * "动画没播"。第一版正是这么坏的——夹具把目标算到了两格外的反方向，而报出来的现象
 * 是"她跑得够快却不播动画"。**被测的是速度到动画那一段，那就只让这一段有机会红。**
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class SprintGameTests {
    /** 每 tick 挪这么远。高于起跑门槛（0.2806），低于一格，不会穿墙。 */
    private static final double RUNNING_STEP = 0.35D;

    private SprintGameTests() {
    }

    /**
     * 动得够快就跑，停下就收，而速度属性一分不动。
     *
     * <p>四条断言各挡一种坏法。第一条确认夹具真的把她推到了门槛以上，否则后面三条
     * 都在测空气；缺第二条，动画永远不播（改之前就是这样）；缺第三条，她会站着做
     * 跑步动作；缺第四条，某天有人把它换回 {@code setSprinting} 不会有任何东西变红，
     * 而她会悄悄快三成。
     */
    @GameTest(batch = "sprint", templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 200)
    public static void movingFastSheRunsAndHerSpeedIsUntouched(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.walledRoom(helper, 20, 6);
        EntityMaid maid = scene.maid(2, 2, 3);
        maid.setTask(new FreedomMaidTask());
        double baseline = maid.getAttributeValue(Attributes.MOVEMENT_SPEED);

        boolean[] ranWhileMoving = {false};
        boolean[] ranWhileStill = {false};
        double[] fastest = {0.0D};
        double[] last = {Double.NaN, Double.NaN};
        helper.startSequence()
                // 推着她走：每 tick 一步，方向沿房间的长边。
                .thenExecuteFor(40, () -> {
                    maid.getNavigation().stop();
                    maid.setPos(
                            maid.getX() + RUNNING_STEP,
                            maid.getY(),
                            maid.getZ()
                    );
                    fastest[0] = Math.max(fastest[0], paceSince(maid, last));
                    if (maid.isSprinting()) {
                        ranWhileMoving[0] = true;
                    }
                })
                // 松手。回差要求她掉回步行速度以下才收腿，静止显然满足。
                .thenExecuteFor(20, () -> maid.getNavigation().stop())
                .thenExecuteFor(20, () -> {
                    if (maid.isSprinting()) {
                        ranWhileStill[0] = true;
                    }
                })
                .thenExecute(() -> {
                    helper.assertTrue(
                            fastest[0] >= TlmSprint.RUNNING_PACE,
                            "夹具没把她推到起跑门槛（最快 " + fastest[0]
                                    + "，门槛 " + TlmSprint.RUNNING_PACE
                                    + "），后面三条都在测空气"
                    );
                    helper.assertTrue(
                            ranWhileMoving[0],
                            "她动得比一个冲刺的玩家还快，却没有播跑步动画——本体的"
                                    + " run 判的就是这个标志位"
                    );
                    helper.assertFalse(
                            ranWhileStill[0],
                            "她已经停下来了还在做跑步动作"
                    );
                    helper.assertTrue(
                            Math.abs(maid.getAttributeValue(
                                    Attributes.MOVEMENT_SPEED) - baseline)
                                    < 1.0E-9D,
                            "她的移动速度被改了（" + baseline + " → "
                                    + maid.getAttributeValue(
                                            Attributes.MOVEMENT_SPEED)
                                    + "）。这里只该翻动画的标志位；走 setSprinting "
                                    + "会顺手挂上原版 +30% 的修饰符"
                    );
                })
                .thenSucceed();
    }

    /**
     * 她挪得多快，只算水平——夹具自己记上一次的位置。
     *
     * <p>不借用 {@code xo} 或 {@code deltaMovement}：这条测试和被测代码跑在一 tick
     * 的**不同相位**上，那两个字段在两个相位上会给出不同答案，于是"夹具说她跑起来
     * 了"和"她没播动画"能同时成立而两句都不假。两边都自己记位置，相位就不再有意义。
     */
    private static double paceSince(EntityMaid maid, double[] last) {
        double moved = Double.isNaN(last[0])
                ? 0.0D
                : Math.hypot(maid.getX() - last[0], maid.getZ() - last[1]);
        last[0] = maid.getX();
        last[1] = maid.getZ();
        return moved;
    }
}
