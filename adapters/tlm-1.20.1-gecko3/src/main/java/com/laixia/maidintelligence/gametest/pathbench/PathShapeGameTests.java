package com.laixia.maidintelligence.gametest.pathbench;

import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel.Anchor;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
        .AnchorResolver;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel.EdgeVeto;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
        .StrideWeb;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
        .VoxelAstar;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
        .VoxelPath;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 路的**形状**快照：给一片地形，看图铺出什么样的路。
 *
 * <p>寻路的多数毛病是形状不对，而不是她摔了——"空地无障碍三十二格铺成
 * 十七站"这种事，驱动她走一遍照样绿，得靠人去数站数才发现。所以这一族
 * 只问图：铺一次路，把"到不到得了、几站、都用了哪些动作"压成一行字，与
 * 期望比。
 *
 * <p>**不驱动女仆**——不造实体、不跑 tick、不等她走到。一场几毫秒，而驱
 * 动一场要几百 tick。测试章程把这一层叫 L1；本想放在纯 JVM 里，可
 * {@code Bootstrap.bootStrap()} 在这套类路径上会拉起 Forge 的事件总线然
 * 后炸（{@code NetworkEvent.<init>()} 不存在），折腾环境不划算，就借
 * GameTest 的世界来跑——**贵的是驱动，不是服务器**。
 *
 * <p>产出是可比对的一行字，不是红绿：路变了就把新旧摆出来，由人判断是改
 * 进还是回归；期望内联在每场里，失败信息直接给出实得值，照抄即可更新。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class PathShapeGameTests {
    private static final int DECK = 3;

    private static final double WIDE = 0.6D;

    private static final double TALL = 1.8D;

    private PathShapeGameTests() {
    }

    /** 空地直线：任意角该把它拉成两站，中间一个折点都不该有。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathshape")
    public static void anOpenRunKeepsItsShape(GameTestHelper helper) {
        snap(helper, "空地直线",
                new String[]{
                    "###########",
                    "###########",
                    "###########",
                },
                1, 1, 9, 1,
                "reach=true stops=2 moves=WALK");
    }

    /** 两格缺口：走到沿口、跳过去、再走到目标——四站三段。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathshape")
    public static void aGapKeepsItsShape(GameTestHelper helper) {
        snap(helper, "两格缺口",
                new String[]{
                    "####..#####",
                    "####..#####",
                    "####..#####",
                },
                1, 1, 9, 1,
                "reach=true stops=4 moves=WALK,LEAP,WALK");
    }

    /** 柱压走道：一格宽的道正中一根柱。锚点图这一层的诚实答案是**车道
     *  跳**——走到柱前、借侧向车道绕柱一跳、再走到目标；贴边挤过去属于
     *  多边形层（MeshWalk），那是 VoxelAstar 铺不出路时才问的兜底，这里
     *  照的是主引擎的相。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathshape")
    public static void aPostedWalkKeepsItsShape(GameTestHelper helper) {
        snap(helper, "柱压走道",
                new String[]{
                    "...........",
                    "#####|#####",
                    "...........",
                },
                1, 1, 9, 1,
                "reach=true stops=6 moves=WALK,LEAP,WALK");
    }

    /**
     * 铺一次路，把形状压成一行字与期望比。
     *
     * <p>地形按行给：一行一条 z、字符位是 x。{@code #} 实地、{@code .}
     * 空、{@code |} 实地上立一根栅栏柱。
     */
    private static void snap(GameTestHelper helper, String what,
            String[] ground, int fromX, int fromZ, int toX, int toZ,
            String want) {
        // 建场前把**整个本场**清空（GameTest 批次之间不还原世界，台账
        // §5 的原案）。首跑三条全红就是这个病：跑道上摊着上一批的栅栏，
        // 平地快照量出 stops=5 moves=LEAP——她在跳垃圾堆，不是在走路。
        // 范围收在 0..12（棋盘步长十三，出界即邻场领地）；往上清到
        // DECK+5——跳弧顶不过 +3.7，残留地板高于此也连不出可走的边。
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 12; z++) {
                for (int y = DECK - 2; y <= DECK + 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        for (int z = 0; z < ground.length; z++) {
            String row = ground[z];
            for (int x = 0; x < row.length(); x++) {
                char mark = row.charAt(x);
                if (mark == '#' || mark == '|') {
                    helper.setBlock(new BlockPos(x, DECK, z), Blocks.STONE);
                }
                if (mark == '|') {
                    helper.setBlock(new BlockPos(x, DECK + 1, z),
                            Blocks.OAK_FENCE);
                }
            }
        }
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        Anchor start = AnchorResolver.resolve(helper.getLevel(),
                new BlockPos(zero.getX() + fromX, zero.getY() + DECK + 1,
                        zero.getZ() + fromZ),
                WIDE, TALL);
        if (start == null) {
            helper.fail(what + "：起点站不住人");
            return;
        }
        VoxelPath path = VoxelAstar.find(
                new StrideWeb(helper.getLevel(), WIDE, TALL, new EdgeVeto()),
                start,
                new Vec3(zero.getX() + toX + 0.5D, zero.getY() + DECK + 1,
                        zero.getZ() + toZ + 0.5D),
                0.45D);
        String got = shapeOf(path);
        helper.assertTrue(got.equals(want), what + "：路的形状变了\n  期望 "
                + want + "\n  实得 " + got);
        // 收场自清：进场清保护自己，出场扫还别人干净格子。基准家族只
        // 清不扫的那阵子，留下的石板随批次洗牌轮流砸中杆桥与玻璃行
        // （sw188–191 两族反相关地翻红，台账 §5）。
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 12; z++) {
                for (int y = DECK - 2; y <= DECK + 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        helper.succeed();
    }

    /** 把一条路压成一行：到不到得了、几站、都用了哪些动作。 */
    private static String shapeOf(VoxelPath path) {
        if (path == null) {
            return "reach=false stops=0 moves=-";
        }
        StringBuilder moves = new StringBuilder();
        String last = "";
        for (int i = 0; i + 1 < path.length(); i++) {
            String move = path.strideAt(i).move().name();
            if (!move.equals(last)) {
                moves.append(moves.isEmpty() ? "" : ",").append(move);
                last = move;
            }
        }
        return "reach=" + path.reaches() + " stops=" + path.length()
                + " moves=" + (moves.isEmpty() ? "-" : moves);
    }
}
