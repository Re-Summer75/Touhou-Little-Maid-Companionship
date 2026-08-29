package com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * 实机验伪的边账本：跳了三次都没落上的边，图侧暂时当它不存在。
 *
 * <p>图的每条边都过了扫掠仿真，可仿真读的是方块形状——实机还有它读不到
 * 的事（贴栏的挤压、落点的弹回）。一条边在实机上连续验伪，执行器却只会
 * 把锅退给重规划，重规划照图又下同一单——她就贴着栏杆每一秒半起跳一次，
 * 跳满整场（栅栏圈实测；玩家定案："不知道障碍无法越过，一直尝试跳过去，
 * 导致无限重试"）。这不是执行侧另起判断，是把实机事实记进图里：验伪的
 * 边禁一阵，A* 自然去找别的路，真无路就诚实报无路。
 *
 * <p>账本挂在导航器上、跟着这一只女仆：她的失败不迁怒别人的图。
 */
public final class EdgeVeto {
    /** 同一条边计满几次起跳未到就拉黑。 */
    private static final int STRIKES = 3;

    /** 两次起跳相隔多少 tick 以内算同一轮较劲。 */
    private static final int BOUT_TICKS = 100;

    /** 拉黑时长（tick）：过期自动放回，世界可能已经变了。 */
    private static final int VETO_TICKS = 600;

    private record Edge(BlockPos from, BlockPos to) {
    }

    private record Tally(int strikes, int lastTick) {
    }

    private final Map<Edge, Tally> tallies = new HashMap<>();
    private final Map<Edge, Integer> vetoedUntil = new HashMap<>();

    private int now;

    /** 导航器每 tick 喂一次钟。 */
    public void clock(int tick) {
        this.now = tick;
    }

    /** 起跳记一笔：短窗内攒满三笔就拉黑这条边。 */
    public void strike(BlockPos from, BlockPos to) {
        Edge edge = new Edge(from.immutable(), to.immutable());
        Tally tally = tallies.get(edge);
        int strikes = tally == null || now - tally.lastTick() > BOUT_TICKS
                ? 1
                : tally.strikes() + 1;
        tallies.put(edge, new Tally(strikes, now));
        if (strikes >= STRIKES) {
            vetoedUntil.put(edge, now + VETO_TICKS);
            tallies.remove(edge);
        }
    }

    /** 到锚销账：这条边实机走通了，前科清零。 */
    public void absolve(BlockPos from, BlockPos to) {
        Edge edge = new Edge(from.immutable(), to.immutable());
        tallies.remove(edge);
        vetoedUntil.remove(edge);
    }

    /** 图侧出边前问一声：这条边禁着没有。 */
    public boolean vetoed(BlockPos from, BlockPos to) {
        Integer until = vetoedUntil.get(new Edge(from, to));
        if (until == null) {
            return false;
        }
        if (now >= until) {
            vetoedUntil.remove(new Edge(from, to));
            return false;
        }
        return true;
    }
}
