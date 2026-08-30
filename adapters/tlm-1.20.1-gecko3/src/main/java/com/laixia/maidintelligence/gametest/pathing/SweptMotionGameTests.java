package com.laixia.maidintelligence.gametest.pathing;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.sweep
        .SweptAcceptance;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.sweep.SweptMotion;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 扫掠仿真内核的验收——它将成为一切边族的唯一裁判，所以先让它自己过堂。
 *
 * <p>两类考题：**几何题**（撞墙截停、门板收稳、石锥拒稳、撞头压弧）拿真
 * 实 VoxelShape 对答案；**黄金题**拿真实物理对答案——给一只真女仆直写同
 * 一初速让原版引擎飞一遍，她落哪儿仿真就得说哪儿。数学芯已在纯 JVM 侧对
 * 表过实机黑匣子（{@code BallisticArcVerification}），这里对的是碰撞解算。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class SweptMotionGameTests {
    private static final int DECK = 4;

    /** 她的身位：与 EntityMaid 的碰撞箱同尺。 */
    private static final double WIDTH = 0.6D;
    private static final double HEIGHT = 1.5D;

    /** 黄金题允差：分轴次序与实体细节的毫差，半格封顶。 */
    private static final double GOLDEN_SLACK = 0.25D;

    private SweptMotionGameTests() {
    }

    /**
     * 黄金对表：同一初速，原版物理飞出来的落点=仿真说的落点。
     *
     * <p>她在滞空里就是弹道体——执行器对无锁滞空不接管、脑子里没有走目标
     * 时空中输入为零，所以直写一股初速后她的飞行就是纯原版物理。这是内核
     * 能拿到的最硬的真值。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "sweptmotion", timeoutTicks = 200)
    public static void herRealFlightLandsWhereTheSimulationSays(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        Vec3 start = new Vec3(zero.getX() + 1.5D, zero.getY() + DECK + 1.0D,
                zero.getZ() + 2.5D);
        Vec3 launch = new Vec3(0.30D, 0.42D, 0.0D);

        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(start.x, start.y, start.z);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());

        SweptMotion.Flight said = SweptMotion.fly(
                helper.getLevel(), start, launch, WIDTH, HEIGHT);
        helper.runAfterDelay(5, () -> maid.setDeltaMovement(launch));
        helper.runAfterDelay(6 + SweptMotion.LONGEST_FLIGHT, () -> {
            Vec3 landed = maid.position();
            maid.discard();
            helper.assertTrue(
                    said.outcome() == SweptMotion.Outcome.LANDED,
                    "平台上平跳一步，仿真居然说 " + said.outcome());
            helper.assertTrue(
                    landed.distanceTo(said.end()) < GOLDEN_SLACK,
                    "原版物理落在 " + landed + "，仿真说 " + said.end()
                            + "——差 " + String.format("%.2f",
                                    landed.distanceTo(said.end())) + " 格");
            helper.succeed();
        });
    }

    /** 几何四题：撞墙截停、门板收稳、石锥拒稳、撞头压弧。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "sweptmotion", timeoutTicks = 100)
    public static void wallsLidsSpikesAndCeilingsAllAnswerCorrectly(
            GameTestHelper helper
    ) {
        // 一条起跳台，四条并排的靶道（z=0..3 各一题，互不相扰）。
        for (int z = 0; z <= 7; z++) {
            helper.setBlock(new BlockPos(1, DECK, z), Blocks.STONE);
        }
        // 题一（z=0）：前方两格立一堵两高的墙。
        helper.setBlock(new BlockPos(3, DECK + 1, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(3, DECK + 2, 0), Blocks.STONE);
        // 靶位由物理定：0.35 前速带首 tick 地面摩擦，渐近射程 x≈3.97——
        // 靶放 x=4 就永远够不到（第一版真放过：门板题红、石锥断言恒真）。
        // 题二（z=2）：落点是一块悬空的下半门板。
        helper.setBlock(new BlockPos(3, DECK + 1, 2), Blocks.OAK_TRAPDOOR);
        // 题三（z=4）：落点格立一根滴水石锥（下面垫实心）。
        helper.setBlock(new BlockPos(3, DECK, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(3, DECK + 1, 4),
                Blocks.POINTED_DRIPSTONE);
        // 题四（z=6）：头顶两格盖板压弧；对照道 z=7 无盖。两道共用一条低
        // 三格的承接台——撞头砍掉上升段的滞空，同一承接层上必然更早落地、
        // 飞得更短。
        helper.setBlock(new BlockPos(2, DECK + 3, 6), Blocks.STONE);
        helper.setBlock(new BlockPos(3, DECK + 3, 6), Blocks.STONE);
        for (int x = 2; x <= 8; x++) {
            helper.setBlock(new BlockPos(x, DECK - 2, 6), Blocks.STONE);
            helper.setBlock(new BlockPos(x, DECK - 2, 7), Blocks.STONE);
        }

        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        helper.runAfterDelay(2, () -> {
            double feet = zero.getY() + DECK + 1.0D;
            Vec3 launch = new Vec3(0.35D, 0.42D, 0.0D);

            SweptMotion.Flight wall = SweptMotion.fly(helper.getLevel(),
                    at(zero, 1.5D, feet, 0.5D), launch, WIDTH, HEIGHT);
            helper.assertTrue(
                    wall.end().x < zero.getX() + 3.0D,
                    "两高的墙没把她截停：仿真飞到了 x="
                            + (wall.end().x - zero.getX()));

            SweptMotion.Flight lid = SweptMotion.fly(helper.getLevel(),
                    at(zero, 1.5D, feet, 2.5D), launch, WIDTH, HEIGHT);
            helper.assertTrue(
                    lid.outcome() == SweptMotion.Outcome.LANDED
                            && Math.abs(lid.end().y
                                    - (zero.getY() + DECK + 1.1875D)) < 0.05D,
                    "悬空门板该把弧收稳在板顶 0.1875：仿真给的是 "
                            + lid.outcome() + " y="
                            + (lid.end().y - zero.getY()));

            // 石锥题的断言错过两次才写对：一次要求 PERCHED（其实她贴柱落
            // 在垫底石头上，物理正确）；一次拿整形包围盒的西面判（石锥是
            // **多段盒**——尖细、基座宽，还带按坐标伪随机的 XZ 偏移——她
            // 贴着上段细盒停下，包围盒最宽面冤枉她"穿透"）。终审版是体积
            // 语义：落点处的身位箱与柱的**每个真实碰撞盒**都不相交。
            BlockPos spikeCell = helper.absolutePos(new BlockPos(3,
                    DECK + 1, 4));
            SweptMotion.Flight spike = SweptMotion.fly(helper.getLevel(),
                    at(zero, 1.5D, feet, 4.5D), launch, WIDTH, HEIGHT);
            net.minecraft.world.phys.AABB landedBody =
                    new net.minecraft.world.phys.AABB(
                            spike.end().x - WIDTH / 2.0D + 0.001D,
                            spike.end().y + 0.001D,
                            spike.end().z - WIDTH / 2.0D + 0.001D,
                            spike.end().x + WIDTH / 2.0D - 0.001D,
                            spike.end().y + HEIGHT - 0.001D,
                            spike.end().z + WIDTH / 2.0D - 0.001D);
            boolean intruded = helper.getLevel().getBlockState(spikeCell)
                    .getCollisionShape(helper.getLevel(), spikeCell)
                    .toAabbs().stream()
                    .anyMatch(box -> box.move(spikeCell.getX(),
                                    spikeCell.getY(), spikeCell.getZ())
                            .intersects(landedBody));
            helper.assertFalse(intruded,
                    "身位箱穿进了石锥柱身：落点 ("
                            + String.format("%.2f", spike.end().x
                                    - zero.getX())
                            + ", "
                            + String.format("%.2f", spike.end().z
                                    - zero.getZ()) + ")");

            SweptMotion.Flight capped = SweptMotion.fly(helper.getLevel(),
                    at(zero, 1.5D, feet, 6.5D), launch, WIDTH, HEIGHT);
            SweptMotion.Flight free = SweptMotion.fly(helper.getLevel(),
                    at(zero, 1.5D, feet, 7.5D), launch, WIDTH, HEIGHT);
            helper.assertTrue(
                    capped.end().x < free.end().x - 0.1D,
                    "撞头没有压短弧：盖板道飞到 x="
                            + (capped.end().x - zero.getX()) + "，敞道 x="
                            + (free.end().x - zero.getX()));
            helper.succeed();
        });
    }

    /**
     * 干净的缺口不该绕道：中线飞得过去就走中线。车道是**兜底的第二条
     * 路**，不是常态——它若在常态上也生效，每条跳边都要多扫二十次。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing")
    public static void aCleanGapNeedsNoLane(GameTestHelper helper) {
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        walkway(helper, 0, 3);
        walkway(helper, 6, 9);
        double lane = laneAcross(helper, zero, 3.5D, 6);
        helper.assertTrue(lane == 0.0D,
                "干净缺口该走中线，却让开了 " + lane);
        helper.succeed();
    }

    /**
     * 柱压在**弧线途中**（玩家实机场景）：中线正撞柱心，柱旁的侧缝三维
     * 里是空的——扫掠该解出一条侧向车道，而不是把整条边否掉。
     *
     * <p>否掉的代价实测过：{@code path=null}、整场重下一百三十多次单，
     * 行为层最后放弃（柱旁车道案）。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing")
    public static void aPostAcrossTheArcBendsTheLaneAside(
            GameTestHelper helper
    ) {
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        walkway(helper, 0, 3);
        walkway(helper, 5, 9);
        helper.setBlock(new BlockPos(5, DECK + 1, 1), Blocks.OAK_FENCE);
        double lane = laneAcross(helper, zero, 3.5D, 6);
        helper.assertTrue(!Double.isNaN(lane),
                "柱旁的缝是空的，这一跳却一条车道都没解出来");
        helper.assertTrue(Math.abs(lane) > 0.0D,
                "中线正撞柱心，车道却仍是 0");
        helper.succeed();
    }

    /**
     * 柱压在**起跳格**（玩家点名"边缘放一个栅栏试试"）：格心站不下人，
     * 但沿地板边缘侧一步就绕得过去，那一步同样是车道。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing")
    public static void aPostOnTheBrinkBendsTheLaneAside(
            GameTestHelper helper
    ) {
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        walkway(helper, 0, 3);
        walkway(helper, 6, 9);
        helper.setBlock(new BlockPos(3, DECK + 1, 1), Blocks.OAK_FENCE);
        double lane = laneAcross(helper, zero, 2.5D, 6);
        helper.assertTrue(!Double.isNaN(lane),
                "崖沿柱旁绕得过去，这一跳却一条车道都没解出来");
        helper.succeed();
    }

    /**
     * 直跳的边**存不存在**：缺口断了两条道、第三条完好，直线跳过去只
     * 有两格。
     *
     * <p>"她该跳却绕了路"这条红追过三轮定价（跳边加价 0.6、0.15、撤
     * 销），全在症状上动手。量尺后来把账算清了——直跳 2.5、绕行 3.228，
     * 平滑后也要 2.828，**直跳本来就该赢**。账既然不亏，那就只剩一种可
     * 能：这条边在图上根本不成立。这颗钉子把问题劈成两半，答的是"边在
     * 不在"，不是"贵不贵"。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing")
    public static void aTwoBlockGapStillOffersTheStraightJump(
            GameTestHelper helper
    ) {
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 2; z++) {
                if (x == 4 && z <= 1) {
                    continue;
                }
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        double floor = zero.getY() + DECK + 1;
        double lane = SweptAcceptance.laneFor(helper.getLevel(),
                zero.getX() + 3.5D, floor, zero.getZ() + 1.5D,
                zero.getX() + 5, floor, zero.getZ() + 1,
                WIDTH, HEIGHT);
        helper.assertTrue(!Double.isNaN(lane),
                "两格缺口的直跳在图上就不成立——定价再怎么改，她也只能绕路");
        helper.succeed();
    }

    /** 一格宽的走道，{@code from}..{@code to} 闭区间。 */
    private static void walkway(GameTestHelper helper, int from, int to) {
        for (int x = from; x <= to; x++) {
            helper.setBlock(new BlockPos(x, DECK, 1), Blocks.DIRT);
        }
    }

    /** 从走道上的 {@code fromX} 跳到格 {@code toX}，问这条边走哪条道。 */
    private static double laneAcross(GameTestHelper helper, BlockPos zero,
            double fromX, int toX) {
        double floor = zero.getY() + DECK + 1;
        return SweptAcceptance.laneFor(helper.getLevel(),
                zero.getX() + fromX, floor, zero.getZ() + 1.5D,
                zero.getX() + toX, floor, zero.getZ() + 1,
                WIDTH, HEIGHT);
    }

    private static Vec3 at(BlockPos zero, double x, double feetY, double z) {
        return new Vec3(zero.getX() + x, feetY, zero.getZ() + z);
    }
}
