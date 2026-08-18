package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.SureFootedNavigation;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.tlm.MaidIntentBehavior;
import com.laixia.maidintelligence.feature.orchestration.tlm.ambient.TlmEnRouteScoop;
import com.laixia.maidintelligence.feature.orchestration.tlm.ambient.TlmIdleGaze;
import com.laixia.maidintelligence.feature.orchestration.tlm.ambient.TlmSprint;
import com.laixia.maidintelligence.feature.orchestration.tlm.ambient.TlmWeaponStow;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.guard.ProjectileDodge;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.List;
import java.util.Objects;

public final class BehaviorExtraBrain implements IExtraMaidBrain {
    private static final int INTENT_PRIORITY = 4;

    private final MaidIntentApi<EntityMaid> intents;
    private final TlmDeployBoatAutonomy boatAutonomy;

    public BehaviorExtraBrain(
            MaidIntentApi<EntityMaid> intents,
            TlmDeployBoatAutonomy boatAutonomy
    ) {
        this.intents = Objects.requireNonNull(
                intents,
                "intents"
        );
        this.boatAutonomy = Objects.requireNonNull(
                boatAutonomy,
                "boatAutonomy"
        );
    }

    /**
     * 每 tick 都发生、与"她此刻在做什么"无关的那些。
     *
     * <p>几件事共用一个钩子，而它跑在意图**之前**：环顾写下的注视目标会被任何有事
     * 做的意图盖掉，于是"看哪儿"成了默认值而不是又一个决定——不需要协调器，也不会
     * 有第二个写入者去抢同一块记忆。
     *
     * <p>它们都不是意图：不占她的脚、不与任何东西竞争，也不该按 band 被收回许可。
     * 一个正被什么东西盯着的人**更**该四处看。
     *
     * <p>闪避挂在这里而不是交战动作里，理由是同一个：被冷箭射中不需要她先决定
     * "我在打架"，射她的那一个也可能根本不在她视野里——她看见的是箭。它确实会占
     * 她的脚，但只占到落点为止，而且是安全那一档，本来就压得过任何别的事。
     */
    private void ambient(EntityMaid maid, long gameTime) {
        // 寻路升级守卫：拿着裸的地面导航就换成会查立足点的那个。放在最前，
        // 因为这一 tick 里随后写下的任何移动目标都该用升级后的评估。
        SureFootedNavigation.upgrade(maid);
        boatAutonomy.tick(maid, gameTime);
        gaze.tick(maid, gameTime);
        stow.tick(maid, gameTime);
        // 跑不跑也归这里：它不是一个决定，是对"她此刻在赶多远的路"的复述。放在
        // 意图之前，读的就是上一 tick 落定的那个移动目标——一 tick 的滞后正好，
        // 因为它描述的本来就是她已经在走的那段路。
        sprint.tick(maid, gameTime);
        // 路过的东西顺手收了。手是空闲资源，脚不是她的——不绕路、不停步、
        // 不与任何意图竞争，这正是"跟着走还能顺手捡"的那一半。
        scoop.tick(maid, gameTime);
        ProjectileDodge.consider(maid);
    }

    private final TlmIdleGaze gaze = TlmIdleGaze.create();
    private final TlmWeaponStow stow = TlmWeaponStow.create();
    private final TlmSprint sprint = TlmSprint.create();
    private final TlmEnRouteScoop scoop = TlmEnRouteScoop.create();

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>>
    getCoreBehaviors() {
        return List.of(
                Pair.of(
                        INTENT_PRIORITY,
                        new MaidIntentBehavior(
                                intents,
                                this::ambient
                        )
                )
        );
    }
}
