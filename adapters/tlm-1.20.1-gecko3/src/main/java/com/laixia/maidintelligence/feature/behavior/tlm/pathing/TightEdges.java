package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.Node;

/**
 * 格心过不去、贴着边或角挤过去的那一族边。
 *
 * <p>从 {@code SafeFootingNodeEvaluator} 按职责拆出（单文件五百行的布局
 * 纪律）。那边回答"跳得到哪儿"——弧线、落差、缺口；这里只回答一件事：
 * **格心被占住时，身子还有没有别的缝可钻**。两件事都在长，但长的方向不
 * 同：跳跃靠弹道，钻缝靠身位箱贴着真实碰撞量。
 *
 * <p>两支合在一起是因为它们共用同一条纪律——**过得去才连，而"过得去"必须
 * 拿真实碰撞把身位箱扫一遍**。图上多连一条不存在的缝，她就会直挺挺撞上去
 * 站到天荒地老（玩家实测：整排栅栏被逐根当孤柱，"她似乎认为能过"）。
 */
final class TightEdges {
    private final SafeFootingNodeEvaluator owner;

    TightEdges(SafeFootingNodeEvaluator owner) {
        this.owner = owner;
    }

    /**
     * 贴角斜穿：两侧正交邻格都被挡住，可**对角那道缝身子过得去**。
     *
     * <p>原版禁止"两侧都挡住时的斜走"，对整块方块是对的——不能穿墙角。可
     * 栅栏柱只占格子中间四分之一（0.375–0.625），从**方块角点**斜穿过去的
     * 身位箱是 0.7–1.3，与两根柱子都不相交，净空还有 0.075 格：玩家走得过
     * 去，她也该走得过去。栅栏圈的**边角缺口**正好整个落在这条规则的盲区
     * 里——玩家实测点名："栅栏边角缺口女仆是无法正确识别的"。
     *
     * <p>不放宽规则本身，只补这一族边，而且**每条都拿真实碰撞扫过身位**
     * （{@code walkLineClear}）：过得去才连，过不去照旧不连。正交两侧本来
     * 就通的交给原版，这里不插手。
     */
    int cornerCuts(Node[] out, int count, Node node) {
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                int x = node.x + dx;
                int z = node.z + dz;
                // 缺口外面**矮一格**是常态而不是例外：栅栏往往就沿着地形的
                // 坎修。只连同层的话，这种缺口在图里根本不存在（玩家实测：
                // "极端条件下出不来，例如缺口下方低一格方块"）。
                int landing = cornerLanding(node, x, z);
                if (landing == Integer.MIN_VALUE) {
                    continue;
                }
                // 两侧都通的斜走原版自己会连，别重复发。
                if (owner.walkableCell(node.x + dx, node.y, node.z)
                        && owner.walkableCell(node.x, node.y, node.z + dz)) {
                    continue;
                }
                // **两侧脚下都得有地。**只问"有没有碰撞"是不够的：栅栏角
                // 两旁有柱子挡着但地面是通的，斜穿有支撑；而 L 形梁的角两
                // 旁是**虚空**——没有碰撞，所以"线是通的"，可她一脚踏空就
                // 下去了（实测：这一族边刚连上就把 L 形跑和窄梁掉头打伤）。
                // 两种情形在"有没有碰撞"上答案相同，在"有没有地"上正相反。
                if (!bracedAt(node.x + dx, node.y, node.z)
                        || !bracedAt(node.x, node.y, node.z + dz)) {
                    continue;
                }
                // 净空在**她横过去的那一层**量，与执行侧的走法对齐。
                //
                // 斜着上一格时她是**先跳上去、再横过去**的（见 LaneWork 的
                // 穿角：坎那种同层没有缝，缝在上一层），所以判据也该问上一
                // 层。同层与降一格取的仍是起步层——那两种她横过去时就在那一
                // 层——所以 max 一句话把三档都说对。
                //
                // 老实说：这一条是**按执行侧的走法对齐**，不是靠证据修出来
                // 的。我曾以为它就是玩家报的"缺角+高低差进不去"的病因，还
                // 照着写了测试；反证两轮（带/不带这一改）都红、距离几乎一
                // 样，**推翻了**。真因在执行侧的穿角顺序。留着它是因为图与
                // 执行侧该用同一把尺，不是因为它治好了什么。
                double line = Math.max(node.y, landing)
                        + FootingRule.STANDABLE_TOP + 0.05D;
                if (!FootingRule.walkLineClear(owner.world(),
                        node.x + 0.5D, node.z + 0.5D,
                        x + 0.5D, z + 0.5D, line)) {
                    continue;
                }
                count = owner.emitLanding(out, count, x, landing, z);
            }
        }
        return count;
    }

    /**
     * 斜穿时这一侧**扶不扶得住**：脚下这一层有没有地板。
     *
     * <p>拦的是 L 形梁那种悬空角——两旁没有碰撞（所以"线是通的"）却也没有
     * 地，她一脚踏空就下去了。栅栏角两旁有柱子挡着但地面是通的，斜穿有支
     * 撑，照连不误。
     *
     * <p><b>碑：不要为了"高低差"去放宽它。</b>我推演过一版：坎抬高一格时
     * 邻格里是实心的坎、坎底下没有地板，于是判据把坎读成虚空——听起来严丝
     * 合缝，还照着写了测试。**反证一跑就塌了**：撤掉改动那条测试照样绿。
     * 原因是我的夹具**每列只放一个方块**，坎底下才是空气；而真实世界里地
     * 是实心的，坎底下还是土石，这一句本来就成立。玩家报的"栅栏围角缺口+
     * 高低差进不去"另有其因，别再往这儿改。
     *
     * <p>更该记住的是那条方法论：夹具的地形不像真世界，按夹具推出来的病因
     * 会指向真世界里根本不存在的地方。
     */
    private boolean bracedAt(int x, int y, int z) {
        return FootingRule.coveringTopAt(owner.world(),
                        new BlockPos(x, y - 1, z)) >= y - 0.6D;
    }

    /**
     * 斜穿的落点在哪一层：同层，还是**矮一格**；都不成立返回哨兵。
     *
     * <p>只连同层是不够的。栅栏常常沿着地形的坎修，缺口外面矮一格是常态
     * ——图里没有这条边，她就只能站在缺口里干看着（玩家实测："极端条件下
     * 出不来，例如缺口下方低一格方块"）。
     *
     * <p>矮一格的落点还要多问一句头顶：她是**先走到角点、再落下去**的，
     * 所以落点上方那一格必须让身子过得去。
     *
     * <p>高一格也连——**出得来就得进得去**。坎这种地形两边都要走：她从
     * 高处斜穿下来能出圈，从低处斜穿上去才回得来（玩家实测："出来可以，
     * 但进去就不行"）。这一支的走法由执行侧的穿角专门接管，不走登阶段——
     * 登阶是贴脸撞跳，穿不过只有 0.075 格的角缝。
     */
    private int cornerLanding(Node node, int x, int z) {
        if (owner.walkableCell(x, node.y, z)
                && owner.getFloorLevel(new BlockPos(x, node.y, z))
                        >= node.y - 0.6D
                && owner.airy(owner.world(), x, node.y + 1, z)) {
            return node.y;
        }
        if (owner.airy(owner.world(), x, node.y, z)
                && owner.airy(owner.world(), x, node.y + 1, z)
                && owner.walkableCell(x, node.y - 1, z)
                && owner.getFloorLevel(new BlockPos(x, node.y - 1, z))
                        >= node.y - 1.6D) {
            return node.y - 1;
        }
        // 高一格：落点自己要站得住，头顶还要再空一格（她是跳上去的），
        // 而且起跳格自己的头顶也得空——那一跳发生在她自己的柱子里。
        if (owner.walkableCell(x, node.y + 1, z)
                && owner.getFloorLevel(new BlockPos(x, node.y + 1, z))
                        <= node.y + 1.25D
                && owner.airy(owner.world(), x, node.y + 2, z)
                && owner.airy(owner.world(), node.x, node.y + 1, node.z)
                && owner.airy(owner.world(), node.x, node.y + 2, node.z)) {
            return node.y + 1;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * 挤边跨越：正前一格格心被占（栅栏柱这类）但贴边塞得下身位、再往前一
     * 格又是正经路面——连一条穿过被占格的两格边。玩家绕柱贴边走的就是这
     * 条缝：柱只占中间四分之一，格级的图却把整格判死（体素化，玩家点名）。
     * 塞不塞得下由 {@code FootingRule.squeezePoint} 对真实碰撞形状逐候选
     * 点做身位箱测试，执行侧用同一个点走贴边折线。
     */
    int squeezeLanding(
            Node[] out,
            int count,
            Node node,
            Direction direction
    ) {
        int mx = node.x + direction.getStepX();
        int mz = node.z + direction.getStepZ();
        BlockPos mid = new BlockPos(mx, node.y, mz);
        if (!FootingRule.coversCenter(owner.world(), mid)
                || owner.walkableCell(mx, node.y, mz)
                || !lonePost(mid, direction)) {
            return count;
        }
        // **能站进去不等于能穿过去。**从前只问"这格里塞不塞得下一个身位"
        // （squeezePoint），于是连成一片的栅栏也被判成能挤：她确实能贴着柱
        // 子西面站住（实测停在 x=8.07，离柱面只差半分），可东边被柱身封死，
        // 那是死胡同。图上却因此连了一条"穿过栅栏"的边，她照着撞了 54 次
        // （栅栏圈实测：门槛跳对着栅栏连跳，一条腿都没走出去）。
        //
        // 判据换成入口、柱旁、出口三点连成的**真车道**——孤柱两侧有缝才算，
        // 一整排栅栏没有。
        boolean alongX = direction.getStepX() != 0;
        double lane = FootingRule.squeezeLane(owner.world(), mid, alongX,
                0.5D + (alongX ? node.z : node.x));
        if (Double.isNaN(lane)) {
            return count;
        }
        int fx = node.x + 2 * direction.getStepX();
        int fz = node.z + 2 * direction.getStepZ();
        if (owner.walkableCell(fx, node.y, fz)
                && owner.getFloorLevel(new BlockPos(fx, node.y, fz))
                        >= node.y - 0.6D
                && owner.airy(owner.world(), fx, node.y + 1, fz)) {
            count = owner.emitLanding(out, count, fx, node.y, fz);
        }
        // 被占格自己也是节点：贴边窄条站得住人，站上去还能接着起跳——
        // 柱子在崖沿格时，挤边和跳跃必须能组合（玩家实测：柱在边缘就又
        // 站桩了）。执行侧走它时瞄同一个贴边点。
        return owner.emitLanding(out, count, mx, node.y, mz);
    }

    /**
     * 挡路的这根高障是**孤零零一根**，还是一排墙里的一段。
     *
     * <p>"柱旁有缝可以侧身溜过"只对孤柱成立。一整排连起来的栅栏，每一根单
     * 独看都长得像孤柱——头顶那一格确实是空的（栅栏只有一格半高）——于是
     * 整道墙被逐根当成了可以飞越的柱子，图里还在柱与柱之间连出四格一跳的
     * 边。规划出来的路因此"穿墙而过"，她照着直挺挺走过去顶住：玩家原话
     * "栅栏她似乎会认为能过，所以无论是跟随还是寻敌，都只能眼睁睁看着"。
     *
     * <p>判据就是那句话本身：看行进轴**两侧**的邻格还是不是高障。孤柱两侧
     * 空着，一排栅栏两侧还是栅栏。侧身溜过用的正是那两侧的空间，所以这也
     * 不是额外的近似——本来就该问这一句。
     */
    boolean lonePost(BlockPos hard, Direction towards) {
        int px = towards.getStepZ();
        int pz = towards.getStepX();
        return !FootingRule.tallAtCenter(owner.world(), hard.offset(px, 0, pz))
                && !FootingRule.tallAtCenter(
                        owner.world(), hard.offset(-px, 0, -pz));
    }
}
