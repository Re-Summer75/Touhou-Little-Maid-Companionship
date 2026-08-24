package com.laixia.maidintelligence.feature.orchestration.tlm.insight;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.api.insight.MaidInsight;
import com.laixia.maidintelligence.feature.orchestration.api.insight.MaidInsightNarrator;
import com.laixia.maidintelligence.feature.orchestration.api.insight.NoteRule;
import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.insight.MaidInsightSource;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentContext;
import com.laixia.maidintelligence.feature.orchestration.tlm.ambient.TlmPathReveal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Objects;

/**
 * Turns a TLM maid into the panel's contents.
 *
 * <p>The note rules live here because this is the layer that knows what the
 * facts mean — orchestration only knows {@code fact/hunger} is a number.
 * Severity tiers are listed strongest first; the narrator takes only the first
 * that fires for a given fact.
 */
public final class TlmMaidInsightSource implements MaidInsightSource {
    private static final List<NoteRule> NOTE_RULES = List.of(
            new NoteRule(
                    CompanionIntentIds.HUNGER,
                    FactComparison.LESS_OR_EQUAL,
                    6.0D,
                    "very_hungry"
            ),
            new NoteRule(
                    CompanionIntentIds.HUNGER,
                    FactComparison.LESS_OR_EQUAL,
                    20.0D,
                    "hungry"
            ),
            new NoteRule(
                    CompanionIntentIds.COMBAT_ACTIVE,
                    FactComparison.EQUAL,
                    1.0D,
                    "combat"
            ),
            new NoteRule(
                    CompanionIntentIds.CAN_MOVE,
                    FactComparison.EQUAL,
                    0.0D,
                    "stuck"
            ),
            new NoteRule(
                    CompanionIntentIds.BEHAVIOR_OCCUPANCY_LEVEL,
                    FactComparison.GREATER_OR_EQUAL,
                    2.0D,
                    "busy"
            )
    );

    private final MaidIntentApi<EntityMaid> intents;
    private final TlmMaidIntentContext context;

    public TlmMaidInsightSource(
            MaidIntentApi<EntityMaid> intents,
            TlmMaidIntentContext context
    ) {
        this.intents = Objects.requireNonNull(intents, "intents");
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public List<Entity> maidsNear(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        // Typed query against already-loaded entities; no chunk is loaded to
        // answer a question about a held item.
        return List.copyOf(player.level().getEntitiesOfClass(
                EntityMaid.class,
                box,
                maid -> maid.isAlive() && maid.isOwnedBy(player)
        ));
    }

    @Override
    public MaidInsight insightFor(Entity maid) {
        if (!(maid instanceof EntityMaid entityMaid)) {
            return MaidInsight.idle();
        }
        MaidInsight insight = MaidInsightNarrator.summarize(
                intents.inspectDecision(entityMaid),
                NOTE_RULES,
                context.bestHunch(entityMaid)
        );
        return withFootwork(insight, entityMaid);
    }

    /**
     * 在"这一步"后面缀上**脚下正在走的那一段**。
     *
     * <p>意图层说的是"她想干什么"（跟上你、在你附近转转），可寻路的毛病全
     * 发生在下一层：同一个"跟上你"底下，她可能正在走路、正在过柱、正在穿
     * 角、正在被崖边看护刹停，也可能压根没有路。隔着屏幕这几种长得一模一
     * 样——都是"她站在那儿不动"。
     *
     * <p>玩家实测里这一条卡了好几轮：面板显示"跟上你 / go"、粒子链也画到
     * 了缺口，可她就是出不来——缺的正是"她此刻在执行哪一段"。有了它，一
     * 张截图就能定位到分支，不用再靠猜。
     */
    private static MaidInsight withFootwork(
            MaidInsight insight,
            EntityMaid maid
    ) {
        String note = TlmPathReveal.noteOf(maid);
        if (note.isEmpty() || "-".equals(note)) {
            return insight;
        }
        String step = insight.step().isEmpty()
                ? note
                : insight.step() + " · " + note;
        return new MaidInsight(insight.doing(), step, insight.doingForTicks(),
                insight.alternatives(), insight.notes(), insight.hunch());
    }
}
