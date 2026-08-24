package com.laixia.maidintelligence.gametest.pathing.patrol;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 领跑的那个人怎么跑。
 *
 * <p>从 {@code BridgePatrol} 按职责拆出（单文件五百行的布局纪律）。那边是
 * "车与仪表"——怎么记、怎么判卡住；这里只回答一件事：**主人这一 tick 站在
 * 哪儿**。拆开是因为它有自己的一整套教训，而那些教训与读数带无关。
 *
 * <p>三条碑文都在下面：不能瞬移（跟随的准入要他真的在走）、不能绕圈（会把
 * 他甩下窄梁）、不能按她的位置决定走不走（两种写法都死锁）。
 */
final class LeadRunner {
    /**
     * 主人奔跑的步幅：原版玩家冲刺约 0.28 格/tick，取 0.30。
     *
     * <p>**必须真的跑起来**，瞬移不算。跟随（{@code escort_owner}）的准入里
     * 有一条"他上路了"（{@code OwnerTravelWatch}）；瞬移过去再站死这条永远
     * 为假，而静止时接手的两条对新女仆也都不成立（{@code keep_company} 要好
     * 感度 32、{@code linger_near_owner} 分太低），于是她七百 tick 没有走目
     * 标——那是夹具站不住，不是她卡住。
     */
    static final double RUN_PACE = 0.30D;

    /**
     * 到站等她时来回小跑的幅度（沿两站连线，不离开走面）。
     *
     * <p>只能**沿线**跑：第一版绕圈，半径 0.6 就把他甩出一格宽的梁、悬在空
     * 中（三条测试同时红）。幅度也不能小：「他上路了」按**两次读数之间的位
     * 移**算，而读数间隔最长二十 tick——摆得比这快，两次落在同相位上他就
     * "没动过"，跟随资格当场掉线（实测供词：linger_near_owner 被
     * owner_on_the_move 挡住，她站着不动，可现场复铺六节点可达）。
     */
    private static final double WAIT_SHUTTLE = 2.5D;

    private LeadRunner() {
    }

    /**
     * 朝这一站跑一步；已经到了就沿两站连线来回小跑等她。
     *
     * <p>脚不停是必需的：站死之后"他上路了"会掉，跟随随之退场，而她可能还
     * 在半路。这条测试量的必须始终是**跟随中的跑酷**。
     */
    static void runToward(Player owner, Vec3 station, Vec3 other, int tick) {
        double toX = station.x - owner.getX();
        double toZ = station.z - owner.getZ();
        double flat = Math.hypot(toX, toZ);
        double nextX;
        double nextZ;
        if (flat > RUN_PACE) {
            nextX = owner.getX() + toX / flat * RUN_PACE;
            nextZ = owner.getZ() + toZ / flat * RUN_PACE;
        } else {
            double lineX = other.x - station.x;
            double lineZ = other.z - station.z;
            double line = Math.max(1.0E-6D, Math.hypot(lineX, lineZ));
            // 三角波而非正弦：每 tick 都实打实挪 RUN_PACE。正弦在折返点附近
            // 趋近于零，那几 tick 的位移会被读成"他没动"。
            //
            // 而且只朝**另一站**那侧摆：那一段正是她要走的路，必然可走。往
            // 站点背面摆会把他送出地面（栅栏圈实测：地面只到 x=14，站点在
            // 12.5，摆 2.5 就掉下去了）。
            double reach = Math.min(WAIT_SHUTTLE, line * 0.4D);
            double phase = (tick * RUN_PACE) % (2.0D * reach);
            double swing = phase <= reach ? phase : 2.0D * reach - phase;
            nextX = station.x + lineX / line * swing;
            nextZ = station.z + lineZ / line * swing;
        }
        // 碑：试过在这儿拦"别撞到她"，两版都死锁——「贴到两格就站住」她一追
        // 上他就不再出发（八条同时死）；「更近就不走」她站在他与下一站之间时
        // 同样死。领跑的人不能由跟随者的位置决定走不走。撞上身那一下在调用侧
        // 收拾（followPatrol 的下马），这里只管跑。
        owner.setPos(nextX, station.y, nextZ);
    }
}
