package com.laixia.maidintelligence.feature.behavior.pathing;

import com.laixia.maidintelligence.feature.behavior.domain.motion.BallisticArc;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.sweep
        .LeapContract;

/**
 * 起跳合同的自洽：**按它给的初速飞，真能飞到它承诺的距离吗**。
 *
 * <p>这一族判据此前只有 GameTest 验——起一个服务器、造一个世界、跑几百
 * tick，就为了问一句算术。而 {@code LeapContract} 与 {@code BallisticArc}
 * 都是零依赖的纯函数，秒级就能问完（测试章程 L0：能在上一层验的，不要放
 * 到下一层）。
 *
 * <p>三件事：空气阻力是复利不是折扣（按 v·t 配速会系统性欠冲）、封顶与
 * 下限真的夹得住、以及**合同与仿真同源**——同一条弧线方程，图侧用它定
 * 价、执行侧用它配速，两边不能各算各的。
 */
public final class LeapContractVerification {
    /** 允许的相对误差：等比数列求和与逐 tick 迭代之间的浮点差。 */
    private static final double SLACK = 0.02D;

    private LeapContractVerification() {
    }

    public static void main(String[] args) {
        arcIsCompound();
        speedStaysInBand();
        contractReachesItsPromise();
        System.out.println("LeapContract: 3 checks passed");
    }

    /**
     * 空气阻力是**复利**：走过的路是等比数列的和，不是 v×t。
     *
     * <p>按 v·t 配速会系统性欠冲（同层短一成七、上一格短两成五），这是
     * {@code paceFor} 存在的全部理由。这里把两种算法都算一遍，确认差距
     * 真的在那个量级——否则说明有人把公式改回了线性。
     */
    private static void arcIsCompound() {
        double ticks = LeapContract.AIRBORNE_TICKS;
        double distance = 3.0D;
        double compound = LeapContract.paceFor(distance, ticks);
        double naive = distance / ticks;
        if (compound <= naive) {
            throw new AssertionError(
                    "复利配速反而不比线性快：compound=" + compound
                            + " naive=" + naive);
        }
        double gain = compound / naive;
        if (gain < 1.2D || gain > 2.5D) {
            throw new AssertionError(
                    "复利与线性的比值离谱（应在 1.2..2.5）：" + gain);
        }
    }

    /** 封顶与下限夹得住：再远也不超速，再近也迈得动。 */
    private static void speedStaysInBand() {
        for (double distance = 0.1D; distance <= 12.0D; distance += 0.1D) {
            for (int dy = -1; dy <= 1; dy++) {
                double speed = LeapContract.launchSpeed(distance, dy);
                if (speed > LeapContract.MAX_LEAP_SPEED + 1.0E-9D
                        || speed < LeapContract.MIN_LEAP_SPEED - 1.0E-9D) {
                    throw new AssertionError("初速出界：distance=" + distance
                            + " dy=" + dy + " speed=" + speed);
                }
            }
        }
    }

    /**
     * **合同兑现**：按 {@code launchSpeed} 给的初速，逐 tick 推演真能飞
     * 到那个距离。
     *
     * <p>只查**两头都没被夹住**的那一段。封顶之外合同承诺不了（那是"跳
     * 不了这么远"，由图侧的跨度上限拦）；下限之内它故意飞过头——最小初
     * 速是保证她迈得动的托底，短跳因此必然超出，那不是欠账。第一次写这
     * 条时漏了下限，distance=1.0 当场红给我看（承诺 1.05、实飞 1.43）。
     */
    private static void contractReachesItsPromise() {
        int checked = 0;
        for (double distance = 1.0D; distance <= 4.0D; distance += 0.25D) {
            double speed = LeapContract.launchSpeed(distance, 0);
            if (speed >= LeapContract.MAX_LEAP_SPEED - 1.0E-9D
                    || speed <= LeapContract.MIN_LEAP_SPEED + 1.0E-9D) {
                continue;
            }
            checked++;
            double flown = flightOf(speed, LeapContract.AIRBORNE_TICKS);
            double want = distance * LeapContract.LEAP_MARGIN;
            if (Math.abs(flown - want) > want * SLACK) {
                throw new AssertionError("合同飞不到承诺的距离：want="
                        + want + " flown=" + flown + " (distance="
                        + distance + ")");
            }
        }
        // 夹子若哪天收紧到把整段都吞掉，这条就成了空转的绿灯——宁可红。
        if (checked < 4) {
            throw new AssertionError(
                    "可查的跨度只剩 " + checked + " 档，合同的有效区间太窄");
        }
    }

    /** 逐 tick 推演水平位移——与执行侧用的是同一个衰减。 */
    private static double flightOf(double speed, double ticks) {
        double gone = 0.0D;
        double now = speed;
        for (int tick = 0; tick < (int) Math.round(ticks); tick++) {
            gone += now;
            now *= BallisticArc.HORIZONTAL_DRAG;
        }
        return gone;
    }
}
