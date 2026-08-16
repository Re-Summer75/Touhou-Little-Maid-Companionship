package com.laixia.maidintelligence.feature.orchestration.tlm.ambient;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.perception.tlm.TlmLooseDrop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

/**
 * 路过的东西顺手收了——手是空闲资源，脚不是她的。
 *
 * <p>这是资源仲裁最小的一个实例，也是"跟着走和捡东西打架"的另一半解法。仲裁的
 * 语言是：**移动目标只有一个持有者**（当前活动意图的动作），别人不许写；而**手在
 * 没被占用时可以被路边的机会借走**，借的时候不碰移动。于是护送持有她的脚的整段
 * 路上，清扫仍然以"顺手"的形态存在——不绕路、不停步、不与任何意图竞争。
 *
 * <p>只收**臂展之内**的：她的碰撞箱外扩本体的拾取半径，正是 {@code pushEntities}
 * 每 tick 自动扫的那一圈再加半只手臂。伸得更远就成了隔空吸物；一格都不肯伸就和
 * 本体的自动拾取完全重合、这个类白写。
 *
 * <p>挂在环境钩子上、跑在意图之前，和跑步动画同理：它不是一个决定，是对"她正
 * 走在路上"的顺手利用。战斗中不借——那双手另有安排（{@code handsBusy} 那一套），
 * 而且弯腰捡东西的帧出现在交换节奏里怎么看都是错的。
 */
public final class TlmEnRouteScoop {
    /**
     * 臂展：拾取半径之外再伸出去的那一截。
     *
     * <p>半格。本体自动拾取是碰撞箱外扩拾取半径（默认 0.5），这里再加半格——
     * 路线旁一格内的东西够得着，两格外的仍然要清扫意图专门走一趟。
     */
    private static final double ARM_BLOCKS = 0.5D;

    private TlmEnRouteScoop() {
    }

    public static TlmEnRouteScoop create() {
        return new TlmEnRouteScoop();
    }

    public void tick(EntityMaid maid, long gameTime) {
        // 只在"正走在路上"时借手：站着的时候要么清扫意图自己会来，要么她本来
        // 就没事做，不需要这条顺手。
        if (!maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            return;
        }
        // 打着架不借。手另有安排，而且这一弯腰会出现在交换节奏正中间。
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            return;
        }
        AABB reach = maid.getBoundingBox().inflate(ARM_BLOCKS + 0.5D);
        for (Entity candidate : maid.level().getEntities(
                maid, reach, TlmLooseDrop::couldBeTaken
        )) {
            if (!TlmLooseDrop.collectable(maid, candidate)) {
                continue;
            }
            if (candidate instanceof ItemEntity item) {
                maid.pickupItem(item, false);
            }
            // 经验球和 P 点不在这里收：本体的 pushEntities 对它们的自动半径
            // 已经够用，而它们没有"塞不下"的问题，不需要多这半格臂展。
        }
    }
}
