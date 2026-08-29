package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 什么算地板、什么算墙、什么算板——立足的物理尺，全包只此一把。
 *
 * <p>从 {@code SafeFootingNodeEvaluator} 按职责拆出（单文件五百行的布局
 * 纪律）：评估器回答"图怎么连"，这里只回答"这一格的碰撞对一个站在格心的
 * 人意味着什么"。规划（节点分类、跳跃扫描）与执行（崖边检测、唇沿判定、
 * 跳线重验）必须用同一把尺——两侧对"什么算地板"答案不一，她就会被推进
 * 缺口或者被自己的安全机构钉死。
 */
public final class FootingRule {
    /** 自身碰撞顶面不高于这个值才算"站得住自己"。半格：台阶的高度。 */
    static final double STANDABLE_TOP = 0.5D;

    private FootingRule() {
    }

    /**
     * 这一格的碰撞接不接得住站在格子中心的人：碰撞非空，且**盖得住中心**。
     *
     * <p>盖住中心这一条是把竖板和地板分开的那把尺：石头、台阶、楼梯、关着
     * 的活板门都盖住中心；开着的活板门/门是贴着格边的一条竖片，她的重心落
     * 在格子中心时脚下什么都没有——刷怪塔骗的就是这一步。用包围盒判断，
     * 接受楼梯这类 L 形的近似。
     */
    public static boolean coversCenter(BlockGetter level, BlockPos pos) {
        return coversCenter(level.getBlockState(pos)
                .getCollisionShape(level, pos));
    }

    /** 同一把尺的形状版：碰撞的包围盒在水平面上盖不盖得住格子中心。 */
    public static boolean coversCenter(VoxelShape shape) {
        if (shape.isEmpty()) {
            return false;
        }
        return shape.min(Direction.Axis.X) <= 0.5D
                && shape.max(Direction.Axis.X) >= 0.5D
                && shape.min(Direction.Axis.Z) <= 0.5D
                && shape.max(Direction.Axis.Z) >= 0.5D;
    }

    /**
     * 这一格里盖得住格心的碰撞顶面有多高；没有则负无穷。
     *
     * <p>把"下面有没有东西"升级成"下面的东西有多高"的那把尺：顶面贴着走面
     * （半格内）是平路，不用跳也不该跳；顶面低出脚面半格以上是**低洼**——
     * 沉在缺口里的关门板、浅坑——弧线从上面过是合法跑酷。只用布尔的"有没
     * 有"，关着的门板沉在缺口里就把跳跃线整个掐死：不能飞（规划不连线）也
     * 不能走（孤板连不成路），她两头不是——玩家实测：关着不跳、开了反而能跳。
     */
    public static double coveringTopAt(BlockGetter level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos)
                .getCollisionShape(level, pos);
        if (!coversCenter(shape)) {
            return Double.NEGATIVE_INFINITY;
        }
        return pos.getY() + shape.max(Direction.Axis.Y);
    }

    /**
     * 格心一根**细高柱**（滴水石锥、末地烛、避雷针这类）：碰撞盖住格心、
     * 顶面高过半格、水平却不满格。宽度门槛取 0.8：床（1.0）、箱子
     * （0.875）是满面的台子，不进这类。
     *
     * <p>**这不再是禁令，只是"窄"的事实**。第一版拿它当三道闸（分类判死、
     * 晋升拒绝、唇沿盲跳），玩家实测判过头：跑酷图里柱顶就是要踩的中继，
     * "有不完整方块不代表不可以站或过"。她当年摔不是站不住——柱顶
     * onGround 物理成立——是图不认站位、自救在柱旁乱舞把她蹭下去。现在
     * 站位交给 {@link #perchTop} 认、落不落得上交给扫掠仿真终审，这把尺
     * 只剩仿真侧标注"窄立足"（{@code SweptMotion} 的 PERCHED）一个用途。
     */
    public static boolean slimPillar(VoxelShape shape) {
        if (shape.isEmpty()
                || !coversCenter(shape)
                || shape.max(Direction.Axis.Y) <= STANDABLE_TOP) {
            return false;
        }
        double wide = Math.max(
                shape.max(Direction.Axis.X) - shape.min(Direction.Axis.X),
                shape.max(Direction.Axis.Z) - shape.min(Direction.Axis.Z));
        return wide < 0.8D;
    }

    /**
     * 这一格的碰撞是**要跳才上得去、但站得住人的高台面**：盖住格心、顶面
     * 高过半格、不超过一格二（跳弧顶一格二五之内）。石锥 0.69、末地烛
     * 1.0、床 0.56、箱子 0.875 都是；栅栏与墙（1.5）跳不上去，不算。
     *
     * <p>与 {@link #selfFloor}（顶不高于半格的矮地板，走着就能上）合起来
     * 盖满"这格自己能托住脚"的全谱——物理的尺，不是方块名的枚举。**满格
     * 方块除外**：石头羊毛的碰撞同样"盖住格心、顶面 1.0"，可那是墙体不是
     * 台面——第一版漏了这条，全图的实心格被改判可走，A* 当场在墙里铺路
     * （一轮十四红，碑）。
     *
     * <p>上限**严格小于一格**：顶恰在一格整的柱（末地烛），站顶的人脚踩
     * y=1.0、按方块归属已在柱的**上格**里——那份立足由上格的晋升表达
     * （{@code CellClassifier.standableBelow} 的例外），柱格自己不当台
     * 面。归属律一句话：顶不到一格，节点在柱格；顶到一格，节点在上格。
     */
    static boolean perchTop(VoxelShape shape) {
        return !shape.isEmpty()
                && !Block.isShapeFullBlock(shape)
                && coversCenter(shape)
                && shape.max(Direction.Axis.Y) > STANDABLE_TOP
                && shape.max(Direction.Axis.Y) < 1.0D;
    }

    /**
     * 这一格自己就是贴脚的矮地板（关着的下半活板门、地毯这类）：碰撞非空、
     * 顶面不高于半格、盖得住格心。
     */
    static boolean selfFloor(VoxelShape shape) {
        return !shape.isEmpty()
                && shape.max(Direction.Axis.Y) <= STANDABLE_TOP
                && coversCenter(shape);
    }

    /**
     * 站在 {@code (x, feetY, z)} 这一点上，托着她的东西顶面有多高；没有则
     * 负无穷。
     *
     * <p>**"脚下半格那一格"是错的问法。**站在普通方块上脚面是整数，减半格
     * 正好落到脚下那一格；可站在**下半活板门、地毯**这类矮地板上时脚面是
     * 小数（门板 0.1875），减半格会掉到门板**下面**那一格去——那儿通常是
     * 虚空。于是探针在檐的正中间就报"前面是悬崖"，她当场起跳，起跳点与瞄
     * 点全错，外观是"站在门板上必然转身往侧边跳下去"（玩家实测，逐次必现）。
     *
     * <p>所以两支都要问：这一格自己是不是矮地板（脚面因此才是小数），或者
     * 下面那一格顶不顶得住。台阶（半格）两种问法都对，所以这个错一直没被
     * 台阶暴露出来。
     */
    static double footingUnder(
            BlockGetter level,
            double x,
            double feetY,
            double z
    ) {
        BlockPos self = BlockPos.containing(x, feetY + 0.05D, z);
        VoxelShape shape = level.getBlockState(self)
                .getCollisionShape(level, self);
        if (selfFloor(shape)) {
            return self.getY() + shape.max(Direction.Axis.Y);
        }
        return coveringTopAt(level,
                BlockPos.containing(x, feetY - 0.5D, z));
    }

    /**
     * 这一格站得住人：脚下有盖住格心的地板，**或者它自己就是矮地板**。
     *
     * <p>第二支不能省。悬空的关门板、地毯这类"自己就是地板"的格子脚下往往
     * 是虚空——只问脚下那格就会把它们全判成不能落脚，而图层认它们、执行侧
     * 不认，边连出来却没人敢跳（玩家实测：门板嵌在上一格的下半，理论上跳得
     * 上去，她却认为不可以）。规划与执行必须同一把尺。
     */
    static boolean standable(BlockGetter level, BlockPos pos) {
        return coversCenter(level, pos.below())
                || selfFloor(level.getBlockState(pos)
                        .getCollisionShape(level, pos));
    }

    /**
     * 这一格是贴边竖片（开着的活板门/门这类）：有碰撞但盖不住格心——身子
     * 从旁边过毫无阻碍，格心也站得下人。栅栏柱、玻璃板、墙这些盖住格心的
     * 不算。
     */
    static boolean edgePlate(VoxelShape shape) {
        return !shape.isEmpty() && !coversCenter(shape);
    }

    /**
     * 托着站在此格的人的支撑，水平方向的**短边**有多宽：横放末地烛的杆
     * 面 0.25、下半门板 1.0、满块 1.0；本格自身有可站矮碰撞（顶不高于一
     * 格）用自己的，否则用脚下那格的。没有支撑给 1.0——悬空归别的判官
     * 管，这把尺只回答"面有多窄"。
     *
     * <p>它存在的理由：到位判据、切角松量这些数都是按 0.6 以上的走面校
     * 准的，在四分之一格的杆面上同样的松量就是切进虚空的斜线（玩家实测
     * 点名：慢速在烛桥转角走出四十五度、从旁边掉下去；奔跑反而靠惯性压
     * 着杆过）。
     */
    public static double supportBreadth(BlockGetter level, BlockPos pos,
            double feetY, double slack) {
        VoxelShape self = level.getBlockState(pos)
                .getCollisionShape(level, pos);
        // **顶面贴脚的才是她的支撑。**格内有个细碰撞不等于她站在细物上
        // ——竖烛立在站位块顶时她站的是块、烛只是身旁的柱；按"格内有矮
        // 碰撞就取其窄度"判，判到半径被错收到贴边站位够不着的程度（烛旁
        // 中转钉实测：距格心 0.19 对收紧后的 0.175，三百二十五次小跳原地
        // 空转）。
        if (!self.isEmpty() && coversCenter(self)
                && Math.abs(pos.getY() + self.max(Direction.Axis.Y) - feetY)
                        <= slack) {
            return Math.min(
                    self.max(Direction.Axis.X) - self.min(Direction.Axis.X),
                    self.max(Direction.Axis.Z) - self.min(Direction.Axis.Z));
        }
        VoxelShape below = level.getBlockState(pos.below())
                .getCollisionShape(level, pos.below());
        if (!below.isEmpty() && coversCenter(below)
                && Math.abs(pos.below().getY()
                        + below.max(Direction.Axis.Y) - feetY) <= slack) {
            return Math.min(
                    below.max(Direction.Axis.X)
                            - below.min(Direction.Axis.X),
                    below.max(Direction.Axis.Z)
                            - below.min(Direction.Axis.Z));
        }
        return 1.0D;
    }

    /** 挤边身位的半宽：她的包围盒正好 0.6。 */
    private static final double SQUEEZE_HALF = 0.30D;

    /** 挤边候选点离格心的偏移：栅栏柱（边在 0.625）两侧留半分缝正好过。 */
    private static final double SQUEEZE_REACH = 0.43D;

    /**
     * 格心被占但边上挤得过去的格子，给出能塞下一个身位的**贴边点**；没有
     * 返回 null。玩家过栅栏柱就是这么走的：柱只占中间四分之一，贴边那条窄
     * 带加上外溢到邻格（甚至虚空上方）的身位正好过人——格级的图看不见这
     * 条缝，判定要下到真实碰撞形状的分辨率（体素化，玩家点名）。
     *
     * <p>候选点在两轴 ±0.425 的边带与四角；每个候选做 0.6 宽、两格高的身位
     * 箱对本格与八邻真实 VoxelShape 的相交测试，脚下那格还要有承托。
     */
    static Vec3 squeezePoint(BlockGetter level, BlockPos cell) {
        return squeezePoint(level, cell, new Vec3(
                cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D));
    }

    /**
     * 方向感知版：几条窄带都站得住时，选离 {@code toward} 最近的那条——
     * 助跑要的是柱子朝落点那一侧的窄带，瞄背面的等于把自己抵在柱上。
     */
    static Vec3 squeezePoint(BlockGetter level, BlockPos cell, Vec3 toward) {
        double[] offsets = {-SQUEEZE_REACH, 0.0D, SQUEEZE_REACH};
        Vec3 best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (double ox : offsets) {
            for (double oz : offsets) {
                if (ox == 0.0D && oz == 0.0D) {
                    continue;
                }
                double px = cell.getX() + 0.5D + ox;
                double pz = cell.getZ() + 0.5D + oz;
                if (!bodyFits(level, cell, px, pz)
                        || !standsOnSomething(level, px, cell.getY(), pz)) {
                    continue;
                }
                double distSq = (px - toward.x) * (px - toward.x)
                        + (pz - toward.z) * (pz - toward.z);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = new Vec3(px, cell.getY(), pz);
                }
            }
        }
        return best;
    }

    /**
     * 过被占格的**侧向车道**：行进轴两旁 ±0.43 的两条窄带，入口、柱旁、
     * 出口三个位置都塞得下身位才算一条真车道。正面窄带是陷阱——能站，
     * 但下一步就抵在柱面上（读数带实测：滑挤成九十 tick 的死舞）。返回
     * 车道的侧向坐标（世界系），没有车道返回 NaN；两条都通选离
     * {@code mobPerp}（她当下的侧向坐标）近的。
     */
    static double squeezeLane(
            BlockGetter level,
            BlockPos cell,
            boolean alongX,
            double mobPerp
    ) {
        double center = 0.5D + (alongX ? cell.getZ() : cell.getX());
        double best = Double.NaN;
        // 先试她当前所在的那一侧：等距时来回换边会让对齐左右摇摆。
        int preferred = mobPerp >= center ? 1 : -1;
        for (int pick = 0; pick <= 1; pick++) {
            int side = pick == 0 ? preferred : -preferred;
            double perp = center + side * SQUEEZE_REACH;
            boolean clear = true;
            for (double para = -SQUEEZE_REACH; para <= SQUEEZE_REACH + 0.01D;
                    para += SQUEEZE_REACH) {
                double px = alongX ? cell.getX() + 0.5D + para : perp;
                double pz = alongX ? perp : cell.getZ() + 0.5D + para;
                if (!bodyFits(level, cell, px, pz)
                        || !standsOnSomething(level, px, cell.getY(), pz)) {
                    clear = false;
                    break;
                }
            }
            if (clear && (Double.isNaN(best)
                    || Math.abs(perp - mobPerp) < Math.abs(best - mobPerp))) {
                best = perp;
            }
        }
        return best;
    }

    /**
     * 从这儿走到那儿，身子过不过得去——沿直线按真实碰撞形状扫几个身位。
     *
     * <p>格级的图答不了这个问题：开着的活板门、开着的门都是**贴边的整格高
     * 竖片**，格心空着、脚下有地板，图上读出来是"能站能进"，可她的身子从那
     * 一面横穿过不去。玩家实测就是被这么钉住的（读数带：顶在门板西面 4.70
     * 处，note=walk，看门狗连咬）。
     *
     * <p>下沿是要害。**合法的地板本身会占掉格底那一层**——关着的下半门板
     * 占 0..0.19、台阶占 0..0.5——身位箱贴着格底起算的话，"走到门板上"会被
     * 读成"身子过不去"，门槛跳当场变成到处乱开的机枪（实测：悬空门板线 5
     * 连跳、V 形唇沿被甩飞）。所以下沿由调用方按问题给：问"平着过得去吗"
     * 传 {@link #STANDABLE_TOP} 之上一点，问"抬一格过得去吗"传一格之上一点。
     *
     * @param bottomY 身位箱下沿的绝对高度
     */
    public static boolean walkLineClear(
            BlockGetter level,
            double fromX,
            double fromZ,
            double toX,
            double toZ,
            double bottomY
    ) {
        // 采样按**距离**给，不是固定段数：定长六段在一格步上够密（间距
        // 0.17），到了任意角的长直线上就成了筛子——七格四的线间距 1.23
        // 格，0.25 宽的栅栏柱整根从缝里漏过去，图连出一条穿墙的直线（栅
        // 栏圈实测：她接到黄线就自我作废，note 卡在 stale）。四分之一格
        // 一采，比身位箱窄得多，漏不掉东西。
        double span = Math.hypot(toX - fromX, toZ - fromZ);
        int steps = Math.max(6, (int) Math.ceil(span / 0.25D));
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            if (!bodyFitsAt(level,
                    fromX + (toX - fromX) * t,
                    bottomY,
                    fromZ + (toZ - fromZ) * t)) {
                return false;
            }
        }
        return true;
    }

    /** 指定下沿的身位箱对真实碰撞形状的相交测试。 */
    private static boolean bodyFitsAt(
            BlockGetter level,
            double px,
            double bottomY,
            double pz
    ) {
        AABB body = new AABB(
                px - SQUEEZE_HALF, bottomY, pz - SQUEEZE_HALF,
                px + SQUEEZE_HALF, bottomY + 1.9D, pz + SQUEEZE_HALF
        );
        BlockPos cell = new BlockPos((int) Math.floor(px),
                (int) Math.floor(bottomY), (int) Math.floor(pz));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos at = cell.offset(dx, dy, dz);
                    VoxelShape shape = level.getBlockState(at)
                            .getCollisionShape(level, at);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    for (AABB box : shape.toAabbs()) {
                        if (box.move(at).intersects(body)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean bodyFits(
            BlockGetter level,
            BlockPos cell,
            double px,
            double pz
    ) {
        AABB body = new AABB(
                px - SQUEEZE_HALF, cell.getY() + 0.05D, pz - SQUEEZE_HALF,
                px + SQUEEZE_HALF, cell.getY() + 1.95D, pz + SQUEEZE_HALF
        );
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos at = cell.offset(dx, dy, dz);
                    VoxelShape shape = level.getBlockState(at)
                            .getCollisionShape(level, at);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    for (AABB box : shape.toAabbs()) {
                        if (box.move(at).intersects(body)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /** 跳弧顶（一格二五）之下、跳得上去的台面上限。 */
    static final double PERCH_TOP = 1.2D;

    /**
     * 格心被**走不过去的高碰撞**占着（柱、墙、烛）——这种格走不到格心，
     * 穿行要走贴边窄带（挤缝、车道那一族的入口判据）。
     *
     * <p>门槛是**走**的极限（半格），不是跳的极限：末地烛（1.0）既是跳
     * 得上的台面（{@link #perchTop}）**又是**要贴边绕的柱——一格两种真
     * 实，两把尺并存，谁也不吞谁。曾把这里抬到 1.2 想"可跳上的都不算
     * 柱"，挤缝穿行当场对烛失灵；瞄点的取舍另有一把尺（{@code aimPoint}
     * 只对跳不上去的真柱找贴边）。
     */
    public static boolean tallAtCenter(BlockGetter level, BlockPos cell) {
        VoxelShape shape = level.getBlockState(cell)
                .getCollisionShape(level, cell);
        return coversCenter(shape)
                && shape.max(Direction.Axis.Y) > STANDABLE_TOP;
    }

    /**
     * 走向一个格子该瞄哪：默认瞄格心；只有格心被**跳也上不去的**高物
     * （栅栏柱这类，高过 {@link #PERCH_TOP}）占着而贴边又塞得下身位时，
     * 才瞄贴边点。跳得上的台面（石锥顶、烛顶）瞄格心——那是要踩上去的
     * 立足，对着它找贴边窄带会让挤缝逻辑把她从中继柱旁蹭下去（正向钉
     * Leg2 实测 note=squeeze 侧摔）。找不到贴边点也退回格心——瞄点只是
     * 优化，可走性另有判官。
     */
    static Vec3 aimPoint(BlockGetter level, BlockPos cell) {
        if (unclimbableAtCenter(level, cell)) {
            Vec3 strip = squeezePoint(level, cell);
            if (strip != null) {
                return strip;
            }
        }
        return new Vec3(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D);
    }

    /**
     * 格心被**跳也上不去的高碰撞**占着（栅栏柱、墙）——挤缝、车道、贴边
     * 瞄点那一族的入口判据。跳得上的台面（石锥 0.69、烛顶 1.0）不算：那
     * 是要踩上去的立足，挤缝逻辑对它做的每一次"贴边侧移"都是把她从柱顶
     * 往缝里推（中继钉十连实测：两成的回程被走段挤缝推出侧沿）。
     */
    public static boolean unclimbableAtCenter(BlockGetter level,
            BlockPos cell) {
        VoxelShape shape = level.getBlockState(cell)
                .getCollisionShape(level, cell);
        return coversCenter(shape)
                && shape.max(Direction.Axis.Y) > PERCH_TOP;
    }

    /** 贴边点脚下要有承托：点所在的柱子里，下一格顶面得贴着脚。 */
    private static boolean standsOnSomething(
            BlockGetter level,
            double px,
            int y,
            double pz
    ) {
        BlockPos under = BlockPos.containing(px, y - 0.5D, pz);
        VoxelShape shape = level.getBlockState(under)
                .getCollisionShape(level, under);
        return !shape.isEmpty()
                && under.getY() + shape.max(Direction.Axis.Y) >= y - 0.1D;
    }
}
