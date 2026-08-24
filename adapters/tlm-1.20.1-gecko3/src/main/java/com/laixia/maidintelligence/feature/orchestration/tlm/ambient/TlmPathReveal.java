package com.laixia.maidintelligence.feature.orchestration.tlm.ambient;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing
        .SureFootedNavigation;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

/**
 * 手持灵魂透镜时，把她眼里的那条路画出来。
 *
 * <p>为什么值得做：寻路的毛病在实机里只有一种长相——"她站着不动"或者
 * "她直挺挺撞上去"。可这一种长相底下是**两种完全不同的病**：图铺出了一条
 * 穿墙的路（她真的以为能过），还是根本没铺出路（连通性断了）。隔着截图分
 * 不出来，隔着测试也分不出来——测试里她跌跌撞撞总能蹭过去，图却是坏的。
 *
 * <p>所以这里画的不是装饰，是**判据**：
 * <ul>
 *   <li>绿色一串——路径逐节点。**粒子穿过栅栏就是图错了。**</li>
 *   <li>白色一点——她当前正走向的那个节点。</li>
 *   <li>红色一点——终点，且这条路**到不了**目标（残路）。</li>
 *   <li>头顶灰烟——根本没有路。</li>
 * </ul>
 *
 * <p>只在有人手持透镜、且在二十四格内时才发粒子；没人看的时候一点开销
 * 都不该有。
 */
public final class TlmPathReveal {
    /** 几 tick 画一次：粒子有寿命，太密只是浪费带宽。 */
    private static final int INTERVAL_TICKS = 4;

    /** 谁在这个半径内手持透镜，就为谁画。 */
    private static final double WATCH_RADIUS = 24.0D;

    /** 一条路最多画这么多节点，免得远路把屏幕铺满。 */
    private static final int MAX_NODES = 48;

    private TlmPathReveal() {
    }

    public static TlmPathReveal create() {
        return new TlmPathReveal();
    }

    public void tick(EntityMaid maid, long gameTime) {
        if (gameTime % INTERVAL_TICKS != 0
                || !(maid.level() instanceof ServerLevel level)) {
            return;
        }
        if (!watched(level, maid)) {
            return;
        }
        Path path = maid.getNavigation().getPath();
        if (path == null || path.isDone()) {
            // 没有路：头顶一缕灰烟。**这和"路穿过了墙"是两种病**，看得见
            // 才分得开。
            level.sendParticles(ParticleTypes.SMOKE,
                    maid.getX(), maid.getEyeY() + 0.6D, maid.getZ(),
                    2, 0.05D, 0.05D, 0.05D, 0.0D);
            return;
        }
        int shown = 0;
        for (int i = path.getNextNodeIndex();
                i < path.getNodeCount() && shown < MAX_NODES; i++, shown++) {
            Node node = path.getNode(i);
            level.sendParticles(nodeMark(path, i),
                    node.x + 0.5D, node.y + 0.25D, node.z + 0.5D,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * 这个节点画成什么样：当前要去的、到不了的终点、还是路上的一段。
     */
    private static ParticleOptions nodeMark(Path path, int index) {
        if (index == path.getNextNodeIndex()) {
            return ParticleTypes.END_ROD;
        }
        if (index == path.getNodeCount() - 1 && !path.canReach()) {
            return ParticleTypes.ANGRY_VILLAGER;
        }
        return ParticleTypes.HAPPY_VILLAGER;
    }

    /** 附近有没有人举着透镜在看。 */
    private static boolean watched(ServerLevel level, EntityMaid maid) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(maid) <= WATCH_RADIUS * WATCH_RADIUS
                    && holdingLens(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判据只看物品的注册名，不引平台层的类。
     *
     * <p>这个包是本体适配层，看不见 Forge 侧的 {@code SoulLensItem}；而为了
     * 一句判断把依赖方向倒过来不值得。注册名是两边都认的同一个事实。
     */
    private static boolean holdingLens(Player player) {
        return isLens(player.getMainHandItem())
                || isLens(player.getOffhandItem());
    }

    private static boolean isLens(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem().builtInRegistryHolder().key().location()
                        .getPath().equals("soul_lens");
    }

    /** 执行器这一 tick 走的分支，给洞察面板配一行字用。 */
    public static String noteOf(EntityMaid maid) {
        return maid.getNavigation() instanceof SureFootedNavigation sure
                ? sure.pathwalkNote()
                : "-";
    }
}
