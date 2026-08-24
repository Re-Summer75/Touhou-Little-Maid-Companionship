package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.SureFootedNavigation;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.tlm.MaidIntentBehavior;
import com.laixia.maidintelligence.feature.orchestration.tlm.ambient.TlmAttackMemoryJanitor;
import com.laixia.maidintelligence.feature.orchestration.tlm.ambient.TlmEnRouteScoop;
import com.laixia.maidintelligence.feature.orchestration.tlm.ambient.TlmFallBlackBox;
import com.laixia.maidintelligence.feature.orchestration.tlm.ambient.TlmIdleGaze;
import com.laixia.maidintelligence.feature.orchestration.tlm.ambient.TlmSprint;
import com.laixia.maidintelligence.feature.orchestration.tlm.ambient.TlmPathReveal;
import com.laixia.maidintelligence.feature.orchestration.tlm.ambient.TlmStillnessBox;
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
        steering = this.intents;
    }

    /**
     * 真正在驱动她的那一个编排器——**供词专用**。
     *
     * <p>不是多余的：注册表里那一个未必是附加脑手里这一个。实测撞见过——同
     * 一条测试、同一份代码，一轮的供词是完整轨迹，另一轮却是原样的 idle()，
     * 而附加脑的出勤计数显示它跑了五百多次。查注册表等于碰运气，而据此下的
     * 结论（"意图层对她一无所知"）把真凶所在的整个层都排除掉了。
     *
     * <p>写在这里，供词就永远问的是掌舵的那一个。
     */
    private static MaidIntentApi<EntityMaid> steering;

    /** 掌舵的那个编排器；组装完成之前为 null。 */
    public static MaidIntentApi<EntityMaid> steeringIntents() {
        return steering;
    }

    /**
     * 这只女仆的附加脑跑过多少次、最后一次是哪一 tick。
     *
     * <p>为了了断一个死结：实测供词里，执行器证明附加脑**跑过**（升级导航
     * 是它的第一行，而且全仓只有那一处），可紧邻下一行的意图层对她**一无所
     * 知**（inspect 返回原样的 idle()），中间没有任何异常。两件事不可能同时
     * 为真——除非它们跑在**不同的实例**上，或者那一行根本没被执行到。
     *
     * <p>数字能分开这两种：跑了九百次而编排器一无所知，那是实例的问题（不
     * 是她的病）；只跑了一两次，那是附加脑很早就停了，该往准入条件查。猜了
     * 三轮都没结论，改成数。
     */
    private static final java.util.Map<EntityMaid, long[]> AMBIENT_RUNS =
            new java.util.WeakHashMap<>();

    /** 记三个数：跑过几次、第一次、最后一次。 */
    private static final int RUNS = 0;
    private static final int FIRST = 1;
    private static final int LAST = 2;

    /** 附加脑对这只女仆的出勤记录，测试卡住时当供词打出来。 */
    public static String ambientDiary(EntityMaid maid) {
        long[] seen = AMBIENT_RUNS.get(maid);
        if (seen == null) {
            return "附加脑=从没跑过";
        }
        // 首末两端一起报。同样是"跑了 135 次"，散布在九百 tick 里（每 tick
        // 被什么挡掉）和集中在最后 135 tick 里（很晚才启动）是两种病，只报
        // 次数分不出来。
        return "附加脑=跑了" + seen[RUNS] + "次，t=" + seen[FIRST]
                + "…" + seen[LAST] + "（跨度"
                + (seen[LAST] - seen[FIRST] + 1) + "）";
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
        long[] seen = AMBIENT_RUNS.computeIfAbsent(
                maid, m -> new long[]{0L, gameTime, gameTime});
        seen[RUNS]++;
        seen[LAST] = gameTime;
        // 寻路升级守卫：拿着裸的地面导航就换成会查立足点的那个。放在最前，
        // 因为这一 tick 里随后写下的任何移动目标都该用升级后的评估。
        SureFootedNavigation.upgrade(maid);
        // 攻击记忆的门房：指着尸体/感知外的记忆当场清。放在意图之前，这一
        // tick 的资格判据读到的才是干净的记忆。
        janitor.tick(maid, gameTime);
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
        // 静止黑匣子：十秒纹丝不动就倒状态。只记录，不干预。
        stillness.tick(maid, gameTime);
        // 摔落黑匣子：跌出行走面就把之前六十 tick 的读数带倒进日志。只记录，不干预。
        fallBox.tick(maid, gameTime);
        // 手持灵魂透镜时把她眼里的路画出来（只在有人看时才发粒子）。
        pathReveal.tick(maid, gameTime);
    }

    private final TlmIdleGaze gaze = TlmIdleGaze.create();
    private final TlmWeaponStow stow = TlmWeaponStow.create();
    private final TlmSprint sprint = TlmSprint.create();
    private final TlmEnRouteScoop scoop = TlmEnRouteScoop.create();
    private final TlmStillnessBox stillness = TlmStillnessBox.create();
    private final TlmFallBlackBox fallBox = TlmFallBlackBox.create();
    private final TlmPathReveal pathReveal = TlmPathReveal.create();
    private final TlmAttackMemoryJanitor janitor =
            TlmAttackMemoryJanitor.create();

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
