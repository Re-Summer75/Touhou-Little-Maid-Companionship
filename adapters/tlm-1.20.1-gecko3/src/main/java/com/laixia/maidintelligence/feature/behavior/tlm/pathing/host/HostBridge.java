package com.laixia.maidintelligence.feature.behavior.tlm.pathing.host;

import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel.Anchor;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
        .AnchorResolver;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
        .EdgeVeto;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
        .StrideWeb;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
        .VoxelAstar;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
        .VoxelPath;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 面向宿主问询的翻译层：**问路问自有引擎，答卷投影成原版 Path**。
 *
 * <p>从 {@code VoxelAstar} 按职责拆出（五百行布局纪律）。这里没有搜索，
 * 只有"sink 的 createPath 问询怎么答"——原版 sink 拿答案决定擦不擦走目
 * 标，而原版 A* 眼里杆桥是绝路，体素引擎曾因此永远接不到单（杆桥案：t42
 * 写目标、t43 被擦，全场十五次）。影子只答问询，执行永远走 moveTo 的重新
 * 规划。历史与教训详见台账 §11。
 */
public final class HostBridge {
    /** 空气目标向下找地面只找这么深，口径同建路落地。 */
    private static final int GROUNDING_REACH = 8;

    private HostBridge() {
    }

    /**
     * 体素引擎试铺一条到 {@code target} 的路，投影成原版 Path；图上真无
     * 路时返回 null（那就是诚实的"不可达"）。
     *
     * <p>三道省钱闸，从便宜到贵：**贴身目标**（两格半内同层）直接发存根
     * ——拾取连发每件换一次单，脚边的东西不值得一次 A*；**配额吃紧**也
     * 发存根——sink 每 tick 换单、探路连发不设闸曾烧掉半个 tick（掉落
     * 物风暴 jcmd 定点：十二抽十一中在 find，全从 sink.start 进来，而探
     * 路不走 PathClock 那条账）；**同格十 tick 缓存**——战斗目标每 tick
     * 挪半格，重复问询不值得重复作答。空闲时照常全额试铺，密封圈的诚实
     * 钉由此不受影响。
     */
    public static Path toward(Mob mob, BlockPos target, EdgeVeto veto) {
        // 空中目标先落地（贴脚八格内）：够取物、悬空点位的单都长这样，
        // 不落地必是部分路径，sink 又会拿"没到"当"到不了"擦单。
        BlockPos aim = target;
        if (mob.level().getBlockState(aim).isAir()) {
            BlockPos.MutableBlockPos drop = aim.below().mutable();
            int floor = Math.max(mob.level().getMinBuildHeight(),
                    aim.getY() - GROUNDING_REACH);
            while (drop.getY() > floor
                    && mob.level().getBlockState(drop).isAir()) {
                drop.move(0, -1, 0);
            }
            if (!mob.level().getBlockState(drop).isAir()) {
                aim = drop.above();
            }
        }
        if (Math.hypot(aim.getX() + 0.5D - mob.getX(),
                aim.getZ() + 0.5D - mob.getZ()) <= 2.5D
                && Math.abs(aim.getY() - mob.getY()) <= 1.5D) {
            return stub(mob, aim);
        }
        long cell = aim.asLong();
        Probe known = PROBES.get(mob.getUUID());
        if (known != null && mob.tickCount - known.tick < 10) {
            // 每女仆全额探路限频：十 tick 内答过的，同格复用原答卷、异
            // 格连环问发乐观存根。全局"配额吃紧才存根"试过一轮：并行批
            // 次里配额常态吃紧，诚实钉与杆桥的冷问询全被存根污染
            // （sw215 七红）——节流必须用局部条件，冷问询永远全额。
            return known.cell == cell ? known.answer : stub(mob, aim);
        }
        var web = webOf(mob, veto);
        Anchor start = AnchorResolver.nearby(mob.level(),
                mob.blockPosition(), mob.getBoundingBox(), mob.getY(), web);
        if (start == null) {
            confess("no-start", mob, aim);
            return null;
        }
        VoxelPath path = VoxelAstar.find(web, start,
                new Vec3(aim.getX() + 0.5D, aim.getY(), aim.getZ() + 0.5D),
                0.45D,
                // 新织的图（要现仿真弹道）才吃预算档；缓存图上纯搜索
                // 便宜、全额诚实——null 让 sink 写 CANT_REACH 秒拉黑。
                freshlyWoven()
                        ? VoxelAstar.headroom(mob.level().getGameTime())
                        : 0L);
        if (path == null) {
            confess("no-route", mob, aim);
        }
        Path answer = path == null ? null : shadowOf(path, aim);
        PROBES.put(mob.getUUID(), new Probe(cell, mob.tickCount, answer));
        return answer;
    }

    /** 探路返空取证（每五秒至多一声）：杆桥案 sink 擦单复发时，null
     *  出自哪个口一轮定位。 */
    private static long lastConfess;
    private static boolean confessed;

    private static void confess(String why, Mob mob, BlockPos aim) {
        long now = System.nanoTime();
        // nanoTime 起点任意，减 MIN_VALUE 会溢出成永远压声——首声用
        // 布尔另记（sw217 整轮零供词就是这么哑的）。
        if (confessed && now - lastConfess < 1_000_000_000L) {
            return;
        }
        confessed = true;
        lastConfess = now;
        com.mojang.logging.LogUtils.getLogger().warn(
                "[probe-null] {} at=({}, {}, {}) aim={}", why,
                String.format("%.1f", mob.getX()),
                String.format("%.1f", mob.getY()),
                String.format("%.1f", mob.getZ()), aim);
    }

    private record Probe(long cell, int tick, Path answer) {
    }

    private static final Map<UUID, Probe> PROBES = new HashMap<>();

    /** 图缓存（按女仆、两秒 TTL、探路与执行共享）：StrideWeb 的弹道
     *  仿真缓存是实例字段，每单 new 等于每单全部重仿真——封圈案 93
     *  次全额审每次都 150ms，烧的全是同一批弹道（sw238）。图冻结两秒
     *  =规划对世界变化至多滞后两秒，与 mesh 雕刻缓存同口径。 */
    private static final Map<Mob, Woven> WEBS =
            new java.util.WeakHashMap<>();

    private record Woven(StrideWeb web, int stamp) {
    }

    /** 最近一次 webOf 给的是不是新织的图：新织=本单要现仿真弹道（贵，
     *  吃预算档）；缓存图上纯搜索便宜，全额诚实。 */
    private static boolean freshWeave;

    public static boolean freshlyWoven() {
        return freshWeave;
    }

    public static StrideWeb webOf(Mob mob, EdgeVeto veto) {
        Woven kept = WEBS.get(mob);
        // 只按时间作废：锚与弹道的缓存键都是绝对坐标，挪窝不冲突、走
        // 到哪懒补到哪（挪两格就整张作废曾把命中率打回原形）。
        if (kept != null && mob.tickCount - kept.stamp < 40) {
            freshWeave = false;
            return kept.web;
        }
        freshWeave = true;
        var web = new StrideWeb(mob.level(), mob.getBbWidth(),
                mob.getBbHeight(), veto);
        WEBS.put(mob, new Woven(web, mob.tickCount));
        return web;
    }

    /** 两节点乐观存根：让 sink 放行，别的不承诺。 */
    private static Path stub(Mob mob, BlockPos aim) {
        var nodes = new ArrayList<Node>();
        nodes.add(new Node(mob.blockPosition().getX(),
                mob.blockPosition().getY(), mob.blockPosition().getZ()));
        nodes.add(new Node(aim.getX(), aim.getY(), aim.getZ()));
        return new Path(nodes, aim, true);
    }

    /**
     * 自有路径的原版投影。reaches **如实转告**（一律 true 曾打红密封圈
     * 诚实钉；sink 只在 null 时擦单，部分路径照跑）。{@code goal} 是
     * **原始目的地**不是路径末端（拿 end 当 target 时 sink 把她的单换成
     * 前沿——四格缺口案她"计划内"跳崖下坑）。
     */
    public static Path shadowOf(VoxelPath vp, BlockPos goal) {
        var nodes = new ArrayList<Node>();
        for (int i = 0; i < vp.length(); i++) {
            var cell = vp.anchorAt(i).cell();
            nodes.add(new Node(cell.getX(), cell.getY(), cell.getZ()));
        }
        return new Path(nodes, goal, vp.reaches());
    }
}
