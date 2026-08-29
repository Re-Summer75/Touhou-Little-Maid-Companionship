package com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel;

import com.laixia.maidintelligence.feature.behavior.tlm.pathing.FootingRule;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.sweep
        .LeapContract;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.sweep
        .SweptAcceptance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

import java.util.ArrayList;
import java.util.List;

/**
 * 边网：锚点之间怎么走——自有图的邻接供给。
 *
 * <p>四类骨干边（走、登、跳、降），判据全部取自自有的立足尺与扫掠内核：
 * 走边拿身位线扫真实碰撞，跳边与降边拿执行侧同款初速在仿真里飞一遍落进
 * 才连——**图承诺的每一步都是仿真里真走得通的一步**。挤缝与穿角两类在
 * 第三阶段并入（它们要多锚点解析先就位）。
 */
public final class StrideWeb implements StrideSupplier {
    /** 同层跳最远跨度（含落点）：与旧图同一个物理极限。 */
    private static final int LEAP_REACH = 4;

    /** 干落最深：与旧图同一条容忍线。 */
    private static final int DROP_DEPTH = 6;

    private final BlockGetter level;
    private final double width;
    private final double height;
    private final EdgeVeto veto;

    /** 一次规划内的锚点缓存：邻域彼此重叠，同格要被解析几十次，而支
     *  撑面语法的解析（盒集展开+净空探查）不便宜——不缓存就是把最热
     *  的函数按倍数放大（Theta* 上线实测单 tick 四十秒的过载）。图对
     *  象每单新建，缓存随图生灭，不吃世界变更。 */
    private final java.util.HashMap<Long, List<Anchor>> anchors =
            new java.util.HashMap<>();

    /** 跳边验收的记账：一次规划里同一条边会被问上好几遍（A* 会重开已
     *  改善的节点），而每问一次就是一整条弧的逐 tick 扫掠——最贵的东
     *  西不该算第二遍。 */
    private final java.util.HashMap<JumpKey, Boolean> jumps =
            new java.util.HashMap<>();

    private record JumpKey(int fx, int fy, int fz, int tx, int ty, int tz) {
    }

    /** 视线检查的记账：Theta* 每次出队都要兑付一条捷径，而节点会被反
     *  复改善、反复出队——同一条线不该沿着采样点再走一遍。 */
    private final java.util.HashMap<JumpKey, Boolean> lines =
            new java.util.HashMap<>();

    /** 缓存版的跳边终审。 */
    private boolean jumpOk(Anchor from, Anchor to) {
        JumpKey key = new JumpKey(
                (int) Math.round(from.at().x * 8.0D),
                (int) Math.round(from.at().y * 8.0D),
                (int) Math.round(from.at().z * 8.0D),
                (int) Math.round(to.at().x * 8.0D),
                (int) Math.round(to.at().y * 8.0D),
                (int) Math.round(to.at().z * 8.0D));
        Boolean known = jumps.get(key);
        if (known != null) {
            return known;
        }
        boolean ok = SweptAcceptance.jumpAccepted(level,
                from.at().x, from.at().y, from.at().z,
                to.cell().getX(), to.at().y, to.cell().getZ(),
                width, height);
        jumps.put(key, ok);
        return ok;
    }

    public StrideWeb(BlockGetter level, double width, double height,
            EdgeVeto veto) {
        this.level = level;
        this.width = width;
        this.height = height;
        this.veto = veto;
    }

    /** 这一格所有站位（含柱旁的贴边缝）；缓存空表也算答案。 */
    private List<Anchor> all(BlockPos cell) {
        long key = cell.asLong();
        List<Anchor> hit = anchors.get(key);
        if (hit != null) {
            return hit;
        }
        List<Anchor> found = AnchorResolver.resolveAll(
                level, cell, width, height);
        anchors.put(key, found);
        return found;
    }

    /** 这一格的主站位（面积最大的那个）；没有就 null。 */
    private Anchor resolve(BlockPos cell) {
        Anchor best = null;
        for (Anchor a : all(cell)) {
            if (best == null
                    || (a.stand().maxX - a.stand().minX)
                            * (a.stand().maxZ - a.stand().minZ)
                    > (best.stand().maxX - best.stand().minX)
                            * (best.stand().maxZ - best.stand().minZ)) {
                best = a;
            }
        }
        return best;
    }

    /** 从 {@code from} 看过去，这一格里她该踩的位置：保持车道（柱两侧
     *  各有一条缝，走哪条由几何决定，不由格心决定）。 */
    private Anchor nearest(Anchor from, BlockPos cell) {
        // **车道只给挤缝用**：她已经在贴边缝里（出发锚是 EDGE）才把横
        // 向偏移带过去；平地上一律走格心。对每一格都拉向她那一侧试过一
        // 轮——目标点被拉到支撑面最外沿（实测 next=(12.0,…) 而非格心
        // 12.5），落点正踩在唇上，三十七红里大半是这么摔的。
        boolean squeezing = from.kind() == Anchor.Kind.EDGE;
        Anchor best = null;
        double bestD = Double.MAX_VALUE;
        for (Anchor face : all(cell)) {
            Anchor aim = squeezing
                    ? AnchorResolver.lane(level, face,
                            from.at().x, from.at().z, width, height)
                    : face;
            double d = from.flatTo(aim);
            if (d < bestD) {
                bestD = d;
                best = aim;
            }
        }
        return best;
    }

    /** 挤缝的过路费：贴边位是**下策**，有正路先走正路。
     *
     * <p>缝站得住却常常通不到别处（栅栏墙里两柱之间那条 0.375 的带子就
     * 是：沿墙走得动，穿墙走不动）。不加价，她会把缝当近路一头扎进去，
     * 贴着柱子卡死在墙线上（栅栏圈实测 rel x=8.07，身位正抵着柱）。加
     * 上价，只有"没有别的路"时她才挤——那正是挤缝该出场的时候。 */
    private static final double SQUEEZE_TOLL = 1.5D;

    @Override
    public List<Out> from(Anchor from) {
        List<Out> outs = new ArrayList<>();
        BlockPos cell = from.cell();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            walkOrClimb(outs, from, cell, side);
            leaps(outs, from, cell, side);
            drop(outs, from, cell, side);
        }
        // 对角：走与小落差。斜线的安全不靠"两侧通"这类格级近似——身位
        // 沿线扫**真实碰撞**，柱挡就不连、缝够就连；杆桥的缺杆转角、之
        // 字楼梯的斜接全靠这一族（缺它们时她在转角外一格干站）。
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                diagonal(outs, from, cell, dx, dz);
            }
        }
        // 实机验伪的边（账本拉黑中）当不存在：A* 自会找别的路，找不到
        // 就是诚实的无路——比贴着跳不过的障碍每秒半重试一次强。
        outs.removeIf(out ->
                veto.vetoed(cell, out.to().cell()));
        outs.replaceAll(out -> out.to().kind() == Anchor.Kind.EDGE
                ? new Out(out.to(), out.stride(), out.cost() + SQUEEZE_TOLL)
                : out);
        return outs;
    }

    /**
     * 任意角拉直的视线检查：身位沿线无碰撞，且**每半步脚下都有支撑**
     * （顶差在台阶高内）。走是唯一参与拉直的动作——它没有合同要锚定。
     */
    @Override
    public boolean lineWalkable(Anchor from, Anchor to) {
        JumpKey key = new JumpKey(
                (int) Math.round(from.at().x * 8.0D),
                (int) Math.round(from.at().y * 8.0D),
                (int) Math.round(from.at().z * 8.0D),
                (int) Math.round(to.at().x * 8.0D),
                (int) Math.round(to.at().y * 8.0D),
                (int) Math.round(to.at().z * 8.0D));
        Boolean known = lines.get(key);
        if (known != null) {
            return known;
        }
        boolean ok = lineWalkableUncached(from, to);
        lines.put(key, ok);
        return ok;
    }

    private boolean lineWalkableUncached(Anchor from, Anchor to) {
        if (Math.abs(to.at().y - from.at().y) > 0.6D) {
            return false;
        }
        double flat = from.flatTo(to);
        if (flat < 1.0E-4D) {
            return true;
        }
        if (!FootingRule.walkLineClear(level,
                from.at().x, from.at().z, to.at().x, to.at().z,
                Math.max(from.at().y, to.at().y) + 0.05D)) {
            return false;
        }
        int steps = (int) Math.ceil(flat / 0.5D);
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            double px = from.at().x + (to.at().x - from.at().x) * t;
            double py = from.at().y + (to.at().y - from.at().y) * t;
            double pz = from.at().z + (to.at().z - from.at().z) * t;
            Anchor mid = resolve(
                    BlockPos.containing(px, py + 0.1D, pz));
            if (mid == null || mid.breadth() < 0.4D
                    || Math.abs(mid.at().y - py) > 0.6D) {
                return false;
            }
        }
        return true;
    }

    /** 对角一步：同层或上下一格内，身位线扫真实碰撞过得去才连。 */
    private void diagonal(List<Out> outs, Anchor from, BlockPos cell,
            int dx, int dz) {
        BlockPos next = cell.offset(dx, 0, dz);
        for (int dy = 1; dy >= -1; dy--) {
            Anchor to = nearest(from, next.above(dy));
            if (to == null) {
                continue;
            }
            double rise = to.at().y - from.at().y;
            if (rise > 1.25D || rise < -1.25D) {
                return;
            }
            if (rise <= 0.6D) {
                if (!FootingRule.walkLineClear(level,
                        from.at().x, from.at().z, to.at().x, to.at().z,
                        Math.max(from.at().y, to.at().y) + 0.05D)) {
                    return;
                }
                // 斜线的**中段也要有脚下**：两个正交中介格至少一个能站，
                // 否则这条斜线走到一半是悬空（断口转角实测：线扫无碰撞
                // ≠脚下有支撑，她走斜线中途掉进断口）。悬空的斜线降级为
                // 带锁小跳——弧线飞过去，落点由仿真验过。
                boolean braced = resolve(cell.offset(dx, dy, 0)) != null
                        || resolve(cell.offset(0, dy, dz)) != null;
                // **斜走要正经面**：窄条上的对角就是四十五度抄近道，
                // 转角处一步切出杆外就是坠落（玩家最早报的那桩"转角呈
                // 45 度路径、从旁边掉下去"）。直走照旧允许窄条——杆桥
                // 得能走；抄近道不行。
                if (braced && to.breadth() >= 0.4D) {
                    outs.add(new Out(to, Stride.WALK_PACE,
                            from.flatTo(to) + Math.abs(rise) * 0.5D + 0.2D));
                } else if (jumpOk(from, to)) {
                    outs.add(new Out(to,
                            new Stride(Stride.Move.LEAP,
                                    LeapContract.launchSpeed(
                                            from.flatTo(to), 0)),
                            from.flatTo(to) + 0.8D));
                }
                return;
            }
            // 斜着登一格：贴脸带锁跳，仿真终审（弧线撞不撞角上的东西由
            // 真实碰撞回答）。
            if (jumpOk(from, to)) {
                outs.add(new Out(to,
                        new Stride(Stride.Move.CLIMB,
                                LeapContract.launchSpeed(from.flatTo(to), 1)),
                        from.flatTo(to) + 1.8D));
            }
            return;
        }
    }

    /** 平走与登一格：邻格锚点，高差定动作。 */
    private void walkOrClimb(List<Out> outs, Anchor from, BlockPos cell,
            Direction side) {
        BlockPos next = cell.relative(side);
        for (int dy = 1; dy >= -1; dy--) {
            Anchor to = nearest(from, next.above(dy));
            if (to == null) {
                continue;
            }
            double rise = to.at().y - from.at().y;
            if (rise > 1.25D || rise < -1.25D) {
                continue;
            }
            if (rise <= 0.6D && rise >= -1.25D) {
                // 平走（含贴脚小落差）：身位沿线扫真实碰撞，过得去才连。
                //
                // **窄条也是路，薄棱不是**：横放的末地烛桥宽 0.25，一
                // 刀切"走路要正经面"就是把整座杆桥从图上抹掉（断肘杆桥
                // 三红，她在岸边差四格半干站）——杆桥恰是玩家点名要走
                // 的地形。而立起来的活板门顶棱只有 0.1875，比杆条还薄，
                // 那是门板的边缘不是路，踩着走就是走进门洞（开门洞案实
                // 测）。界划在两者之间：0.2。窄面上的稳当由执行侧管（判
                // 到按面宽收口、窄面不避让、不贴沿）。
                if (to.breadth() >= 0.2D
                        && FootingRule.walkLineClear(level,
                                from.at().x, from.at().z,
                                to.at().x, to.at().z,
                                Math.max(from.at().y, to.at().y)
                                        + 0.05D)) {
                    outs.add(new Out(to, Stride.WALK_PACE,
                            from.flatTo(to) + Math.abs(rise) * 0.5D));
                }
                return;
            }
            // 登一格：贴脸带锁跳，仿真验收。
            if (jumpOk(from, to)) {
                outs.add(new Out(to,
                        new Stride(Stride.Move.CLIMB,
                                LeapContract.launchSpeed(from.flatTo(to), 1)),
                        from.flatTo(to) + 1.5D));
            }
            return;
        }
    }

    /** 跳边：跨二到四格，同层与上下一格，仿真终审。 */
    private void leaps(List<Out> outs, Anchor from, BlockPos cell,
            Direction side) {
        for (int reach = 2; reach <= LEAP_REACH; reach++) {
            BlockPos far = cell.relative(side, reach);
            for (int dy = 1; dy >= -1; dy--) {
                Anchor to = nearest(from, far.above(dy));
                if (to == null) {
                    continue;
                }
                int band = (int) Math.round(to.at().y - from.at().y);
                if (band > 1 || band < -1) {
                    continue;
                }
                if (!jumpOk(from, to)) {
                    continue;
                }
                double speed = LeapContract.launchSpeed(
                        from.flatTo(to), band);
                // 极限跨度加价：有稳路先走稳路，同旧图的定价哲学。
                // 跨度越远越贵：跳得远就越贴弹道极限，起跳点差半格就是
                // 摔。按纯距离算，"从这儿跳三格"与"走一格再跳两格"恰好
                // 一样贵，她会挑更险的那条——加价打破这个平手，先走近再
                // 跳。极限跨度另有重罚。
                // 极限跨度加价：有稳路先走稳路，同旧图的定价哲学。
                //
                // 按跨度递增的加价试过两档（0.6、0.15/格），都在"直跳赢
                // 绕路"上翻车——任意角把走路按纯直线距离计价，跳却背着
                // 过路费，天平本来就偏；再加价就是逼她绕远。定价维持原
                // 样：让她从台中央起跳的那桩病（末地烛中继）真正的药是
                // 执行侧探真沿，不是图侧劝退。
                double toll = reach >= LEAP_REACH ? 3.0D : 0.5D;
                outs.add(new Out(to,
                        new Stride(Stride.Move.LEAP, speed),
                        from.flatTo(to) + toll));
                return;
            }
        }
    }

    /** 降边：沿口外一格向下扫到地，干落封深、仿真验落点。 */
    private void drop(List<Out> outs, Anchor from, BlockPos cell,
            Direction side) {
        BlockPos out = cell.relative(side);
        if (resolve(out) != null) {
            return;
        }
        for (int depth = 2; depth <= DROP_DEPTH; depth++) {
            Anchor to = resolve(out.below(depth - 1));
            if (to == null) {
                continue;
            }
            if (SweptAcceptance.dropAccepted(level,
                    from.at().x, from.at().y, from.at().z,
                    to.cell().getX(), to.at().y, to.cell().getZ(),
                    width, height)) {
                outs.add(new Out(to,
                        new Stride(Stride.Move.DROP,
                                LeapContract.dropPushFor(from.flatTo(to))),
                        from.flatTo(to) + depth + 3.0D));
            }
            return;
        }
    }
}
