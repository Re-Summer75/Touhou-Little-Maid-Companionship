package com.laixia.maidintelligence.feature.orchestration.tlm.errand.leisure;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.OwnerLingerPolicy;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .ApproachTarget;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .BlockApproachTarget;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .EntityApproachTarget;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.Errand;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .MaintainProximityErrand;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.FootingRule;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 主人停下来的时候，在他附近找个地方待着。
 *
 * <p>自由模式没有游走：宿主的 {@code RANDOM_STROLL} 被整个删掉了，因为它是第二个
 * 决策者，会把撤退的移动目标抢走。删得对，但剩下的画面是主人一停她就钉在原地，
 * 看上去像一件家具而不像一个人。
 *
 * <p>所以游走以她自己的意图回来，走的是和跟随、回家同一套骨架——没有第二个往
 * {@code WALK_TARGET} 上写的东西，那正是当初删掉它的理由。落点圆心在主人身上而不在
 * 她身上：锚在自己身上的随机游走会漂走，锚在主人身上的每一步都重新以他为圆心，
 * 所以她只会在他附近打转。
 *
 * <p>有一定概率落点**就是主人本人**，那一趟就成了"回到他身边"。这不是另一条规则，
 * 是同一次抽签的另一个结果。
 *
 * <p>归在 {@code leisure} 包下，与消遣、落座同属自娱 band。这三段状态（记住落点、
 * 到期、到站发呆）曾经住在 {@code MaintainProximityErrand} 里，仅仅因为差事目录满了
 * 没有文件位——那正是这次按 band 分包要解决的事。
 */
public final class LingerNearOwnerErrand {
    private LingerNearOwnerErrand() {
    }

    public static MaintainProximityErrand create() {
        Map<EntityMaid, Chosen> chosen = new WeakHashMap<>();
        return new MaintainProximityErrand(
                "linger_near_owner",
                (maid, gameTime) -> lingerSpot(maid, gameTime, chosen),
                MaintainProximityErrand.NOTHING_TO_PREPARE,
                (maid, target) -> spotStillNearOwner(maid, target),
                (maid, gameTime) -> chosen.put(maid, restAfter(maid, gameTime)),
                // 这一趟的步速，和落点一起抽、一起记。散步不是赶路，所以整体比标称
                // 慢；而每趟不一样与停顿不等长是同一条理由。**必须跟着落点一起记
                // 住**：每 tick 重掷会让她一步快一步慢地抽搐，与目标必须稳定是同一
                // 条契约。
                maid -> {
                    Chosen remembered = chosen.get(maid);
                    return remembered == null ? 1.0D : remembered.pace();
                }
        );
    }

    /**
     * 她此刻认着的那一点，或者一段发呆。
     *
     * <p>{@code target} 为 null 表示"到了，正在站着"，站到 {@code until} 为止。
     * 一个记录同时表达这两件事，是因为它们本来就是同一个状态机的两个相：走过去、
     * 站一会儿、再挑一个。
     */
    private record Chosen(
            ApproachTarget target,
            long at,
            long until,
            double pace
    ) {
    }

    /**
     * 到站了，站一会儿。
     *
     * <p>没有这一段她就一直在走：评估间隔只有两秒，到站两秒后又出发。而给意图配
     * 一个固定冷却同样不行——那是等长的停顿，无论取多大都像节拍器。停多久因此和
     * 去哪儿一起抽，见 {@link OwnerLingerPolicy#restTicks}。
     */
    private static Chosen restAfter(EntityMaid maid, long gameTime) {
        int rest = OwnerLingerPolicy.INSTANCE.restTicks(
                maid.getRandom().nextDouble()
        );
        return new Chosen(null, gameTime, gameTime + rest, 1.0D);
    }

    /**
     * 这一趟去哪儿：他身边，或者他周围圆内的一点。
     *
     * <p>记住的那一点一直用到走完为止。三种情况重抽：还没抽过；主人已经从那一点
     * 旁边走开、它不再算"他附近"；或者**认了太久还没到**。
     *
     * <p>第三条是安全网，不是配平。抽出来的点不保证走得到——路可能被切断、可能在
     * 悬崖对面。到不了就不会到站，不到站就不会被忘记，于是她被钉在原地反复走向一个
     * 够不着的点。玩家看到的是"她不动了"，而那个坏法从"到站才忘记"这一条里直接
     * 长出来。
     */
    private static ApproachTarget lingerSpot(
            EntityMaid maid,
            long gameTime,
            Map<EntityMaid, Chosen> chosen
    ) {
        LivingEntity owner = maid.getOwner();
        if (owner == null || !owner.isAlive() || owner.isSpectator()) {
            chosen.remove(maid);
            return null;
        }
        Chosen remembered = chosen.get(maid);
        if (remembered != null && gameTime < remembered.until()) {
            // 正站着发呆。答"没有值得去的地方"，而不是原地假装走一趟——差事本来
            // 就用返回空表示这个意思，引擎会把这一轮让给别的意图。
            return null;
        }
        if (remembered != null
                && remembered.target() != null
                && gameTime - remembered.at() < OwnerLingerPolicy.PATIENCE_TICKS
                && spotStillNearOwner(maid, remembered.target())) {
            return remembered.target();
        }
        ApproachTarget picked = rollSpot(maid, owner);
        chosen.put(maid, new Chosen(
                picked,
                gameTime,
                gameTime,
                OwnerLingerPolicy.INSTANCE.paceFactor(
                        maid.getRandom().nextDouble()
                )
        ));
        return picked;
    }

    /**
     * 抽一次签。
     *
     * <p>水平位置是抽出来的，高度不是——高度只能由地形回答。沿用主人的高度会把点
     * 放进山体里或半空中，而 {@code ApproachAndCommitAction} 判"到了没有"量的是三维
     * 距离，于是那种点她永远到不了。
     *
     * <p>解析到地面之后再用**同一条**"还算不算他附近"筛一遍：解析结果可能落到他
     * 楼上或崖底。这里不引入"高度差上限"那样的第三个数——要问的问题和落点过期时
     * 问的完全一样，只是时机不同。筛掉的那一趟改成回到他身边，那本来就是同一次
     * 抽签里的另一支。
     */
    private static ApproachTarget rollSpot(EntityMaid maid, LivingEntity owner) {
        OwnerLingerPolicy policy = OwnerLingerPolicy.INSTANCE;
        RandomSource random = maid.getRandom();
        if (policy.returnsToOwner(random.nextDouble())) {
            return new EntityApproachTarget(owner);
        }
        double bearing = random.nextDouble();
        double reach = random.nextDouble();
        BlockPos ground = maid.level().getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(
                        owner.getX() + policy.offsetX(bearing, reach),
                        owner.getY(),
                        owner.getZ() + policy.offsetZ(bearing, reach)
                )
        );
        // 高度图会把**细柱的顶**当成地面（末地烛是 MOTION_BLOCKING）：抽
        // 中柱列时落点解析到柱顶，她就被自己的散步指路上了烛顶——到站发
        // 呆、下一趟下来、再抽中再上去，玩家看到的是她在烛尖上循环打转
        // （实测：台上两根烛，中签率高得像必然）。散步不落柱尖：脚下那格
        // 是细柱的换成回他身边，那本来就是同一次抽签的另一支。
        if (FootingRule.slimPillar(maid.level()
                .getBlockState(ground.below())
                .getCollisionShape(maid.level(), ground.below()))) {
            return new EntityApproachTarget(owner);
        }
        ApproachTarget spot = new BlockApproachTarget(ground);
        return spotStillNearOwner(maid, spot)
                ? spot
                : new EntityApproachTarget(owner);
    }

    /**
     * 那个点还算"他附近"吗。
     *
     * <p>不重新抽签——重抽的话每一 tick 的目标都不一样，这趟永远走不完（见
     * {@link Errand#find} 上的稳定性契约）。判据是主人有没有从这个点旁边走开：
     * 他走开了，这个点就不再是他附近，而追着一个过期的落点走是玩家一眼能看出来
     * 的呆。
     */
    private static boolean spotStillNearOwner(
            EntityMaid maid,
            ApproachTarget target
    ) {
        LivingEntity owner = maid.getOwner();
        if (owner == null || !owner.isAlive() || owner.isSpectator()
                || !target.valid()) {
            return false;
        }
        return OwnerLingerPolicy.INSTANCE.stillNearOwner(
                target.tracker().currentPosition().distanceTo(owner.position())
        );
    }
}
