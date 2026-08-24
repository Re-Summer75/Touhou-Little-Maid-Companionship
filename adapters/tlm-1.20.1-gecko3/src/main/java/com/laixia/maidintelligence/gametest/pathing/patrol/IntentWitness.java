package com.laixia.maidintelligence.gametest.pathing.patrol;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.BehaviorExtraBrain;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMode;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;

/**
 * 她停下来的那一刻，谁在掌舵。
 *
 * <p>寻路供词答不了"为什么没有走目标"——那是意图层的事。栅栏圈实测：她已
 * 经出了圈、路还活着（14/18，reach=y）、前面一路空地，可走目标在 t185 被抹
 * 掉之后再没挂回来，此后七百 tick 速度恒为 0.00。而当时的寻路供词是
 * {@code note=walk hardStops=0 watchdogs=0 leaps=0 climbs=0 hops=0 drops=0}
 * ——一个字都解释不了，因为脚没毛病：是**没人叫她走**。
 *
 * <p>所以卡住的供词里必须有这一句：当时哪件差事在跑、什么状态、候选里谁得
 * 了多少分、上一次换挡是什么。"她走不动"和"她没被叫去走"是两种病，修法南
 * 辕北辙，而只看坐标分不出来。
 */
final class IntentWitness {
    private IntentWitness() {
    }

    /** 掌舵的那件差事 + 候选打分；取不到就照实说取不到，不要猜。 */
    static String of(EntityMaid maid) {
        try {
            MaidIntentApi<EntityMaid> api = intents();
            IntentTrace trace = api.inspect(maid);
            StringBuilder out = new StringBuilder();
            // 自由模式是整个附加脑的准入（MaidIntentBehavior 的
            // checkExtraStartConditions）。它一关，升级寻路、闪避、意图三件
            // 一起停——而外面只看得到"她站着不动"。空的候选表既可能是"评估过
            // 但一件都不合格"，也可能是"根本没评估"，这一句把两者分开。
            out.append("自由模式=")
                    .append(FreedomMode.isActive(maid) ? "开" : "关")
                    .append(' ');
            // 报出**问的是哪一个实例**。inspect 返回 idle() 只有一个含义
            // ——"states 里没有她"——而那既可能是编排器真没评估过她，也可能
            // 是我问错了对象（这一层有 shadow / live 两个编排器，还隔着一层
            // 门面）。两者的结论截然相反：前者是她的病，后者是供词的病。实
            // 测撞上过一次自相矛盾的读数：自由模式=开、执行器供词证明附加脑
            // 跑过（导航升级只在那儿做），可 inspect 仍是 idle——那种时候先
            // 怀疑量具，别怀疑她。
            out.append("问的是=")
                    .append(api.getClass().getSimpleName())
                    .append(' ');
            // 全局计数一并报出。inspect 给 idle() 有两种读法——编排器从没
            // 评估过**她**，或者整个编排器一次都没转起来——而这两种的下一
            // 步查法完全相反。计数非零就说明它在工作、只是漏了她。
            // 她自己的 tick 数——**不依赖任何假设**的那一个判据。
            //
            // 附加脑出勤截断有两种可能：她被 tick 了但附加脑没跑（往准入
            // 查），或者她**根本没被 tick**（那是夹具/加载的事，与她无关）。
            // 我为此先后猜过准入条件、诊断开关、目录为空、两个实例、区块加
            // 载——全错。tickCount 一个数就分得开：它跟着出勤一起停，就是没
            // 人 tick 她；它照涨而出勤不涨，才轮到准入。
            out.append("她的tick=").append(maid.tickCount).append(' ');
            out.append(BehaviorExtraBrain.ambientDiary(maid)).append(' ');
            out.append("计数=").append(api.metrics()).append(' ');
            out.append("intent=");
            out.append(trace.activeIntent() == null
                            ? "(none)" : trace.activeIntent())
                    .append('/').append(trace.activeState())
                    .append(" last=").append(trace.lastTransition())
                    .append(" cand=");
            for (IntentTrace.Candidate candidate : trace.candidates()) {
                out.append(' ').append(candidate.intent()).append('=')
                        .append(String.format("%.2f", candidate.score()))
                        .append('(').append(candidate.status()).append(')');
            }
            return out.toString();
        } catch (RuntimeException unavailable) {
            return "intent=n/a(" + unavailable.getClass().getSimpleName() + ")";
        }
    }

    /**
     * 问**掌舵的那一个**，不是注册表里碰巧登记的那一个。
     *
     * <p>实测撞见过两者不是同一个实例：同一条测试、同一份代码，一轮的供词
     * 是完整轨迹，另一轮却是原样的 idle()，而附加脑的出勤计数显示它跑了五
     * 百多次。据此我曾下过"意图层对她一无所知"的结论，把真凶所在的整个层
     * 都排除掉了——那不是她的病，是量具问错了对象。
     */
    @SuppressWarnings("unchecked")
    private static MaidIntentApi<EntityMaid> intents() {
        MaidIntentApi<EntityMaid> steering =
                BehaviorExtraBrain.steeringIntents();
        return steering != null
                ? steering
                : (MaidIntentApi<EntityMaid>) AdapterRuntime.require(
                        MaidIntentApi.class);
    }
}
