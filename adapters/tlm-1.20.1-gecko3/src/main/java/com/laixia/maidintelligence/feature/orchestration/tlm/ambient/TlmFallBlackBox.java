package com.laixia.maidintelligence.feature.orchestration.tlm.ambient;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.SureFootedNavigation;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * 摔落黑匣子：她摔下去的那一刻，把之前六十 tick 的逐帧记录倒进日志。
 *
 * <p>为什么要装在**游戏里**而不是只在测试里：玩家实机"必然复现"的那种摔
 * （悬空门板上转身出沿），夹具照着口述逐格重建、补上腾空动量、补上斜拉，
 * 咬合验证仍然两轮全绿——现场还有配料是转述不出来的。与其继续猜，把测试
 * 用的那条读数带原样搬进实机：她一摔，日志里就是完整现场，逐 tick 的位置、
 * 速度、竖直分量、着地、执行分支、下一节点、走目标，全都在。
 *
 * <p>只记录，不干预；只在自由模式下待命（别的差事她的脚归宿主管，摔不摔
 * 都轮不到这套执行器负责）。判据：站过的最高地面往下掉两格半、且不在水
 * 里——正常的下崖边最深六格，可那种是**有落点的**，落地就收；这里抓的是
 * "跌出行走面"的那一种，两格半时先把带子倒出来，摔到底还是被接住都不影响
 * 供词已经在手。倒带后冷却十秒，别把一次长坠摔成刷屏。
 */
public final class TlmFallBlackBox {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 留多少 tick 的底：够看清"她是怎么走到那一步的"。 */
    private static final int TAPE_TICKS = 60;

    /** 站过的最高地面往下掉这么多就倒带。 */
    private static final double FELL_BLOCKS = 2.5D;

    /** 倒带后的冷却（tick）：一次长坠只倒一次。 */
    private static final int COOLDOWN_TICKS = 200;

    private final String[] rows = new String[TAPE_TICKS];
    private int written;
    private double highestGround = Double.NEGATIVE_INFINITY;
    private long quietUntil;

    public static TlmFallBlackBox create() {
        return new TlmFallBlackBox();
    }

    private TlmFallBlackBox() {
    }

    /** 每 tick 一行进环形带；跌出行走面就整卷倒进日志。 */
    public void tick(EntityMaid maid, long gameTime) {
        if (maid.onGround()) {
            highestGround = Math.max(highestGround, maid.getY());
        }
        rows[(int) (written++ % TAPE_TICKS)] = row(maid, gameTime);
        if (gameTime < quietUntil
                || maid.isInWater()
                || maid.getY() > highestGround - FELL_BLOCKS) {
            return;
        }
        quietUntil = gameTime + COOLDOWN_TICKS;
        StringBuilder tape = new StringBuilder(
                "[maid-fall] 她跌出了行走面（最高站面 y="
                        + String.format("%.2f", highestGround)
                        + "），之前六十 tick：\n");
        tape.append("t\tpos\tvel\tvy\tgnd\tnote\tnxt\twalkTo\n");
        long oldest = Math.max(0, written - TAPE_TICKS);
        for (long at = oldest; at < written; at++) {
            String kept = rows[(int) (at % TAPE_TICKS)];
            if (kept != null) {
                tape.append(kept).append('\n');
            }
        }
        LOGGER.warn(tape.toString());
        highestGround = maid.getY();
    }

    /** 与测试读数带同款的一行；导航不是我们的就报它是谁。 */
    private String row(EntityMaid maid, long gameTime) {
        PathNavigation nav = maid.getNavigation();
        String note = nav instanceof SureFootedNavigation sure
                ? sure.pathwalkNote()
                : nav.getClass().getSimpleName();
        Path path = nav.getPath();
        BlockPos next = path == null || path.isDone()
                ? null
                : path.getNextNodePos();
        Vec3 velocity = maid.getDeltaMovement();
        String walk = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(target -> {
                    Vec3 to = target.getTarget().currentPosition();
                    return String.format("%.1f,%.1f,%.1f", to.x, to.y, to.z);
                })
                .orElse("-");
        return String.format("%d\t%.2f,%.2f,%.2f\t%.2f\t%+.2f\t%s\t%s\t%s",
                gameTime,
                maid.getX(), maid.getY(), maid.getZ(),
                Math.hypot(velocity.x, velocity.z),
                velocity.y,
                maid.onGround() ? "y" : (maid.isInWater() ? "WATER" : "AIR"),
                note,
                next == null ? "-" : next.toShortString())
                + "\t" + walk;
    }
}
