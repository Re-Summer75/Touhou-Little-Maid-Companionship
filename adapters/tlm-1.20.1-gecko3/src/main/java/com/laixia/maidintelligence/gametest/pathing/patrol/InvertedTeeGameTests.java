package com.laixia.maidintelligence.gametest.pathing.patrol;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.gametest.support.PathwalkTrace;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 倒 T 的四格对照：缺口两格 / 三格 × 主人站在结构上 / 结构外。
 *
 * <p>玩家实测的原话是"倒 T 和平台间隔两格不会出问题，三格一定会"，而此前
 * 按同样形状建的钉子却全绿——差别一定在别的维度上，所以这里把两个维度摆成
 * 四格对照，一次跑完再说，不再一个个猜。
 *
 * <p>**主人在不在结构上**是最可疑的那一维：实机截图里玩家站在结构外看着
 * 她，于是她的走目标是空中的一个点，A* 只给得出"到不了的残路"，而离主人
 * 最近的可达点恰恰是倒 T 顶上那一格——她爬上去，再继续朝主人挪，就挪出去
 * 了。这与"跑到 T 形最顶部、准备下一格时掉下去"一字不差。
 *
 * <p>速度按实机：跟随计划里写死 0.75，执行侧的 {@code pace()} 直接用它。
 * （试过压到 1.5 模拟"奔跑"，那是六倍速、一 tick 挪一格六，她在走到缺口前
 * 就冲出了梁——那不是奔跑，是瞬移，夹具不成立。）
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class InvertedTeeGameTests {
    /** 抬到干落上限之外：不给她"跳下去走地面"这条捷径。 */
    private static final int DECK = 9;

    /** 实机跟随的速度系数（escort_owner 计划里写死的那个）。 */
    private static final float FOLLOW_PACE = 0.75F;

    private static final int WATCHED = 500;

    private InvertedTeeGameTests() {
    }

    /** 缺口两格、主人站在横杠东端：玩家说这个不出问题。 */
    static void overTheTeeBeyondATwoGapWithHimOnTheBar(
            GameTestHelper helper
    ) {
        teeRun(helper, 2, false, "tee gap2 on-bar");
    }

    /** 缺口三格、主人站在横杠东端。 */
    static void overTheTeeBeyondAThreeGapWithHimOnTheBar(
            GameTestHelper helper
    ) {
        teeRun(helper, 3, false, "tee gap3 on-bar");
    }

    /** 缺口两格、主人在结构外的半空——她够不着他，只能贴到最近的点。 */
    static void overTheTeeBeyondATwoGapWithHimOffTheStructure(
            GameTestHelper helper
    ) {
        teeRun(helper, 2, true, "tee gap2 off-structure");
    }

    /** 缺口三格、主人在结构外：玩家实机的那一格，预期这里复现"必掉"。 */
    static void overTheTeeBeyondAThreeGapWithHimOffTheStructure(
            GameTestHelper helper
    ) {
        teeRun(helper, 3, true, "tee gap3 off-structure");
    }

    /**
     * 引桥 x0..4（走面 DECK+1）→ 缺口 {@code gap} 格 → 横杠三格、正中间顶
     * 上一格。她跟着主人过去；摔一次就是红。
     *
     * @param offStructure 主人是否站在结构之外的半空（她够不着）
     */
    private static void teeRun(GameTestHelper helper, int gap,
            boolean offStructure, String tape) {
        for (int x = -1; x <= 15; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 0; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.GLASS);
        }
        int barFrom = 5 + gap;
        for (int x = barFrom; x <= barFrom + 2; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.GLASS);
        }
        helper.setBlock(new BlockPos(barFrom + 1, DECK + 1, 2), Blocks.GLASS);

        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        Player owner = helper.makeMockPlayer();
        double ownerX = zero.getX() + (offStructure
                ? barFrom + 5.5D : barFrom + 2.5D);
        double ownerY = zero.getY() + DECK + (offStructure ? 3.0D : 1.0D);
        owner.setPos(ownerX, ownerY, zero.getZ() + 2.5D);

        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(zero.getX() + 1.5D, zero.getY() + DECK + 1.0D,
                zero.getZ() + 2.5D);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setHomeModeEnable(false);
        maid.setOwnerUUID(owner.getUUID());
        helper.getLevel().addFreshEntity(maid);
        // setTask 必须在入场之后：它会重建脑子并对旧的 stopAll，对尚未被
        // 世界接受的实体做这件事，症状要很久以后才现形（仓库旧教训）。
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());

        PathwalkTrace trace = new PathwalkTrace(tape, zero);
        double fallLine = zero.getY() + DECK + 0.4D;
        double[] lowest = new double[]{maid.getY()};
        double[] farthest = new double[]{maid.getX()};

        for (int tick = 1; tick <= WATCHED; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                trace.sample(at, maid);
                lowest[0] = Math.min(lowest[0], maid.getY());
                farthest[0] = Math.max(farthest[0], maid.getX());
                // 主人原地小幅挪动，脚不停；走目标每 tick 指着他本人。
                owner.setPos(ownerX + Math.sin(at * 0.25D) * 0.6D,
                        ownerY, owner.getZ());
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new EntityTracker(owner, false),
                                FOLLOW_PACE, 0)
                );
            });
        }

        helper.runAfterDelay(WATCHED + 20, () -> {
            trace.dump();
            String diary = BridgePatrol.diaryOf(maid);
            double reached = farthest[0] - zero.getX();
            boolean fell = lowest[0] < fallLine;
            maid.discard();
            owner.discard();
            helper.assertTrue(!fell,
                    "缺口 " + gap + " 格（主人"
                            + (offStructure ? "在结构外" : "在横杠上")
                            + "）：她摔了，最远只到 x="
                            + String.format("%.2f", reached) + "；" + diary);
            helper.assertTrue(reached >= barFrom,
                    "缺口 " + gap + " 格：她没能过到倒 T 上（最远 x="
                            + String.format("%.2f", reached) + "）；" + diary);
            helper.succeed();
        });
    }

    /** 多连钉并行展开（语义不变，墙钟除以连数），见 {@code BridgePatrol.spread}。 */
    @GameTestGenerator
    public static java.util.Collection<net.minecraft.gametest.framework
            .TestFunction> invertedTeeRuns() {
        java.util.List<net.minecraft.gametest.framework.TestFunction> runs =
                new java.util.ArrayList<>();
        PinSpread.spread(runs, "invertedtee", 4, 560,
                "overtheteebeyondatwogapwithhimonthebar",
                InvertedTeeGameTests::overTheTeeBeyondATwoGapWithHimOnTheBar);
        PinSpread.spread(runs, "invertedtee", 4, 560,
                "overtheteebeyondathreegapwithhimonthebar",
                InvertedTeeGameTests::overTheTeeBeyondAThreeGapWithHimOnTheBar);
        PinSpread.spread(runs, "invertedtee", 4, 560,
                "overtheteebeyondatwogapwithhimoffthestructure",
                InvertedTeeGameTests::overTheTeeBeyondATwoGapWithHimOffTheStructure);
        PinSpread.spread(runs, "invertedtee", 4, 560,
                "overtheteebeyondathreegapwithhimoffthestructure",
                InvertedTeeGameTests::overTheTeeBeyondAThreeGapWithHimOffTheStructure);
        return runs;
    }
}
