package com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 自有 A*：锚点图上的最短路，预算封顶，前沿诚实。
 *
 * <p>不继承原版的任何寻路件——原版搜索绑死在整格节点上，亚格立足在它眼
 * 里没有名字，一切补丁由此而生（玩家定案："不需要任何原版的寻路内容"）。
 * 主循环是 **Lazy Theta***：走边入队时先假设祖父直线可视、按任意角直线
 * 代价指路，出队时才兑付视线检查（延迟验证），验伪退回最优走邻——直线
 * 的短从搜索里就参与选路，不是事后拉直；跳与降带着初速合同，不参与任意
 * 角。出口再过一道拉直（对 Theta* 输出多为空转，兜的是验伪回退的次优段）。
 *
 * <p>**前沿要诚实**：预算耗尽或图真到不了时，返回"离目标最近的已展开
 * 点"的路径并把 {@code reaches=false} 写明白；而前沿若就是起点脚下，返
 * 回 null——一节点残路是老图的顽疾（advanced-out 空转整场），这里从根
 * 上不产它。
 */
public final class VoxelAstar {
    /** 展开预算。绕行题吃的就是这个数（栅栏圈的远路预算烧完就只剩部
     *  分路径，她停在离主人最近的墙前）；多锚点让节点近乎翻倍，翻倍的
     *  底气来自 jumpOk/lineWalkable 记账。 */
    private static final int VISIT_BUDGET = 4096;

    /** 一次规划的**标称**开销：占位按它扣不按最坏值——按最坏占位等于
     *  名额砍到四个，几只无路的就吃干全服（抬高石实测钉柱一百七十
     *  tick）。 */
    private static final int PLAN_COST = 256;

    /** 全 tick 预算：一 tick 内**全体**女仆共享的展开额度。玩家会养几
     *  十只，错峰之外还要有硬顶——超额者本 tick 沿旧路走，下 tick 再
     *  排，规划峰值摊成平线。服务器 tick 单线程，静态无锁即安全。 */
    private static final int TICK_BUDGET = 8192;
    private static long budgetStamp = Long.MIN_VALUE;
    private static int budgetSpent;

    /** 全 tick 的**时间**硬顶。按次数放行会把 69ms 与 1ms 的规划当同一
     *  个名额（n=24 实测两三次灾难规划就吃穿 tick）——按时间记账，次数
     *  额度照旧。第一版 5ms 饿死过人（倒 T lastPlan=76t：按实体顺序下
     *  单，饿的总是同几只——那会儿还没有"没路必放行"的保底，如今有）。
     *  15ms 时代栏边 tick 被规划峰值顶到二十七（sealed p95）；8ms 试
     *  过一轮，尖刺压到十四但拾取吞吐砍半（fenced 收货 235→101）、战
     *  斗对双红——12ms 是两头都站得住的中间值。 */
    private static final long NANO_BUDGET = 12_000_000L;
    private static long nanosSpent;
    private static long lastDenyLog = Long.MIN_VALUE;
    private static long lastBrakeLog = Long.MIN_VALUE;

    private VoxelAstar() {
    }

    /** 兜底（mesh 雕刻）的耗时也计入本 tick 的时间账：强闯者烧的钱下
     *  一单的 tryReserve 看得见，全场其他人自动让路。 */
    public static void charge(long nanos) {
        nanosSpent += nanos;
    }

    private static void rollTick(long gameTime) {
        if (gameTime != budgetStamp) {
            budgetStamp = gameTime;
            budgetSpent = 0;
            nanosSpent = 0L;
        }
    }

    /** 本 tick 时间预算的余额（保底两毫秒）：生产规划的墙钟硬顶。
     *  15ms 账是花完才记的后置账，拦不住正在爆炸的这一单（sealed
     *  臂 p95 190ms 单发尖刺）——把余额传进 find 当硬顶，良图无
     *  感（常规规划 1~3ms），毒图交诚实前沿分段推进；完备性钉
     *  走四参版不受管。 */
    public static long headroom(long gameTime) {
        rollTick(gameTime);
        return Math.max(2_000_000L, NANO_BUDGET - nanosSpent);
    }

    /** 在这一 tick 的全局预算里给一次规划占位；占不到就别铺。 */
    public static boolean tryReserve(long gameTime) {
        rollTick(gameTime);
        if (budgetSpent + PLAN_COST > TICK_BUDGET
                || nanosSpent >= NANO_BUDGET) {
            // 拒单取证（每百 tick 至多一声）：长冻结全靠这行对质——是
            // 纳秒帐还是名额帐、被吃到多少。zigzag 案排除到最后才指到
            // 配额头上，因为它此前一言不发。
            // MIN_VALUE 初值直接进减法会溢出成永远压声（confess 同案）。
            if (gameTime - lastDenyLog >= 100L
                    || lastDenyLog == Long.MIN_VALUE) {
                lastDenyLog = gameTime;
                com.mojang.logging.LogUtils.getLogger().warn(
                        "[voxel-quota] denied t={} spent={}ms plans={}",
                        gameTime, nanosSpent / 1_000_000L,
                        budgetSpent / PLAN_COST);
            }
            return false;
        }
        budgetSpent += PLAN_COST;
        return true;
    }

    /**
     * 从 {@code start} 找到 {@code goal} 附近的路。
     *
     * @param acceptWithin 目标判定半径：锚点距 goal 水平在此之内算到
     */
    public static VoxelPath find(
            StrideSupplier edges,
            Anchor start,
            Vec3 goal,
            double acceptWithin
    ) {
        return find(edges, start, goal, acceptWithin, 0L);
    }

    public static VoxelPath find(
            StrideSupplier edges,
            Anchor start,
            Vec3 goal,
            double acceptWithin,
            long hardCap
    ) {
        // 时间账在入口记：搜索自己花了多久，本 tick 的时间闸就按它关。
        long clock = System.nanoTime();
        try {
            return search(edges, start, goal, acceptWithin, hardCap);
        } finally {
            nanosSpent += System.nanoTime() - clock;
        }
    }

    /** 单次搜索的时间刹车：到点就交诚实前沿（展开预算是节点上限，可
     *  节点有单价——毒图烧满 4096 节点一次 254ms，sw183 n=24 实测；时
     *  间闸拦得住下一单、拦不住正在爆炸的这单）。 */
    private static final long SEARCH_NANO_CAP = 8_000_000L;

    /** 刹车前的**确定性地板**：不满 512 节点不许刹。纯时间刹车让同图
     *  不同轮交出不同的路（玻璃行道案：贪心前沿把她引进爬不出的口袋）；
     *  测试地形图总量都在几百节点内，给足即恢复确定，大世界超过才轮到
     *  时间闸。 */
    private static final int BRAKE_FLOOR = 512;

    private static VoxelPath search(
            StrideSupplier edges,
            Anchor start,
            Vec3 goal,
            double acceptWithin,
            long hardCap
    ) {
        record Open(Anchor at, double f) {
        }
        Map<Key, Double> gScore = new HashMap<>();
        Map<Key, Visit> seen = new HashMap<>();
        PriorityQueue<Open> open = new PriorityQueue<>(
                (a, b) -> Double.compare(a.f, b.f));

        Key startKey = key(start);
        gScore.put(startKey, 0.0D);
        seen.put(startKey, new Visit(start, null, null, null, null, 0.0D));
        open.add(new Open(start, heuristic(start, goal)));

        Anchor closest = start;
        double closestH = heuristic(start, goal);
        int visited = 0;
        long began = System.nanoTime();

        while (!open.isEmpty() && visited < VISIT_BUDGET) {
            // 每 8 个节点看一次表：毒图单节点 0.3ms，32 步粒度的刹车
            // 惯性就是十毫秒超程（sealed p95 的另一成分）。地板期
            // 内另设三倍时间的硬顶：地板保的是测试图的确定性，那些图
            // 节点便宜、永远碰不到这条线；栅栏格的弹道图单节点贵，512
            // 个保底节点一次上百毫秒（sealed 臂实测 plan 150ms/次），
            // 地板反成了单价放大器。
            if ((visited & 7) == 7
                    && System.nanoTime() - began > SEARCH_NANO_CAP
                    && (visited >= BRAKE_FLOOR
                            || (hardCap > 0L
                                    && System.nanoTime() - began
                                            > hardCap))) {
                // 刹车开火要自报（限频）：它是全引擎唯一按**墙钟**做决
                // 定的地方——宿主一忙它就刹得更早、半截更短，载荷由此
                // 漏进按 tick 计数的世界。杆桥悬案的最后一个嫌疑人。
                if (began - lastBrakeLog > 5_000_000_000L
                        || lastBrakeLog == Long.MIN_VALUE) {
                    lastBrakeLog = began;
                    com.mojang.logging.LogUtils.getLogger().warn(
                            "[voxel-brake] visited={} ms={}", visited,
                            (System.nanoTime() - began) / 1_000_000L);
                }
                break;
            }
            Anchor here = open.poll().at;
            Key hereKey = key(here);
            visited++;

            // Lazy Theta* 的延迟兑付：入队时假设"祖父直线可视"记下的捷
            // 径父，在出队这一刻才真验视线。验伪就把这个节点**整个撤
            // 销**（把 g 也收回去）——它的直边父路径还在队里排着，会以
            // 老实的折线身份重新入场。标准 setVertex 要反查全部邻边，
            // 而我们的边生成带扫掠仿真，一次反查贵过十次搜索步（实测
            // 单 tick 四十秒过载的另一半来源）。
            Visit hv = seen.get(hereKey);
            // 撤销过的节点在队列里还留着旧条目——弹到它时账已销，跳过。
            // （不跳就是对着空账本问父亲：实机整轮崩在这一行。）
            if (hv == null) {
                continue;
            }
            Visit hvParent = hv.cameFrom() == null ? null
                    : seen.get(hv.cameFrom());
            // **凡是走了捷径的都要验**，不设长度下限。原先只验超过
            // 1.6 格的直线，转角切线恰好 1.41 格——从来没被验过就当合法
            // 用了，她于是从杆桥的转角斜着切出去（横烛桥实测：z 从 2.50
            // 漂到 2.98，人已在两条杆之间的空处，t=42 坠落）。捷径的判据
            // 是"父亲不是脚下这一步的父亲"，与它多长无关。
            // 捷径一律兑付，不设任何豁免。
            //
            // 曾放过"共线免验"：祖父→父→本节点在一条直线上就当成两条已
            // 验走边的接续。可它只看坐标，**没问中间那一段是不是走**
            // ——链路若是"祖父 跳→ 父 走→ 本节点"，共线同样成立，于是
            // 一条本该起跳的缺口被当作直线放行，她照着走下去就是摔（台
            // 阶岛、门板台阶、浮空门板三族成片翻红，全是这个形状）。性
            // 能由记账（jumpOk/lineWalkable 缓存）补回，不靠豁免。
            if (hvParent != null && hv.stepFrom() != null
                    && !hv.cameFrom().equals(hv.stepFrom())
                    && hv.stride().move() == Stride.Move.WALK
                    && !edges.lineWalkable(hvParent.at(), here)) {
                // 直线是假的：换挂备胎（老实的邻步父），代价照它的算。
                // 节点留在账本里——它可能正是别人的祖先。
                gScore.put(hereKey, hv.stepG());
                hv = new Visit(here, hv.stepFrom(), hv.stepStride(),
                        hv.stepFrom(), hv.stepStride(), hv.stepG());
                seen.put(hereKey, hv);
            }

            // **贴边位是过路，不是目的地**：柱旁的缝站得住，却常是过不去
            // 的死口袋（栅栏圈实测：她挤进柱格西侧的缝、note=walk 卡在
            // 墙线上 x=8.07 不动——那儿离目标更近，诚实前沿就把她停在
            // 了那里）。前沿只认正经站位，缝只在路过时用。
            double h = heuristic(here, goal);
            if (h < closestH && here.kind() != Anchor.Kind.EDGE) {
                closestH = h;
                closest = here;
            }
            // 竖直验收留宽（上下各两格）：goal 的 y 常常不是站位口径
            // ——杆顶的锚比路标格高一大截、灯塔目标悬在空中。曾把上方
            // 收紧到半格（治"站在崖唇上宣布到站"），杆桥一族当场全红：
            // 杆顶锚永远高过路标 y，验收永不放行，部分路径又被降边剪
            // 刀截住，她钉死在半途（sw188 十一红）。沿口的病得在执行
            // 侧治，不能拿验收的尺子改。
            if (Math.hypot(here.at().x - goal.x, here.at().z - goal.z)
                    <= acceptWithin
                    && Math.abs(here.at().y - goal.y) <= 2.0D) {
                VoxelPath hit = rebuild(seen, here, true);
                return hit == null ? null
                        : LeapLanes.spread(straighten(hit, edges));
            }

            Double gHereBox = gScore.get(hereKey);
            if (gHereBox == null) {
                continue;
            }
            double gHere = gHereBox;
            Visit hereNow = seen.get(hereKey);
            Key grandKey = hereNow.cameFrom();
            // 祖父也可能已被撤销（它自己的直线验伪过）——那就没有捷径
            // 可假设，老实走邻边。
            Visit grandVisit = grandKey == null ? null : seen.get(grandKey);
            Anchor grand = grandVisit == null ? null : grandVisit.at();
            Double gGrand = grandKey == null ? null : gScore.get(grandKey);
            for (StrideSupplier.Out out : edges.from(here)) {
                Anchor to = out.to();
                Key outKey = key(to);
                double stepG = gHere + out.cost();
                double g = stepG;
                Key parentKey = hereKey;
                Stride stride = out.stride();
                // Theta* 的第二条路：走边且祖父在同一走高差走廊里，先假
                // 设祖父直线可视（真伪等它自己出队时兑付）——任意角的
                // 直线代价从**搜索里**就在指路，不是事后拉直。跳与降带
                // 着初速合同，父永远是脚下这一步。
                // 捷径限长 8.5 格：视线验伪是按格数的碰撞扫掠＋逐半步踩
                // 点，一条三十格捷径一次验伪抵几十个搜索步。跳边闸门上
                // 线前这笔账被跳链遮着（LEAP 不参与捷径）；闸门一开全图
                // 皆走，规划 p50 当场翻 2.8~6 倍（sw182），全是长线的验
                // 伪费。八格半够把空地八格拉成一条直线（快照钉着），更
                // 长的路多立几个路标，走起来没有分别。
                if (out.stride().move() == Stride.Move.WALK
                        && grand != null && gGrand != null
                        && grand.flatTo(to) <= 8.5D
                        && Math.abs(grand.at().y - to.at().y) <= 0.6D) {
                    double line = gGrand + grand.flatTo(to)
                            + Math.abs(to.at().y - grand.at().y) * 0.5D;
                    double step = stepG;
                    if (line <= step) {
                        g = line;
                        parentKey = grandKey;
                        stride = Stride.WALK_PACE;
                    }
                }
                Double known = gScore.get(outKey);
                if (known != null && known <= g) {
                    continue;
                }
                gScore.put(outKey, g);
                seen.put(outKey, new Visit(to, parentKey, stride,
                        hereKey, out.stride(), stepG));
                open.add(new Open(to, g + heuristic(to, goal)));
            }
        }
        // 部分路径：前沿诚实。前沿=起点的一节点残路不产。
        if (key(closest).equals(startKey)) {
            return null;
        }
        VoxelPath partial = rebuild(seen, closest, false);
        return partial == null ? null
                : LeapLanes.spread(straighten(partial, edges));
    }

    /**
     * 任意角拉直（Theta* 的后处理形态）：连续的走段用贪心最远可视点合
     * 并——视线通（{@link StrideSupplier#lineWalkable}）就直连，折点只
     * 留在支撑的真实拐点上。跳、降、爬带着初速合同，不参与。单段封在
     * 四格半内：执行侧的 stale 守卫按"锚够不着"作废路径，直线段太长会
     * 被它误杀。
     */
    private static VoxelPath straighten(VoxelPath path,
            StrideSupplier edges) {
        if (path == null || path.length() < 3) {
            return path;
        }
        List<Anchor> anchors = new ArrayList<>();
        List<Stride> strides = new ArrayList<>();
        anchors.add(path.anchorAt(0));
        int i = 0;
        while (i < path.length() - 1) {
            if (path.strideAt(i).move() != Stride.Move.WALK) {
                anchors.add(path.anchorAt(i + 1));
                strides.add(path.strideAt(i));
                i++;
                continue;
            }
            int far = i + 1;
            while (far + 1 < path.length()
                    && path.strideAt(far).move() == Stride.Move.WALK
                    && path.anchorAt(i).flatTo(path.anchorAt(far + 1))
                            <= 4.5D
                    && edges.lineWalkable(path.anchorAt(i),
                            path.anchorAt(far + 1))) {
                far++;
            }
            anchors.add(path.anchorAt(far));
            strides.add(Stride.WALK_PACE);
            i = far;
        }
        return new VoxelPath(anchors, strides, path.reaches());
    }

    /**
     * 一个节点的记账：当前认的父亲与合同，外加**降级备胎**——走直线捷
     * 径时同时记下老实的邻步父与它的代价。验伪那一刻换挡就行，不必把节
     * 点从账本里删掉：删掉会牵连所有以它为祖先的路（实测她反复拿不到
     * 路、供词冻在上一条 note，原地不动）。
     */
    private record Visit(Anchor at, Key cameFrom, Stride stride,
            Key stepFrom, Stride stepStride, double stepG) {
    }

    private static VoxelPath rebuild(Map<Key, Visit> seen, Anchor end,
            boolean reaches) {
        List<Anchor> anchors = new ArrayList<>();
        List<Stride> strides = new ArrayList<>();
        // **父链要防成环**：就地降级会把节点的代价往上抬，A* 里"父亲
        // 一定比孩子便宜"这条隐含保证就此失效——回溯于是可能兜圈子，
        // 服务器线程死转（转储实锤：CPU 全在这一行，整轮停摆）。见到走
        // 回头的节点就判这条链不作数：宁可这一 tick 没路，下一 tick 重
        // 铺，也不能把 tick 烧掉。
        java.util.Set<Key> seenBack = new java.util.HashSet<>();
        Visit walk = seen.get(key(end));
        while (walk != null) {
            if (!seenBack.add(key(walk.at()))) {
                return null;
            }
            anchors.add(0, walk.at());
            if (walk.stride() != null) {
                strides.add(0, walk.stride());
            }
            if (walk.cameFrom() == null) {
                break;
            }
            Visit up = seen.get(walk.cameFrom());
            if (up == null) {
                return null;
            }
            walk = up;
        }
        // 没把握到达就不做不可逆的事：部分路径剪到第一条降边之前。"尽量
        // 接近"若要靠跳崖兑现，兑现的是陷落不是接近（四格缺口案实测：前
        // 沿在缺口底贴对岸墙根，上不去也回不来——比停在沿口差远了）；同
        // 层的跳跃接近则原样保留（结构外跟随全靠它）。剪空了就是无路。
        if (!reaches) {
            for (int i = 0; i < strides.size(); i++) {
                if (strides.get(i).move() == Stride.Move.DROP) {
                    if (i == 0) {
                        return null;
                    }
                    anchors = anchors.subList(0, i + 1);
                    strides = strides.subList(0, i);
                    break;
                }
            }
        }
        return new VoxelPath(anchors, strides, reaches);
    }

    /**
     * 锚点键：站位的八分格量化（同格的不同贴边带是不同节点）。
     *
     * <p>三整数的 record，不是拼出来的字符串——键是搜索最热的东西，一
     * 次规划要造几百上千个，字符串键每个都带 {@code toShortString}、三
     * 次拼接与一个字符数组。量化坐标本身已经唯一确定锚点，格号是它的
     * 函数，不必再进键里。
     */
    private record Key(int qx, int qy, int qz) {
    }

    private static Key key(Anchor a) {
        return new Key(
                (int) Math.round(a.at().x * 8.0D),
                (int) Math.round(a.at().y * 8.0D),
                (int) Math.round(a.at().z * 8.0D));
    }

    private static double heuristic(Anchor a, Vec3 goal) {
        return Math.sqrt(a.at().distanceToSqr(goal));
    }

    /** 时间配额是否已吃紧（探路层据此改发乐观存根）。 */
    public static boolean strained() {
        return nanosSpent >= NANO_BUDGET;
    }
}
