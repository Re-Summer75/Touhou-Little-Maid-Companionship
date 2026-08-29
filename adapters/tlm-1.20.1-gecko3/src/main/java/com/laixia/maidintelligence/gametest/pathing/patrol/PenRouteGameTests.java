package com.laixia.maidintelligence.gametest.pathing.patrol;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.gametest.support.PathwalkTrace;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 只问规划层一句话：圈里到圈外，**这条路你铺不铺得出来**。
 *
 * <p>玩家实测："女仆无法从反向绕出来，栅栏她似乎会认为能过，所以无论是
 * 跟随还是寻敌，都只能眼睁睁看着"。这句话里有两种完全不同的病，而走完整
 * 流程的钉子分不开它们：路铺不出来（规划层的事）与路铺出来了走不动（执行
 * 侧、意图层、战斗层都有份）。
 *
 * <p>所以这条不驱动、不跟随、不打架——建好圈，把她放进去，向圈外要一条
 * 路，然后**把整条路逐节点打出来**并断言它到得了站。绿了就说明问题在下游；
 * 红了就说明图本身没连通，往上游查。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class PenRouteGameTests {
    private static final int DECK = 2;

    private PenRouteGameTests() {
    }

    /** 七乘七、开口在西面正中；她在圈心，目标在东面圈外。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "penroute", timeoutTicks = 120)
    public static void fromInsideAWidePenAPathOutExists(
            GameTestHelper helper
    ) {
        ground(helper);
        for (int x = 2; x <= 8; x++) {
            fence(helper, x, 2);
            fence(helper, x, 8);
        }
        for (int z = 3; z <= 7; z++) {
            if (z != 5) {
                fence(helper, 2, z);
            }
            fence(helper, 8, z);
        }
        assertRouteOut(helper, 5, 5, 12, 5, "wide pen");
    }

    /** 五乘五、开口在西面正中；目标在斜对角外——最短直线正对着墙。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "penroute", timeoutTicks = 120)
    public static void fromInsideATightPenAPathOutExists(
            GameTestHelper helper
    ) {
        ground(helper);
        for (int x = 3; x <= 7; x++) {
            for (int z = 3; z <= 7; z++) {
                boolean edge = x == 3 || x == 7 || z == 3 || z == 7;
                if (edge && !(x == 3 && z == 5)) {
                    fence(helper, x, z);
                }
            }
        }
        assertRouteOut(helper, 5, 5, 11, 10, "tight pen");
    }

    /**
     * 大圈：同一道题，只把规模放大——这是唯一还没测过的维度。
     *
     * <p>A* 的节点预算按跟随距离算死（约"距离×16"），而我们每个节点吐出的
     * 邻居数是原版的好几倍：下崖、跳跃、斜跳、挤边、檐跳各一族。**圈一大，
     * 预算就可能在她摸到开口之前烧光**，外观正是玩家报的"眼睁睁看着"——
     * 而小圈全绿会把这条完全掩盖掉。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "penroute", timeoutTicks = 120)
    public static void fromInsideALargePenAPathOutExists(
            GameTestHelper helper
    ) {
        for (int x = -8; x <= 24; x++) {
            for (int z = -8; z <= 24; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.GRASS_BLOCK);
            }
        }
        // 十三见方的圈（0..12），开口在西面正中（0,6）；目标在东面圈外。
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 12; z++) {
                boolean edge = x == 0 || x == 12 || z == 0 || z == 12;
                if (edge && !(x == 0 && z == 6)) {
                    fence(helper, x, z);
                }
            }
        }
        assertRouteOut(helper, 6, 6, 18, 6, "large pen");
    }

    /**
     * 圈**完全封死**：这时候铺不出路才是对的。
     *
     * <p>反向的钉子。没有它，"铺不出路"这条断言就分不清是她的毛病还是世界
     * 本来就没路——而玩家的圈到底有没有开口，从截图上我并不总是看得准。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "penroute", timeoutTicks = 120)
    public static void fromInsideASealedPenThereIsNoRouteOut(
            GameTestHelper helper
    ) {
        ground(helper);
        for (int x = 2; x <= 8; x++) {
            fence(helper, x, 2);
            fence(helper, x, 8);
        }
        for (int z = 3; z <= 7; z++) {
            fence(helper, 2, z);
            fence(helper, 8, z);
        }
        EntityMaid maid = penned(helper, 5, 5);
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        helper.runAfterDelay(20, () -> {
            Path path = maid.getNavigation().createPath(
                    helper.absolutePos(new BlockPos(12, DECK + 1, 5)), 0);
            boolean reaches = path != null && path.canReach();
            String shown = PathwalkTrace.describe(path, zero);
            maid.discard();
            helper.assertTrue(!reaches,
                    "封死的圈里居然铺出了到得了的路——那说明图把栅栏当成"
                            + "能过的了：" + shown);
            helper.succeed();
        });
    }

    /**
     * 缺角带落差的圈：沿她真实走过的那条对角线，**逐格**向圈外要路。
     *
     * <p>整流程那条钉子是抖的（四次运行红一次），而抖的东西最难定罪。读数带
     * 说得很清楚：她走到 (5,5) 时一次重规划把八节点的可达路换成了终点在
     * (4,4) 的两节点残路（reach=NO），此后每次重铺都是同一根残桩，人就冻在
     * 那儿。可"那次替换是从哪一格铺出来的、是不是每次都这样"，整流程答不了
     * ——它只跑一条实际轨迹，而轨迹每轮都略有漂移。
     *
     * <p>这条把起点摊开：她可能站的每一格各铺三遍。某一格**稳定**铺不出路，
     * 那是图的病，当场就能定位到是哪一族边没发；每格都能铺、只是偶尔不行，
     * 那是搜索预算或世界快照的病，得往那边查。两种病的修法完全不同，先分开。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "penroute", timeoutTicks = 200)
    public static void everyStepOutOfADroppedCornerPenPlansARoute(
            GameTestHelper helper
    ) {
        droppedCornerPen(helper);
        EntityMaid maid = penned(helper, 7, 7);
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        helper.runAfterDelay(20, () -> {
            StringBuilder bad = new StringBuilder();
            StringBuilder all = new StringBuilder();
            for (int step = 7; step >= 4; step--) {
                for (int again = 1; again <= 3; again++) {
                    maid.moveTo(zero.getX() + step + 0.5D,
                            zero.getY() + DECK + 1.0D,
                            zero.getZ() + step + 0.5D);
                    Path path = maid.getNavigation().createPath(
                            helper.absolutePos(new BlockPos(0, DECK, 0)), 0);
                    all.append("  从 (").append(step).append(',').append(step)
                            .append(") 第").append(again).append("次: ")
                            .append(PathwalkTrace.describe(path, zero))
                            .append('\n');
                    if (path == null || !path.canReach()) {
                        bad.append(" (").append(step).append(',').append(step)
                                .append(")#").append(again);
                    }
                }
            }
            System.out.println(
                    "=== pen route: dropped corner, every start ===\n" + all);
            maid.discard();
            helper.assertTrue(bad.length() == 0,
                    "圈里这些格子铺不出到得了圈外的路：" + bad + "\n" + all);
            helper.succeed();
        });
    }

    /**
     * 站在**栅栏顶上**时，起点必须落在真正托着她脚的那一格。
     *
     * <p>实机实测她会被穿角/登阶送上栅栏顶（rel y=4.50，正是 3 + 1.5 的顶
     * 面）。原版算起点取 {@code floor(y + 0.5)}，那条规则默认地板要么在整数
     * 高度、要么不高过半格——栅栏和墙是一格半，前提当场就破：起点被抬高一
     * 格，整条路从她**头顶正上方的幽灵格**起步。执行侧按自己那一格算出
     * dy=+1，登阶分支连爬四百二十七次也爬不进一个不存在的落脚点。
     *
     * <p>只断言"这条路到得了"是不够的——起点高一格时那条路照样
     * {@code canReach=true}（幽灵格往下掉就是了），红不了，我差点就这么放
     * 过去。所以这里钉的是**起点的 y**：必须等于 {@code floor(她的 y)}。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "penroute", timeoutTicks = 120)
    public static void perchedOnAFenceTopHerRouteStartsWhereSheStands(
            GameTestHelper helper
    ) {
        droppedCornerPen(helper);
        EntityMaid maid = penned(helper, 6, 6);
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        // 北墙上的一根柱子：(4, DECK+1, 3)，碰撞顶面在 DECK+1+1.5。
        helper.runAfterDelay(10, () -> maid.moveTo(
                zero.getX() + 4.5D,
                zero.getY() + DECK + 1 + 1.5D,
                zero.getZ() + 3.5D));
        helper.runAfterDelay(30, () -> {
            double feet = maid.getY() - zero.getY();
            Path path = maid.getNavigation().createPath(
                    helper.absolutePos(new BlockPos(0, DECK, 0)), 0);
            String shown = PathwalkTrace.describe(path, zero);
            boolean grounded = maid.onGround();
            int startY = path == null || path.getNodeCount() == 0
                    ? Integer.MIN_VALUE
                    : path.getNode(0).y - zero.getY();
            System.out.println("=== pen route: fence top ==="
                    + "\n  脚 y=" + feet + " 落地=" + grounded
                    + " 起点 y=" + startY + "\n  " + shown);
            maid.discard();
            helper.assertTrue(grounded && Math.abs(feet - (DECK + 2.5D)) < 0.2D,
                    "夹具没把她放稳在栅栏顶上（脚 y=" + feet + "，落地="
                            + grounded + "）——这条测的东西还没开始就跑偏了");
            helper.assertTrue(path != null && path.canReach(),
                    "栅栏顶上铺不出到得了圈外的路：" + shown);
            helper.assertTrue(startY == Mth.floor(feet),
                    "起点不在托着她脚的那一格：脚 y=" + feet + "，起点 y="
                            + startY + "（该是 " + Mth.floor(feet) + "）——"
                            + "路会从她头顶的幽灵格起步，执行侧从此只会往上"
                            + "爬：" + shown);
            helper.succeed();
        });
    }

    /** 玩家点名的那一种：九见方的圈只缺西北一角，缺角外面还矮一格。 */
    private static void droppedCornerPen(GameTestHelper helper) {
        for (int x = -2; x <= 16; x++) {
            for (int z = -2; z <= 16; z++) {
                boolean outsideNorthWest = x < 3 || z < 3;
                helper.setBlock(new BlockPos(x,
                        outsideNorthWest ? DECK - 1 : DECK, z), Blocks.STONE);
            }
        }
        for (int x = 3; x <= 11; x++) {
            for (int z = 3; z <= 11; z++) {
                boolean edge = x == 3 || x == 11 || z == 3 || z == 11;
                if (edge && !(x == 3 && z == 3)) {
                    fence(helper, x, z);
                }
            }
        }
    }

    /**
     * 七乘七的圈、开口在西面正中：**圈内每一格**都要铺得出到圈外的路。
     *
     * <p>此前只从圈心问过一次，而圈心恰好离缺口最近——那次绿证伪掉了"预算
     * 烧不到缺口"的猜想，可它证伪的是**最容易的那一格**。实机反复出现的
     * `advanced-out` 残桩，供词里她贴着**东墙**（离缺口最远），现场复铺给的
     * 是"1 nodes, canReach=false"：从她那一格一条边都发不出来。
     *
     * <p>我们每个节点吐出的邻居是原版的好几倍（下崖、跳跃、斜跳、挤边、穿
     * 角各一族），A* 的节点预算按跟随距离算死。起点离缺口越远，烧到缺口之
     * 前预算越可能耗尽——而"从圈心问一次"永远看不见这件事。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "penroute", timeoutTicks = 200)
    public static void fromEveryCellInAPenAPathOutExists(
            GameTestHelper helper
    ) {
        ground(helper);
        for (int x = 2; x <= 8; x++) {
            fence(helper, x, 2);
            fence(helper, x, 8);
        }
        for (int z = 3; z <= 7; z++) {
            if (z != 5) {
                fence(helper, 2, z);
            }
            fence(helper, 8, z);
        }
        EntityMaid maid = penned(helper, 5, 5);
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        helper.runAfterDelay(20, () -> {
            StringBuilder bad = new StringBuilder();
            StringBuilder all = new StringBuilder();
            for (int x = 3; x <= 7; x++) {
                for (int z = 3; z <= 7; z++) {
                    maid.moveTo(zero.getX() + x + 0.5D,
                            zero.getY() + DECK + 1.0D,
                            zero.getZ() + z + 0.5D);
                    Path path = maid.getNavigation().createPath(
                            helper.absolutePos(
                                    new BlockPos(12, DECK + 1, 5)), 0);
                    boolean ok = path != null && path.canReach();
                    all.append("  (").append(x).append(',').append(z)
                            .append(")=").append(ok ? "ok" : "NO")
                            .append('/')
                            .append(path == null ? 0 : path.getNodeCount())
                            .append('\n');
                    if (!ok) {
                        bad.append(" (").append(x).append(',').append(z)
                                .append(')');
                    }
                }
            }
            System.out.println(
                    "=== pen route: every cell ===\n" + all);
            maid.discard();
            helper.assertTrue(bad.length() == 0,
                    "圈内这些格子铺不出到得了圈外的路：" + bad + "\n" + all);
            helper.succeed();
        });
    }

    /**
     * 一格宽悬空梁、中段缺一格：**梁上每一格**都要铺得出到对岸的路。
     *
     * <p>整流程那条（梁端掉头）红的样子是 {@code advanced-out} + 现场复铺
     * "1 nodes, canReach=false"——她站在缺口东侧，朝西一条边都发不出来，而
     * 她正是从西边走过来的。同一个形状在四条红里出现过（栅栏圈、梁端掉头、
     * 倒 T、活板门檐），整流程分不清是"这一格发不出边"还是"这一趟运气不
     * 好"。
     *
     * <p>所以把它拍成确定性的：逐格铺，每格三遍。某一格稳定失败就是图的
     * 病，当场定位；每格都能铺就说明病在别处（执行侧或时序）。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "penroute", timeoutTicks = 200)
    public static void alongAGappedBeamEveryCellPlansAcross(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 7; x++) {
            if (x != 4) {
                helper.setBlock(new BlockPos(x, DECK, 2), Blocks.GLASS);
            }
        }
        EntityMaid maid = penned(helper, 1, 2);
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        helper.runAfterDelay(20, () -> {
            StringBuilder bad = new StringBuilder();
            StringBuilder all = new StringBuilder();
            for (int x = 0; x <= 7; x++) {
                if (x == 4) {
                    continue;
                }
                int far = x > 4 ? 0 : 7;
                for (int again = 1; again <= 3; again++) {
                    maid.moveTo(zero.getX() + x + 0.5D,
                            zero.getY() + DECK + 1.0D,
                            zero.getZ() + 2.5D);
                    Path path = maid.getNavigation().createPath(
                            helper.absolutePos(
                                    new BlockPos(far, DECK + 1, 2)), 0);
                    boolean ok = path != null && path.canReach();
                    all.append("  x=").append(x).append("→").append(far)
                            .append(" #").append(again).append('=')
                            .append(ok ? "ok" : "NO").append('/')
                            .append(path == null ? 0 : path.getNodeCount())
                            .append('\n');
                    if (!ok) {
                        bad.append(" x=").append(x).append('#').append(again);
                    }
                }
            }
            System.out.println("=== pen route: gapped beam ===\n" + all);
            maid.discard();
            helper.assertTrue(bad.length() == 0,
                    "梁上这些格子跨不过那个一格缺口：" + bad + "\n" + all);
            helper.succeed();
        });
    }

    /** 建好圈之后：向圈外要一条路，逐节点打出来，并断言它到得了。 */
    private static void assertRouteOut(GameTestHelper helper, int fromX,
            int fromZ, int toX, int toZ, String what) {
        EntityMaid maid = penned(helper, fromX, fromZ);
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        helper.runAfterDelay(20, () -> {
            Path path = maid.getNavigation().createPath(
                    helper.absolutePos(new BlockPos(toX, DECK + 1, toZ)), 0);
            boolean reaches = path != null && path.canReach();
            String shown = PathwalkTrace.describe(path, zero);
            System.out.println("=== pen route: " + what + " ===\n" + shown);
            maid.discard();
            helper.assertTrue(reaches,
                    what + "：圈里到圈外铺不出到得了的路（开口在西面正中，"
                            + "绕过去就是了）——" + shown);
            helper.succeed();
        });
    }

    private static EntityMaid penned(GameTestHelper helper, int x, int z) {
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(zero.getX() + x + 0.5D, zero.getY() + DECK + 1.0D,
                zero.getZ() + z + 0.5D);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        return maid;
    }

    private static void ground(GameTestHelper helper) {
        for (int x = 0; x <= 14; x++) {
            for (int z = 0; z <= 12; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.GRASS_BLOCK);
            }
        }
    }

    private static void fence(GameTestHelper helper, int x, int z) {
        helper.setBlock(new BlockPos(x, DECK + 1, z), Blocks.OAK_FENCE);
    }
}
