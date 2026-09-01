package com.laixia.maidintelligence.gametest.support;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.SureFootedNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 她的脚每一步在做什么，逐行印出来，过不过都印——{@code CombatTrace} 的
 * 寻路同门。
 *
 * <p>只报过挂的场景是"更慢地不知道"。寻路这半年真正定罪的每一桩（takeoff
 * 摩擦吞冲量、门板脚感、缺口柱唇沿、深降改派、可达前沿站桩）最后都是靠一列
 * 数字，不是靠断言。行由**变化**驱动：执行器换分支、路径生灭、节点销账、
 * 起跳落地、走目标生灭，都值一行；长久的真没事才由心跳报时。
 */
public final class PathwalkTrace {
    /** 真没事时多少 tick 也要报一行，免得表读起来像停了。 */
    private static final int HEARTBEAT_TICKS = 20;

    private final String name;
    private final BlockPos zero;
    private final List<String> rows = new ArrayList<>();

    /** 水的定位器只在第一次碰水时开一枪，别刷屏。 */
    private boolean wetReported;

    private String previousNote = "";
    private String previousNext = "";
    private String previousWalk = "";
    private boolean previousGrounded = true;
    private boolean previousHadPath;
    private Vec3 previousPosition;
    private double movedSinceRow;
    private long lastRow = Long.MIN_VALUE;

    public PathwalkTrace(String name, BlockPos zero) {
        this.name = name;
        this.zero = zero;
        // 场距测量：每条读数带自报结构原点。跨批次残骸（幽灵地板、漫进来
        // 的水）要靠它定位——清场体积得按真实场距裁，清大了会抹掉同批邻居。
        System.out.println("[arena] " + name + " zero=" + zero.toShortString());
        rows.add(String.join("\t",
                "tick", "ev", "pos", "mv", "vel", "vy", "gnd",
                "note", "nxt", "end", "reach", "prog", "walkTo"));
    }

    /** 记这一 tick；没变化且心跳未到就什么都不写。 */
    public void sample(long tick, EntityMaid maid) {
        // 实时面板顺手喂一帧：无头测试的位置与动作供词开一扇实时的窗。
        com.laixia.maidintelligence.gametest.support.live.LiveBoard.post(
                name, zero, maid,
                maid.getNavigation() instanceof com.laixia.maidintelligence
                        .feature.behavior.tlm.pathing.SureFootedNavigation
                        sure ? sure.pathwalkNote() : "-");
        PathNavigation nav = maid.getNavigation();
        Path path = nav.getPath();
        String note = nav instanceof SureFootedNavigation sure
                ? sure.pathwalkNote()
                : "-";
        String next = path == null || path.isDone()
                ? "-"
                : rel(path.getNextNodePos());
        String walk = walkTo(maid);
        boolean grounded = maid.onGround();
        boolean hasPath = path != null && !path.isDone();
        if (previousPosition != null) {
            movedSinceRow += maid.position().distanceTo(previousPosition);
        }

        // 水的定位器。两次实测她在本场景根本没有水的高度上 isInWater() 为
        // 真（踏石岛 rel y=10、悬吊门板 rel y=11~13），而全仓只有两处放水、
        // 都在 rel y<=2；棋盘也量过（格距 36 / 行距 13+，场景最宽 30），邻
        // 场够不着彼此。来源不明就别再猜——她一碰水当场把水块的**绝对坐标**
        // 打出来，下一次出现直接定位是谁的水。
        if (!wetReported && maid.isInWater()) {
            wetReported = true;
            StringBuilder wet = new StringBuilder();
            BlockPos feet = maid.blockPosition();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos at = feet.offset(dx, dy, dz);
                        var fluid = maid.level().getFluidState(at);
                        if (!fluid.isEmpty()) {
                            // 方块名 + 是不是源。三种嫌疑长一个样：真水源、
                            // 流下来的水、**含水方块**（含水的活板门照样让
                            // getFluidState 非空）——只报坐标分不开。
                            wet.append(" ").append(at.toShortString())
                                    .append("=")
                                    .append(maid.level().getBlockState(at)
                                            .getBlock().getName().getString())
                                    .append(fluid.isSource() ? "(源)" : "(流)");
                        }
                    }
                }
            }
            // 探顶：沿她头顶正上方那一列往上走到水的尽头，报出最高的水格
            // 和它上面那一格是什么。两轮实测水都是**从上方浇下来的**（缺角
            // 圈里竖着叠了三格的落水柱），源头在 ±2 的扫描窗之外——横着扫
            // 十次也够不着，竖着一杆子就到。
            StringBuilder crown = new StringBuilder();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos column = feet.offset(dx, 0, dz);
                    if (maid.level().getFluidState(column).isEmpty()) {
                        continue;
                    }
                    BlockPos top = column;
                    while (!maid.level().getFluidState(top.above()).isEmpty()) {
                        top = top.above();
                    }
                    crown.append(" 顶=").append(top.toShortString())
                            .append(" 其上=")
                            .append(maid.level().getBlockState(top.above())
                                    .getBlock().getName().getString());
                    // 从柱顶横向找**真正的源**：落水柱的顶与源同高、相邻。
                    // 报出源坐在什么上面——源不会悬空，垫着它的那块方块就
                    // 是凶手的地皮（探顶实测：柱顶 y=-32、其上是空气，比一
                    // 切场景都高 28 格，光有高度定不了案）。
                    for (int sx = -2; sx <= 2 && crown.indexOf("源=") < 0;
                            sx++) {
                        for (int sz = -2; sz <= 2; sz++) {
                            BlockPos well = top.offset(sx, 0, sz);
                            if (maid.level().getFluidState(well).isSource()) {
                                crown.append(" 源=")
                                        .append(well.toShortString())
                                        .append(" 源下=")
                                        .append(maid.level()
                                                .getBlockState(well.below())
                                                .getBlock().getName()
                                                .getString());
                                break;
                            }
                        }
                    }
                    dx = 2;
                    break;
                }
            }
            System.out.println("[wet] " + name + " t=" + tick
                    + " 她在 " + maid.position() + " 附近的水(绝对):"
                    + (wet.length() == 0 ? " 扫不到？！" : wet.toString())
                    + crown);
        }
        String event = eventFor(note, next, walk, grounded, hasPath);
        boolean heartbeat = tick - lastRow >= HEARTBEAT_TICKS;
        previousNote = note;
        previousNext = next;
        previousWalk = walk;
        previousGrounded = grounded;
        previousHadPath = hasPath;
        previousPosition = maid.position();
        if (event.isEmpty() && !heartbeat) {
            return;
        }

        Vec3 velocity = maid.getDeltaMovement();
        rows.add(String.join("\t",
                Long.toString(tick),
                event.isEmpty() ? "." : event,
                String.format("%.1f,%.1f,%.1f",
                        maid.getX() - zero.getX(),
                        maid.getY() - zero.getY(),
                        maid.getZ() - zero.getZ()),
                String.format("%.1f", movedSinceRow),
                String.format("%.2f", Math.hypot(velocity.x, velocity.z)),
                // 竖直分量单列一格。水平速度说不出"谁给了她一记冲量"，而
                // 起跳是个一眼认得出的数（+0.42）：倒 T 实测她在本该轻轻迈
                // 下一格的段里升了 1.3 格，光看水平列查不出是谁抛的。
                String.format("%+.2f", velocity.y),
                // 泡在水里也要报。滞空与泡水在原版里是两种完全不同的
                // 处境：执行器对无锁滞空一概不管，可**泡水时它照常接管**
                // （守卫是 !onGround && !isInWater）。实测撞见过恒定 +0.02
                // 的上浮加上每 tick 触发的唇沿自救，人被送到 y=25——只看
                // AIR 分不出那是自由落体还是在水里往上飘。
                grounded ? "y" : (maid.isInWater() ? "WATER" : "AIR"),
                note,
                next,
                path == null ? "-" : rel(path.getEndNode().asBlockPos()),
                path == null ? "-" : path.canReach() ? "y" : "NO",
                path == null
                        ? "-"
                        : path.getNextNodeIndex() + "/" + path.getNodeCount(),
                walk
        ));
        // 新路一出现就把**整条**印出来。终点和节点数说不出"这条路长什么
        // 样"，而实机里出事的正是那种"看着有路、其实只到脚下"的残桩：终点
        // 列显示 4,3,4、reach=NO，可它到底是重铺出来的两节点，还是好路被截
        // 断的头两节，光看那一列永远分不出——两种病的修法完全不同。
        if ("PATH".equals(event)) {
            rows.add("    路：" + describe(path, zero));
        }
        movedSinceRow = 0.0D;
        lastRow = tick;
    }

    /** 全表进标准输出，过不过都该被调一次。 */
    public void dump() {
        StringBuilder out = new StringBuilder();
        out.append("\n=== pathwalk trace: ").append(name).append(" ===\n");
        for (String row : rows) {
            out.append(row).append('\n');
        }
        System.out.println(out);
    }

    /**
     * 往表里插一段自由文本（俯视快照这类），插在当前位置以保时间顺序。
     *
     * <p>坐标行回答"她去了哪儿"，可"那儿长什么样"要靠人脑把一串数字还原成
     * 地形——我在这上面栽过好几次。图和坐标印在同一条时间线上，形状对不对
     * 一眼就看出来。
     */
    public void aside(String block) {
        rows.add(block);
    }

    /** 一条路的完整证词：能否到站 + 逐节点相对坐标。探图用。 */
    /** 写单前先自己问一遍路并留档：sink 擦单案里"探路到底答了什么"
     *  不用再从全冻读数带反推。答卷进的是同一个限频缓存，紧跟着的
     *  sink 问询同格复用，行为不因取证而变。 */
    public static void probeAndOrder(EntityMaid maid, BlockPos goal,
            String tape, int tick) {
        Path path = maid.getNavigation().createPath(goal, 0);
        System.out.println("[rod-probe] " + tape + " t" + tick + " path="
                + (path == null ? "null"
                        : path.getNodeCount() + "n reach="
                                + path.canReach()));
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(
                        new net.minecraft.world.entity.ai.behavior
                                .BlockPosTracker(goal), 0.45F, 0));
    }

    public static String describe(Path path, BlockPos zero) {
        if (path == null) {
            return "null-path";
        }
        StringBuilder out = new StringBuilder();
        out.append(path.getNodeCount()).append(" nodes, canReach=")
                .append(path.canReach()).append(":");
        for (int i = 0; i < path.getNodeCount(); i++) {
            out.append(" (").append(path.getNode(i).x - zero.getX())
                    .append(",").append(path.getNode(i).y - zero.getY())
                    .append(",").append(path.getNode(i).z - zero.getZ())
                    .append(")");
        }
        return out.toString();
    }

    /** 这一 tick 变了什么；几样一起变时报最能解释的那一样。 */
    private String eventFor(
            String note,
            String next,
            String walk,
            boolean grounded,
            boolean hasPath
    ) {
        if (!note.equals(previousNote)) {
            return "NOTE";
        }
        if (hasPath != previousHadPath) {
            return hasPath ? "PATH" : "PATHGONE";
        }
        if (!next.equals(previousNext)) {
            return "NODE";
        }
        if (grounded != previousGrounded) {
            return grounded ? "LAND" : "AIR";
        }
        if (!walk.equals(previousWalk)) {
            return walk.equals("-") ? "STOP" : "GO";
        }
        return "";
    }

    private String rel(BlockPos pos) {
        return (pos.getX() - zero.getX()) + ","
                + (pos.getY() - zero.getY()) + ","
                + (pos.getZ() - zero.getZ());
    }

    private String walkTo(EntityMaid maid) {
        WalkTarget walk = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        if (walk == null) {
            return "-";
        }
        Vec3 to = walk.getTarget().currentPosition();
        return String.format("%.1f,%.1f,%.1f",
                to.x - zero.getX(), to.y - zero.getY(), to.z - zero.getZ());
    }
}
