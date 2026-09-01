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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 栅栏圈：平地上最朴素的绕行题——她连这个都走不出来（玩家实测）。
 *
 * <p>这条不是跑酷，是**泛用性的试金石**。没有缺口、没有落差、没有孤台，
 * 只有一圈一格半高的墙和一个一格宽的开口：人一眼就看得出该往哪儿绕。她
 * 走不出来，说明问题不在"某个形状没枚举到"，而在**看得见多远、算不算得
 * 过来**——图上的边再多，搜索预算烧完之前找不到那个开口，外观就是站着
 * 不动。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class FencePenGameTests {
    private static final int DECK = 2;
    /** 穿角是刻意的慢步（0.08 格/tick，快了会撞飞），绕圈又要走远路，
     *  时限按最坏的一趟给足。 */
    private static final int DRIVE_TICKS = 900;

    private FencePenGameTests() {
    }

    /** 开口在她背后那一面：直着走撞墙，得整个绕过去。 */
    static void outOfTheFencePenSheFindsTheGap(GameTestHelper helper) {
        // 铺地不许出 0..12（棋盘步长十三），写出去就是写进邻场的领地。
        // 可这一场又需要绕行余地：她出西口绕外围回东时贴着场西走，只留
        // 两格会偶发一步踏出场沿（rel −0.6 的 dropoff 摔，三十八轮闪过
        // 三回）。两头都要，就把**圈**整体挪两格——圈心 (5,5)→(7,7)，
        // 西侧仍留四格绕行地，场地收进 0..12，考题（开口在背后、必须整
        // 圈绕过去）一字未动。
        //
        // 只挪一格试过（圈心 6,6、西侧三格），偶发踏空当场回来：run2 在
        // rel x=−0.67 摔出场沿。西沿那四格是拿摔换来的，不能省。
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 12; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        // 七乘七的栅栏圈，圈心 (7, 7)；开口在**西**面（x=4, z=7），
        // 而主人在东边——直线方向正对着墙，必须整圈绕过去。
        for (int x = 4; x <= 10; x++) {
            fence(helper, x, 4);
            fence(helper, x, 10);
        }
        for (int z = 5; z <= 9; z++) {
            if (z != 7) {
                fence(helper, 4, z);
            }
            fence(helper, 10, z);
        }

        BridgePatrol.followPatrol(helper,
                at(helper, 7.5D, 6.5D),
                at(helper, 11.5D, 7.5D),
                at(helper, 7.5D, 6.5D),
                1, helper.absolutePos(BlockPos.ZERO).getY() + DECK - 1.0D,
                DRIVE_TICKS, "fence pen");
    }

    /**
     * 边角缺口：唯一的开口在**角上**，主人在斜对角外（玩家原话："栅栏边角
     * 缺口女仆是无法正确识别的"）。
     *
     * <p>角上的缝过得去，这一点要算清楚才不会冤枉她：栅栏柱只占格子中间
     * 0.375–0.625，而斜穿走的是**方块角点**，身位箱在那里是 0.7–1.3，与两
     * 根柱子都不相交，净空还有 0.075 格。玩家走得过去。
     *
     * <p>她走不过去是因为原版禁止"两侧正交邻格都被挡时的斜走"——那条规则
     * 为整块方块而设（不能穿墙角），对只占四分之一的柱子就是误伤。补的那
     * 一族边见 {@code SafeFootingNodeEvaluator.cornerCuts}。
     *
     * <p>（我一度把这道题判成无解并改掉了场景，理由是"对角空隙只有 0.53
     * 格"——那个数算错了：斜穿不从格心走。记在这里。）
     */
    static void outOfAWidePenSheCutsTheCornerGap(
            GameTestHelper helper
    ) {
        // 铺地不许出 0..12：GameTest 棋盘两轴的步长都是十三，写出去就是
        // 写进邻场的领地。同一桩事故这个文件里已经犯过两回（z 方向的记在
        // intoAPenWhoseCornerGapStepsUp 的注释里，x 方向让 run4 连红四轮，
        // 供词是起点解不出锚 note=- path=null underfoot=none）——**带高度
        // 差的越界尤其毒**：它把邻场的地面抬高或压低一格，那场的考题就变
        // 了，而"谁红"只由棋盘上邻位有没有人决定。
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 12; z++) {
                helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
            }
        }
        // 玩家指定的规格：圈放大（九见方，3..11）、**只缺西北那一角**
        // （3,3），主人在缺角外侧的斜对角，她在圈心，自己走出来。
        for (int x = 3; x <= 11; x++) {
            for (int z = 3; z <= 11; z++) {
                boolean edge = x == 3 || x == 11 || z == 3 || z == 11;
                if (edge && !(x == 3 && z == 3)) {
                    fence(helper, x, z);
                }
            }
        }
        BridgePatrol.followPatrol(helper,
                at(helper, 7.5D, 7.5D),
                at(helper, 0.5D, 0.5D),
                at(helper, 7.5D, 7.5D),
                1, helper.absolutePos(BlockPos.ZERO).getY() + DECK - 1.0D,
                DRIVE_TICKS, "wide corner pen");
    }

    /**
     * 极端版：缺角外面**矮一格**（玩家实测点名的那一种）。
     *
     * <p>栅栏常常沿着地形的坎修，缺口外面就是个坎——这不是刁难，是常态。
     * 只连同层的斜穿在这里等于没有：图上那条边压根不存在，她站在缺口里干
     * 看着。落点降一层之后，她要先走到角点、再落下去，所以落点头顶那一格
     * 也得让身子过得去。
     */
    static void outOfAPenWhoseCornerGapDropsAStep(
            GameTestHelper helper
    ) {
        // 圈内与东南半边在 DECK；西北角外整片矮一格。
        // 铺地不许出 0..12（棋盘步长十三）：带高度差的越界会把邻场地面
        // 抬高或压低一格，那场的考题当场改写。同族事故见本文件顶部。
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 12; z++) {
                boolean outsideNorthWest = x < 3 || z < 3;
                helper.setBlock(new BlockPos(x,
                        outsideNorthWest ? DECK - 1 : DECK, z),
                        Blocks.STONE);
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
        BridgePatrol.followPatrol(helper,
                at(helper, 7.5D, 7.5D),
                // 主人在矮一格的那片地上，缺角的斜对角外。
                new Vec3(helper.absolutePos(BlockPos.ZERO).getX() + 0.5D,
                        helper.absolutePos(BlockPos.ZERO).getY() + DECK,
                        helper.absolutePos(BlockPos.ZERO).getZ() + 0.5D),
                at(helper, 7.5D, 7.5D),
                1, helper.absolutePos(BlockPos.ZERO).getY() + DECK - 2.0D,
                DRIVE_TICKS, "corner gap drops");
    }

    /**
     * 反向：从矮的那片地**斜着上一格进圈**（玩家实测："出来可以，但进去
     * 就不行"）。出得来就得进得去——坎这种地形两边都要走。
     */
    static void intoAPenWhoseCornerGapStepsUp(
            GameTestHelper helper
    ) {
        // **先清场**：GameTest 在批次之间不还原世界，这片地上一批留下的
        // 栅栏、屏障会原样待着。本场只写地面（DECK-1/DECK）与场沿
        // （DECK+1/+2），没写到的格子就是上一场的遗产——run4 连红五轮，
        // 读数带的俯视图里她四周整片都是立障（`|`），可这场的栅栏只在
        // 3..11，她站的西北矮地一根都不该有。而"谁红"只由棋盘位置上先
        // 跑的是谁决定，所以它一直伪装成间歇病。
        com.laixia.maidintelligence.gametest.support.world.Arena.clear(
                helper, DECK - 1, DECK + 3);
        // 场地 z 只许铺 0..12：棋盘的 z 步长是十三，写到 z16 就是把方块
        // 写进**邻场的领地**——本场的 z16 屏障墙恰好横贯 z+13 邻场的
        // rel z3 线，把那场栅栏圈唯一的缺角门槛封死（缺角钉 run2/3 稳定
        // 红、run1/4 绿，红绿只由棋盘上谁的邻位有人决定；追了预算、对角
        // 登阶两条歧路才用方块名矩阵拍到 Barrier 的脸）。
        // **x 也只许铺 0..12**，和 z 一样。棋盘两轴的步长都是十三，而这
        // 块地原先 x 铺 -2..16（十九格）——本场 x=16 的屏障墙正好落在
        // x+13 邻场的 rel x=3 线上，那是它栅栏圈的西界、缺角门槛所在。
        // z 方向的同一桩事故下面注释里记着，当时只修了 z，x 漏了：run4
        // 一直红在起点解不出锚（note=- path=null underfoot=none），而
        // 红的是谁只由棋盘上邻位有没有人决定。
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 12; z++) {
                boolean outsideNorthWest = x < 3 || z < 3;
                helper.setBlock(new BlockPos(x,
                        outsideNorthWest ? DECK - 1 : DECK, z),
                        Blocks.STONE);
                // 场沿围一圈两高的屏障。真实地形在场外延绵不断，这里却是
                // 铺地的尽头——旧的测试世界是正常地形，场腔外的岩壁恰好替
                // 场景堵住了这条边；换成平坦虚空后她真的从西沿掉了下去
                // （drops=1），摔在低两格的世界地表上再也爬不回来，考题
                // 就变味了。围墙不是限制她，是补回"这块地没有尽头"。
                boolean rim = x == 0 || x == 12 || z == 0 || z == 12;
                if (rim) {
                    helper.setBlock(new BlockPos(x, DECK + 1, z),
                            Blocks.BARRIER);
                    helper.setBlock(new BlockPos(x, DECK + 2, z),
                            Blocks.BARRIER);
                }
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
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        // 她在矮地上、圈外；主人在圈心——她得斜着上一格钻进来。
        //
        // 起点从 rel(0.5,0.5) 挪进一格：z=0 那一线是场沿屏障（rim），
        // 上面盖着两层 BARRIER，而她脚在 DECK、头到 DECK+1.5——**生成
        // 那一刻人就嵌在屏障里**，净空只有一格。这条一直没暴露，是因为
        // 起点锚解析解不出她那格时会退到邻格去碰运气，歪打正着把她拽了
        // 出来；起点解析改成先认脚下真实支撑之后，这个疏漏就露了原形
        // （run4 稳定红，note=- path=null，四百多次下单一步没挪）。
        // rel(1.5,1.5) 仍是矮地、仍在圈外，考题不变。
        BridgePatrol.followPatrol(helper,
                new Vec3(zero.getX() + 1.5D, zero.getY() + DECK,
                        zero.getZ() + 1.5D),
                at(helper, 7.5D, 7.5D),
                new Vec3(zero.getX() + 1.5D, zero.getY() + DECK,
                        zero.getZ() + 1.5D),
                1, zero.getY() + DECK - 2.0D,
                DRIVE_TICKS, "corner gap steps up");
    }

    /** 缺角与圈内同高（整块平台，圈外矮）：原版自己连得上，这条该绿。 */
    static void intoACornerGapLevelWithThePen(
            GameTestHelper helper
    ) {
        intoTheCornerGap(helper, false, "corner level with pen");
    }

    /**
     * 缺角是**下沉的门槛**：与圈外同高，比圈内矮一格——玩家实测的形状。
     *
     * <p>玩家原话："栅栏围角缺口+高低差仍然无法进入，只能绕栅栏外围靠近主
     * 人"，而且**压根不往缺口去**——那句话说明图里根本没有这条边，不是执行
     * 侧过不去。
     *
     * <p>与上面那条只差缺角那一格的高度，而那一格正是要害：同高时进圈是平
     * 着斜穿，下沉时进圈是**斜着往上迈一格**。净空判据一直按她起步那一层
     * 量，可她是先升上去再横过去的——两侧邻格里"抬高的地形本身"正好撞在箱
     * 底上，这条边每次都被判成过不去。
     *
     * <p>判据也换了：必须**进到圈内**。绕到栅栏外面贴着主人站，离圈心的距
     * 离一样近——按距离判，失败的样子会被判成通过。
     */
    static void intoACornerGapNotchedDownToTheOutside(
            GameTestHelper helper
    ) {
        intoTheCornerGap(helper, true, "corner notched down");
    }

    /** 建圈、放人、跟随；判据只有一条：她进没进圈。 */
    private static void intoTheCornerGap(GameTestHelper helper,
            boolean notched, String what) {
        // 铺地不许出 0..12：GameTest 棋盘两轴的步长都是十三，写出去就是
        // 写进邻场的领地。同一桩事故这个文件里已经犯过两回（z 方向的记在
        // intoAPenWhoseCornerGapStepsUp 的注释里，x 方向让 run4 连红四轮，
        // 供词是起点解不出锚 note=- path=null underfoot=none）——**带高度
        // 差的越界尤其毒**：它把邻场的地面抬高或压低一格，那场的考题就变
        // 了，而"谁红"只由棋盘上邻位有没有人决定。
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 12; z++) {
                boolean pen = x >= 3 && x <= 11 && z >= 3 && z <= 11;
                // 缺角那一格是**下沉的门槛**：与圈外同高，比圈内矮一格。
                boolean high = pen && !(notched && x == 3 && z == 3);
                int top = high ? DECK : DECK - 1;
                // 地要填实。每列只放一个方块的夹具会让"坎底下是空气"，而真
                // 世界里坎底下还是土石；按那种地形推出来的病因，指向的是真
                // 世界里根本不存在的地方（碑见 TightEdges.bracedAt）。
                for (int y = DECK - 3; y <= top; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
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
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        Vec3 heart = at(helper, 7.5D, 7.5D);
        Player owner = helper.makeMockPlayer();
        owner.setPos(heart.x, heart.y, heart.z);
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(zero.getX() + 2.5D, zero.getY() + DECK,
                zero.getZ() + 2.5D);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setHomeModeEnable(false);
        maid.setOwnerUUID(owner.getUUID());
        helper.getLevel().addFreshEntity(maid);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());

        PathwalkTrace trace = new PathwalkTrace(what, zero);
        boolean[] inside = new boolean[]{false};
        double[] nearest = new double[]{99.0D};
        for (int tick = 1; tick <= DRIVE_TICKS; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                trace.sample(at, maid);
                double relX = maid.getX() - zero.getX();
                double relZ = maid.getZ() - zero.getZ();
                if (relX > 3.9D && relX < 11.1D
                        && relZ > 3.9D && relZ < 11.1D) {
                    inside[0] = true;
                }
                nearest[0] = Math.min(nearest[0],
                        Math.hypot(relX - 7.5D, relZ - 7.5D));
                LeadRunner.runToward(owner, heart,
                        at(helper, 9.5D, 9.5D), at);
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new EntityTracker(owner, false),
                                0.75F, 0));
            });
        }
        helper.runAfterDelay(DRIVE_TICKS + 20, () -> {
            trace.dump();
            String diary = BridgePatrol.diaryOf(maid) + "; "
                    + IntentWitness.of(maid);
            maid.discard();
            owner.discard();
            helper.assertTrue(inside[0], what + "：她没进圈——最近只到离圈心 "
                    + String.format("%.2f", nearest[0])
                    + " 格，而绕外围贴着栅栏也能到这个距离，所以判据只问她"
                    + "进没进去；" + diary);
            helper.succeed();
        });
    }

    private static void fence(GameTestHelper helper, int x, int z) {
        helper.setBlock(new BlockPos(x, DECK + 1, z), Blocks.OAK_FENCE);
    }

    /** 模板相对坐标换绝对坐标；y 固定在圈内地面。 */
    private static Vec3 at(GameTestHelper helper, double x, double z) {
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        return new Vec3(zero.getX() + x, zero.getY() + DECK + 1.0D,
                zero.getZ() + z);
    }

    /** 多连钉并行展开（语义不变，墙钟除以连数），见 {@code BridgePatrol.spread}。 */
    @GameTestGenerator
    public static java.util.Collection<net.minecraft.gametest.framework
            .TestFunction> fencePenRuns() {
        java.util.List<net.minecraft.gametest.framework.TestFunction> runs =
                new java.util.ArrayList<>();
        PinSpread.spread(runs, "fencepen", 4, 960,
                "outofthefencepenshefindsthegap",
                FencePenGameTests::outOfTheFencePenSheFindsTheGap);
        PinSpread.spread(runs, "fencepen", 4, 960,
                "outofawidepenshecutsthecornergap",
                FencePenGameTests::outOfAWidePenSheCutsTheCornerGap);
        PinSpread.spread(runs, "fencepen", 4, 960,
                "outofapenwhosecornergapdropsastep",
                FencePenGameTests::outOfAPenWhoseCornerGapDropsAStep);
        PinSpread.spread(runs, "fencepen", 4, 960,
                "intoapenwhosecornergapstepsup",
                FencePenGameTests::intoAPenWhoseCornerGapStepsUp);
        PinSpread.spread(runs, "fencepen", 3, 960,
                "intoacornergaplevelwiththepen",
                FencePenGameTests::intoACornerGapLevelWithThePen);
        PinSpread.spread(runs, "fencepen", 3, 960,
                "intoacornergapnotcheddowntotheoutside",
                FencePenGameTests::intoACornerGapNotchedDownToTheOutside);
        return runs;
    }
}
