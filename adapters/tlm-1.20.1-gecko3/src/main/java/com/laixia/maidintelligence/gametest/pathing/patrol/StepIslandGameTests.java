package com.laixia.maidintelligence.gametest.pathing.patrol;

import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 玩家指定的摔跤复现场：带缺口的竖 L 岛，奔跑跟随，来回两趟，十连过关。
 *
 * <p>场景一比一按玩家口述：左空两格、右空三格夹着一座 L 形小岛——岛上一格
 * 比底部两格高一格，高的在左、低的在右；左侧高台的走面比岛的底部高两格
 * （"从两格高的方向跳过来"就是从这儿起跳）。全程一格宽、全悬空、玻璃。
 *
 * <p>驾驶也按实机：不喂走点，主人**跑**到一头、她用自己的跟随意图追
 * （{@link BridgePatrol#followPatrol}）。一次尝试走两个来回（四条腿），任何
 * 一步跌下桥立刻定罪；整条测试跑十次，十次全过才算过——玩家点名"不要一次
 * 过"。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class StepIslandGameTests {
    /** 抬到干落上限（六格）之外：脚下五格就有地面的话，主人在对岸时她
     *  会干脆跳下去走地面——那是合理行为，却把"她走不走得过去"这条测试
     *  换成了别的问题（实测：她从岛东端一跃而下，摔线当场定罪）。 */
    private static final int DECK = 9;

    /** 一个来回两条腿，玩家要两个来回。 */
    private static final int LEGS_WANTED = 4;

    private static final int DRIVE_TICKS = 900;

    private StepIslandGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "stepisland", timeoutTicks = 960,
            attempts = 10, requiredSuccesses = 10)
    public static void atARunSheFollowsOverTheSteppedIslandBothWaysTwice(
            GameTestHelper helper
    ) {
        // 兜底地面：摔下去落在自家院里，别搅进邻居的测试格。
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        // 高台（左）：x0..1，走面 DECK+3——比岛底高两格的出发侧。
        helper.setBlock(new BlockPos(0, DECK + 2, 2), Blocks.GLASS);
        helper.setBlock(new BlockPos(1, DECK + 2, 2), Blocks.GLASS);
        // 左空两格（x2..3）。竖 L 岛：高的一格在左（x4，走面 DECK+2），
        // 底部两格在右（x5..6，走面 DECK+1）。
        helper.setBlock(new BlockPos(4, DECK + 1, 2), Blocks.GLASS);
        helper.setBlock(new BlockPos(5, DECK, 2), Blocks.GLASS);
        helper.setBlock(new BlockPos(6, DECK, 2), Blocks.GLASS);
        // 右空三格（x7..9）。低台（右）：x10..12，走面 DECK+1。
        for (int x = 10; x <= 12; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.GLASS);
        }

        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        double laneZ = zero.getZ() + 2.5D;
        BridgePatrol.followPatrol(helper,
                new Vec3(zero.getX() + 1.5D,
                        zero.getY() + DECK + 3.0D, laneZ),
                new Vec3(zero.getX() + 12.5D,
                        zero.getY() + DECK + 1.0D, laneZ),
                new Vec3(zero.getX() + 0.5D,
                        zero.getY() + DECK + 3.0D, laneZ),
                LEGS_WANTED,
                // 合法的最低脚面是岛底与低台（DECK+1）；再低半格就是掉下去。
                zero.getY() + DECK + 0.6D,
                DRIVE_TICKS, "step-island follow");
    }

    /**
     * 同一座 L 岛，**高的那一格顶上多一块关着的下半活板门**——玩家实测点名
     * 的那一种，而且只在**连跳**时才现形。
     *
     * <p>玩家原话："站在活板门上面然后跳上上格方块而碰撞跳到侧边虚空，这个
     * 现象只出现在需要连续跳的情况。"
     *
     * <p>要害是那块板把落脚点从整数高度挪到了 {@code y + 0.1875}。她的一跳
     * 里有两件事同时发生：**升到板面**（比整格高出十六分之三）和**跨过缺口**
     * ——而下一跳紧接着就要从这块板上再跨两格、再上一格。中间那一跳落在非
     * 整高的薄板上，起跳的竖直余量和落点的判定都跟着偏，一撞就被弹向侧边；
     * 而这条通道只有一格宽，弹出去就是虚空。
     *
     * <p>为什么必须**连跳**才复现：单独一跳落稳之后她会停一拍、重新对线；
     * 连着的第二跳没有那一拍，带着上一跳的残余水平速度直接起跳，撞点才落在
     * 会把她推向侧面的那个角上。所以这条沿用同一套奔跑跟随、两个来回，绝不
     * 改成单跳的静态场景。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "stepisland", timeoutTicks = 960,
            attempts = 10, requiredSuccesses = 10)
    public static void atARunSheClearsTheLiddedStepWithoutBeingFlungAside(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(0, DECK + 2, 2), Blocks.GLASS);
        helper.setBlock(new BlockPos(1, DECK + 2, 2), Blocks.GLASS);
        helper.setBlock(new BlockPos(4, DECK + 1, 2), Blocks.GLASS);
        // 关着的下半活板门盖在高的那一格上：走面从 DECK+2 抬到 DECK+2.1875。
        helper.setBlock(new BlockPos(4, DECK + 2, 2), Blocks.OAK_TRAPDOOR);
        helper.setBlock(new BlockPos(5, DECK, 2), Blocks.GLASS);
        helper.setBlock(new BlockPos(6, DECK, 2), Blocks.GLASS);
        for (int x = 10; x <= 12; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.GLASS);
        }

        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        double laneZ = zero.getZ() + 2.5D;
        BridgePatrol.followPatrol(helper,
                new Vec3(zero.getX() + 1.5D,
                        zero.getY() + DECK + 3.0D, laneZ),
                new Vec3(zero.getX() + 12.5D,
                        zero.getY() + DECK + 1.0D, laneZ),
                new Vec3(zero.getX() + 0.5D,
                        zero.getY() + DECK + 3.0D, laneZ),
                LEGS_WANTED,
                zero.getY() + DECK + 0.6D,
                DRIVE_TICKS, "lidded step island");
    }

    /**
     * 悬空门板当薄桥、贴脸登阶：玩家第二轮实测点名的形状。
     *
     * <p>"下半活板门会因为某种原因转身导致直接从侧边跳下去，即使路径是正确
     * 的，但行动却没有阻止。"截图里她那一步是 climb：落上门板、转身还没转
     * 完就侧身腾空。
     *
     * <p>要害在**登阶的接近段**：climb 在派发里排在 narrow 之前，一接管，
     * 窄道纪律就轮不到；而接近段走的是裸的 MoveControl 推进——它沿她的
     * **朝向**推，着陆后的转身是渐变的，那条弧在 0.6 宽的门板上就出界；宿
     * 主 MoveControl 还有一记"撞面即跳"的辅助，朝向未对齐时那一跳就是侧
     * 跳。两条路都从"裸推"上走——所以场景必须是**两侧全空的薄桥贴着登阶
     * 块**，宽地面上这条弧无害，测不出。
     *
     * <p>来回跑：去程是"门板上登阶"，回程是"从高处锁步下到门板"——门板只
     * 有一格长，两个方向各考一半。
     */
    /**
     * 玩家逐格口述的复现场（羊毛=1、空=0、下半关门板=2；层1=y1、层2=y2）：
     *
     * <pre>
     * y2:  0 0 0 0 0 0 2 1 0 0 0 0 0 0 0 0
     * y1:  1 1 1 1 0 0 0 1 1 0 0 0 1 1 1 1
     *      x1      x5    x8  x10     x13    x16
     * </pre>
     *
     * <p>西台(x1..4) → 空三格、其中 **x7 的上层悬着一块下半关门板** → 岛
     * （x8 两层高、x9 一层）→ 空三格 → 东台(x13..16)。
     *
     * <p>连跳链：跳三格落上门板(x7) → **紧接着**从门板贴脸登上 x8 顶 → 下
     * x9 → 再跳三格。玩家报的那一幕在门板→x8 顶这一跳："站在活板门上面然
     * 后跳上上格方块而碰撞跳到侧边虚空，这个现象只出现在需要连续跳的情况"
     * ——落板带着腾空残余动量，登阶又是贴脸撞跳，撞上方块侧面就被弹向侧
     * 边，而门板只有一格长、两侧全是虚空。回程还有对偶的一半：从 x8 顶下
     * 到只有一格长的薄门板上再起跳。
     *
     * <p>此前两版夹具都复现失败，错都在把场景洗得太干净：一版把门板盖在
     * 头顶（不是贴腰的薄桥）、一版让她平地走上门板（零残余动量）。教训同
     * 栅栏坎那回：**夹具跟真实现场差一格，病灶就根本不出场**。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "stepisland", timeoutTicks = 960,
            attempts = 10, requiredSuccesses = 10)
    public static void acrossAFloatingLidSheClimbsWithoutSwervingOff(
            GameTestHelper helper
    ) {
        for (int x = 0; x <= 17; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 1; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.WHITE_WOOL);
        }
        helper.setBlock(new BlockPos(8, DECK, 2), Blocks.WHITE_WOOL);
        helper.setBlock(new BlockPos(9, DECK, 2), Blocks.WHITE_WOOL);
        for (int x = 13; x <= 16; x++) {
            helper.setBlock(new BlockPos(x, DECK, 2), Blocks.WHITE_WOOL);
        }
        helper.setBlock(new BlockPos(7, DECK + 1, 2), Blocks.OAK_TRAPDOOR);
        helper.setBlock(new BlockPos(8, DECK + 1, 2), Blocks.WHITE_WOOL);

        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        // 主人的巡逻线**平移到走线外侧两格**（z+2）。实机截图里玩家一直站
        // 在航线侧面的山坡上看她跑：跟随目标斜在航线外，路径重铺的空档里
        // 控制器就斜着拽她一下——正是把她从 0.6 宽的门板上拽出侧沿的那只
        // 手。主人在航线正前方的夹具一次都不拽，咬合验证两轮全绿（修复关
        // 掉也绿），说明斜拉是复现的必要条件之一。到站半径三格够她在自己
        // 的航线上贴近计腿，主人本人悬在场外走位（假人不吃物理）。
        double ownerZ = zero.getZ() + 4.5D;
        BridgePatrol.followPatrol(helper,
                new Vec3(zero.getX() + 2.5D,
                        zero.getY() + DECK + 1.0D, zero.getZ() + 2.5D),
                new Vec3(zero.getX() + 14.5D,
                        zero.getY() + DECK + 1.0D, ownerZ),
                new Vec3(zero.getX() + 2.5D,
                        zero.getY() + DECK + 1.0D, ownerZ),
                LEGS_WANTED,
                zero.getY() + DECK + 0.5D,
                DRIVE_TICKS, "floating lid climb");
    }
}
