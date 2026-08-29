package com.laixia.maidintelligence.gametest.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMode;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomOccupancy;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;

import net.minecraft.world.entity.schedule.Activity;

import java.util.List;

/**
 * The freedom task, whose whole job is to bring nothing of its own.
 *
 * <p>The checks that matter are the negative ones. That it registers is easy;
 * that it contributes no competing behaviour is the property the companion
 * orchestrator relies on, and the one a later edit could quietly break by
 * adding "just one" brain task.
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class FreedomTaskGameTests {
    private FreedomTaskGameTests() {
    }

    @GameTest(batch = "freedomtask", templateNamespace = "minecraft", template = "empty")
    public static void freedomTaskIsRegistered(GameTestHelper helper) {
        helper.assertTrue(
                TaskManager.findTask(FreedomMaidTask.UID).isPresent(),
                "The freedom task was not registered with TLM"
        );
        helper.succeed();
    }

    /**
     * The entire point. Anything returned here would be a second opinion about
     * where she should go, competing with the one the orchestrator holds.
     */
    @GameTest(batch = "freedomtask", templateNamespace = "minecraft", template = "empty")
    public static void freedomContributesNoCompetingBehaviour(
            GameTestHelper helper
    ) {
        EntityMaid maid = CompanionScene.room(helper, 3, 2)
                .maid(1, 2, 1);
        int contributed = new FreedomMaidTask()
                .createBrainTasks(maid)
                .size();
        helper.assertTrue(contributed == 0,
                "The freedom task added " + contributed
                        + " brain task(s) of its own");
        helper.succeed();
    }

    /**
     * 自由模式不接受本体替她做的任何判断。
     *
     * <p>逃跑和游走这两个开关一度是开的，理由分别是"没武器时总得能跑"和"发呆
     * 也比僵在原地好看"。两条理由都对，错在把执行这两件事的权力留给了本体：
     * 那是第二个决策者，而它写移动目标的时机与我们的战斗决策完全冲突——时间线
     * 上量到的就是一次正确的后撤被 {@code RANDOM_STROLL} 抢走了腿。
     *
     * <p>逃跑本来就已经归我们：战斗意图只看敌对压力、不看有没有武器，而空手
     * 正是 {@code EngagementRiskPolicy} 判 {@code WITHDRAW} 的情形。
     *
     * <p>进食开关留着，因为它在本体那侧还门控着"她到底能不能进食"这件事本身，
     * 与"由谁决定什么时候吃"是两回事。
     */
    @GameTest(batch = "freedomtask", templateNamespace = "minecraft", template = "empty")
    public static void freedomLeavesNoJudgementToTheHost(
            GameTestHelper helper
    ) {
        EntityMaid maid = CompanionScene.room(helper, 3, 2)
                .maid(1, 2, 1);
        FreedomMaidTask task = new FreedomMaidTask();

        helper.assertFalse(task.enablePanic(maid),
                "本体的惊慌还开着，它会每 tick 抹掉我们写的移动目标");
        helper.assertFalse(task.enableLookAndRandomWalk(maid),
                "本体的随机游走还开着，它会和战斗后撤抢同一双腿");
        helper.assertTrue(task.enableEating(maid),
                "她连吃东西的能力都没有了");
        helper.assertFalse(task.workPointTask(maid),
                "没有工作的任务却占了一个工作点");
        helper.succeed();
    }

    /**
     * 自由模式是白板，其它模式一字不改。
     *
     * <p>这是整套隔离唯一要证明的东西，而且必须双向证明：只断言自由模式里没有
     * 本体行为，等于允许"我们把所有模式都改成了白板"；只断言其它模式完好，等于
     * 允许"我们其实什么都没接管"。
     *
     * <p>判据取"本体的 WORK 活动还能不能激活"，只用 Brain 的公开 API：
     * {@code setActiveActivityIfPossible} 对一个没注册过的活动不会生效。自由模式
     * 不注册 WORK/REST/PANIC，也不注册在它们之间切换的作息行为——什么时候干活、
     * 什么时候休息、什么时候逃，都是判断。本体模式必须仍然能激活它。
     *
     * <p>不数行为条数：那既要一个通到 Brain 私有字段的 accessor，又会把本体每次
     * 增删行为变成我们的测试失败。这里问的是能力在不在，不是有几条。
     */
    @GameTest(batch = "freedomtask", templateNamespace = "minecraft", template = "empty")
    public static void freedomIsBlankTheOtherModesAreUntouched(
            GameTestHelper helper
    ) {
        EntityMaid maid = CompanionScene.room(helper, 3, 2)
                .maid(1, 2, 1);

        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        helper.assertFalse(
                canActivate(maid, Activity.WORK),
                "自由模式里本体的 WORK 活动还能激活，祈求、工作餐、偷吃、"
                        + "随机走动就都还在替她做决定"
        );
        helper.assertTrue(
                canActivate(maid, Activity.CORE),
                "自由模式连 CORE 都没了，走路的 MoveToTargetSink 也在里面"
        );

        // 换到本体任意一个工作模式：它必须原封不动地拿回自己那一套。
        IMaidTask host = TaskManager.getTaskIndex().stream()
                .filter(task -> !FreedomMaidTask.UID.equals(task.getUid()))
                .findFirst()
                .orElseThrow();
        maid.setTask(host);
        helper.assertTrue(
                canActivate(maid, Activity.WORK),
                "换回本体模式后 WORK 激活不了——我们把不该碰的模式也改了"
        );

        // 再换回来：换任务会整个重建 brain，所以隔离必须是可逆的。
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        helper.assertFalse(
                canActivate(maid, Activity.WORK),
                "切走再切回来之后，自由模式没有重新变回白板"
        );

        // 我们这一侧同样双向：编排器只在自由模式启动，本体模式下连问都不问。
        // 缺了这一条，"自由模式是白板"可以由"本模组什么都没接管"来满足。
        helper.assertTrue(
                FreedomMode.isActive(maid),
                "自由模式下 FreedomMode 认为不该由我们接管"
        );
        maid.setTask(host);
        helper.assertTrue(
                FreedomMode.isHostOwned(maid),
                "本体模式下 FreedomMode 仍然认为该由我们接管；"
                        + "意图编排、战斗决策与感知都会在别人的模式里跑起来"
        );
        helper.succeed();
    }

    /** 这个活动注册过没有——没注册的活动是激活不了的。 */
    private static boolean canActivate(EntityMaid maid, Activity activity) {
        maid.getBrain().setActiveActivityIfPossible(activity);
        return maid.getBrain().isActive(activity);
    }

    /** Assigning it must actually take, and must be what she reports being on. */
    @GameTest(batch = "freedomtask", templateNamespace = "minecraft", template = "empty")
    public static void aMaidCanBeSetFree(GameTestHelper helper) {
        EntityMaid maid = CompanionScene.room(helper, 3, 2)
                .maid(1, 2, 1);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());

        helper.assertTrue(
                FreedomMaidTask.UID.equals(maid.getTask().getUid()),
                "The maid did not take the freedom task"
        );
        helper.succeed();
    }

    /**
     * Second in the list a player picks from.
     *
     * <p>Task order is insertion order and extensions register last, so without
     * help this would sit at the bottom past two dozen work tasks. Idle keeps
     * first place as the default a maid is tamed with.
     */
    @GameTest(batch = "freedomtask", templateNamespace = "minecraft", template = "empty")
    public static void freedomIsOfferedSecond(GameTestHelper helper) {
        /*
         * Never added to the level. The hidden-task filter only asks about her
         * state, and a maid actually standing in the shared grid is herself a
         * rideable entity — one extra body here re-broke a command-seating
         * test rooms away, the same neighbour interference the zombie fixture
         * already has to sidestep.
         */
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel());
        List<IMaidTask> offered = TaskManager.getNotHiddenTaskList(maid);

        helper.assertTrue(offered.size() > 1,
                "Only " + offered.size() + " task(s) were offered at all");
        helper.assertTrue(
                FreedomMaidTask.UID.equals(offered.get(1).getUid()),
                "Freedom was offered at another position; second was "
                        + offered.get(1).getUid()
        );
        // The registry itself is untouched: only the presented list moves.
        helper.assertFalse(
                FreedomMaidTask.UID.equals(
                        TaskManager.getTaskIndex().get(1).getUid()
                ),
                "The canonical task order was reordered as well"
        );
        helper.succeed();
    }

    /**
     * Home mode used to report hard occupancy for as long as it was switched
     * on, which blocked every companion intent outright — she would stand at
     * home hungry with food at her feet. Being home is a place, not an errand.
     */
    @GameTest(batch = "freedomtask", templateNamespace = "minecraft", template = "empty")
    public static void beingHomeIsNotBeingBusy(GameTestHelper helper) {
        EntityMaid maid = CompanionScene.room(helper, 3, 2)
                .maid(1, 2, 1);
        maid.setHomeModeEnable(true);
        maid.restrictTo(maid.blockPosition(), 8);
        long gameTime = helper.getLevel().getGameTime();

        boolean claimed = FreedomOccupancy.claimed(maid);
        helper.assertTrue(
                !claimed,
                "Standing inside her own home counted as returning to it"
        );
        helper.succeed();
    }

    /** But actually walking back is an errand, and does occupy her. */
    @GameTest(batch = "freedomtask", templateNamespace = "minecraft", template = "empty")
    public static void walkingHomeStillOccupiesHer(GameTestHelper helper) {
        EntityMaid maid = CompanionScene.room(helper, 3, 2)
                .maid(1, 2, 1);
        maid.setHomeModeEnable(true);
        // A home she is demonstrably not standing in.
        maid.restrictTo(maid.blockPosition().offset(200, 0, 200), 4);

        boolean claimedAway = FreedomOccupancy.claimed(maid);
        helper.assertTrue(
                claimedAway,
                "A maid far from home was not counted as heading back, got "

        );
        helper.succeed();
    }

}
