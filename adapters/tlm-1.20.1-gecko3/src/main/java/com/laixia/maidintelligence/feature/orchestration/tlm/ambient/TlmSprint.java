package com.laixia.maidintelligence.feature.orchestration.tlm.ambient;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.mixin.common.EntitySharedFlagsAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * 她动得够快的时候，播跑步动画。
 *
 * <p>本体的模型里 `run` 这个主状态一直都在，判据是 {@code Mob.isSprinting()}
 * （`AnimationRegister`）。自由模式下没有任何东西翻过那个标志位，所以那段动画一次
 * 也没播过。缺的是标志位，不是动画。
 *
 * <h2>只是动画，不是冲刺</h2>
 *
 * <p>{@code LivingEntity.setSprinting} 覆写了 {@code Entity} 的同名方法，额外挂上
 * {@code SPEED_MODIFIER_SPRINTING}——**移动速度 +30%**。走那个入口就等于顺手给了她
 * 一份本来没有的能力，会改掉撤退、追击、"跑不跑得掉"整套算术。
 *
 * <p>所以这里走 {@link EntitySharedFlagsAccessor}，直接翻 {@code Entity} 的第 3 号
 * 共享标志位：客户端照样收到、照样选 `run`，而属性表一动不动。**她的速度与这段代码
 * 存在与否完全无关。**
 *
 * <h2>判据只有一个：她实际动多快</h2>
 *
 * <p>不看她在赶路还是在打架，不看她要去哪、还有多远。跑步动画描述的是**一个人此刻
 * 移动的样子**，而"她在做什么"跟那个样子没有必然关系——战斗里冲上去砍的那几格和
 * 穿过基地回家的那几十格，腿的动作是一样的。把它绑在某个状态上，就会出现"她明明在
 * 飞奔却走着"和"她明明在挪半格却跑着"两种别扭，而且每加一个新行为都要回来补一笔。
 *
 * <p>门槛取**原版玩家冲刺的速度**（5.612 格/秒）：那是原版认定"一个人型生物在跑"的
 * 速度，用它量她是拿玩家的尺子量她，与本模组其它地方一致。
 *
 * <h2>回差是必需的</h2>
 *
 * <p>速度是个会在门槛附近来回穿的量——转弯、上坎、被撞、寻路节点之间都会掉一下。
 * 直接拿它开关动画，玩家看到的就是走跑之间抽搐。这正是 {@code behavior-spec.md}
 * 第五节那条：**判据只用来开始和刷新一个姿态，不用来逐 tick 驱动动作**。所以起跑和
 * 收腿用两个门槛，中间那段维持原状。
 */
public final class TlmSprint {
    /**
     * 起跑门槛：原版玩家冲刺的速度，格/tick。
     *
     * <p>5.612 格/秒 ÷ 20 tick。原版里这就是"在跑"的速度。
     */
    public static final double RUNNING_PACE = 5.612D / 20.0D;

    /**
     * 收腿门槛：原版玩家步行的速度，格/tick。
     *
     * <p>4.317 格/秒 ÷ 20 tick。掉回步行速度才算不再跑——两个门槛之间那一段维持
     * 原状，抖动就跨不过去。两个数都是原版的，回差宽度不是自己拍的。
     */
    private static final double WALKING = 4.317D / 20.0D;

    private TlmSprint() {
    }

    public static TlmSprint create() {
        return new TlmSprint();
    }

    /**
     * 每 tick 一次。
     *
     * <p>只在要翻转时才写：共享标志位一写就会进同步队列，每 tick 无条件写等于给每个
     * 女仆每 tick 发一次没有变化的更新。
     */
    public void tick(EntityMaid maid, long gameTime) {
        boolean running = maid.isSprinting();
        double pace = pace(maid);
        if (!running && pace >= RUNNING_PACE) {
            setRunning(maid, true);
        } else if (running && pace < WALKING) {
            setRunning(maid, false);
        }
    }

    /**
     * 上次问她到现在，她水平挪了多远。
     *
     * <p>**自己记上一次的位置，不借用现成的字段。**两个看着更省事的写法都试过，都是
     * 错的，而且错得不一样：
     *
     * <ul>
     *   <li>{@code position() - xo}：{@code xo} 在实体 tick 一开始就刷成当前位置，
     *       而这段代码跑在 brain 里、在移动**之前**，所以这个差恒为零。表现是她跑得
     *       再快也不换动画。</li>
     *   <li>{@code getDeltaMovement()}：在这个相位上读出来是零。</li>
     * </ul>
     *
     * <p>两次都是**同一个量在不同相位上给出不同答案**——夹具在实体 tick 之后采样，
     * 量到的是真位移，于是"她确实跑起来了"和"她没播动画"同时成立。自己记位置就与
     * 相位无关：不管这段代码在一 tick 里的哪个位置被调用，两次调用之间她挪了多少
     * 就是多少。
     *
     * <p>竖直分量剔掉，否则下落会被读成跑得飞快——而离地那一档在模型里本来就归
     * `jump`，优先级还高于 `run`。
     */
    private double pace(EntityMaid maid) {
        Vec3 now = maid.position();
        Vec3 last = previous.put(maid, now);
        if (last == null) {
            return 0.0D;
        }
        return Math.hypot(now.x - last.x, now.z - last.z);
    }

    /**
     * 上一 tick 她在哪。
     *
     * <p>存它是因为派生不出来——"挪了多远"要跨 tick 才有意义，而实体自带的那几个
     * 字段在这个相位上都答不了（见 {@link #pace}）。所有者只有这个类，弱引用随实体
     * 卸载一起走。
     */
    private final java.util.Map<EntityMaid, Vec3> previous =
            new java.util.WeakHashMap<>();

    private static void setRunning(EntityMaid maid, boolean running) {
        ((EntitySharedFlagsAccessor) maid).maidIntelligence$setSharedFlag(
                3, running
        );
    }
}
