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
        rows.add(String.join("\t",
                "tick", "ev", "pos", "mv", "vel", "gnd",
                "note", "nxt", "end", "reach", "prog", "walkTo"));
    }

    /** 记这一 tick；没变化且心跳未到就什么都不写。 */
    public void sample(long tick, EntityMaid maid) {
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
                grounded ? "y" : "AIR",
                note,
                next,
                path == null ? "-" : rel(path.getEndNode().asBlockPos()),
                path == null ? "-" : path.canReach() ? "y" : "NO",
                path == null
                        ? "-"
                        : path.getNextNodeIndex() + "/" + path.getNodeCount(),
                walk
        ));
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

    /** 一条路的完整证词：能否到站 + 逐节点相对坐标。探图用。 */
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
