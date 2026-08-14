package com.laixia.maidintelligence.feature.orchestration.tlm.errand.company;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.tlm.MaidSeatAutonomyBridge;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .EntityApproachTarget;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .MaintainProximityErrand;
import net.minecraft.world.entity.LivingEntity;

/**
 * 跟上驯服她的那个人。
 *
 * <p>起身离座是其中一步。跟随此前由宿主的任务代劳，那个任务让位之后，坐在凳子上的
 * 女仆会眼睁睁看着主人走掉而一动不动。
 *
 * <p>放在 {@code company} 包下，因为文件位置要说得出这件事属于哪一类。分类轴就是
 * 中断 band，规则见 {@code docs/architecture/behavior-spec.md}。
 */
public final class FollowOwnerErrand {
    private FollowOwnerErrand() {
    }

    public static MaintainProximityErrand create() {
        return new MaintainProximityErrand(
                "follow_owner",
                (maid, gameTime) -> {
                    LivingEntity owner = maid.getOwner();
                    return owner == null
                            || !owner.isAlive()
                            || owner.isSpectator()
                            ? null
                            : new EntityApproachTarget(owner);
                },
                MaidSeatAutonomyBridge::leaveSeatForFollow
        );
    }
}
