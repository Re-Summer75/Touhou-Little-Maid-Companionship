package com.laixia.maidintelligence.feature.orchestration.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.domain.OwnerFollowPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.OwnerLingerPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.owner.OwnerFactIds;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.application.DefaultMaidIntentOrchestrator;
import com.laixia.maidintelligence.feature.orchestration.application.MutableIntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主人在走的时候她跟得紧，主人停下的时候她别像家具——两条意图，同一个抱怨。
 *
 * <p>玩家报的是"跟随的响应不是很好"。被替掉的那条 {@code anticipate_departure} 距离效用
 * 权重是 0.9，`product` 下 `factor = 1 - weight × (1 - curved)`、因子恒 ≤ 1，所以**它的分数
 * 结构上就要求距离**：主人不走出八格，她的分数够不到 `minimum_score`，而这在那条意图内部
 * 无法通过配平解决。判据换成"主人在不在赶路"之后，那条与本条同 band、同条件且分数恒低，
 * 于是被删除。
 *
 * <p>因此这里全部写在真实 JSON 上，而不是内联一份：最可能写错的就是那两个文件里的
 * 配平，而一份内联副本永远和它们一致。
 */
public final class OwnerCompanyVerification {
    private static final String RESOURCE_ROOT = "data/tlm_companionship/";

    /** 比两条意图的 evaluation_interval_ticks 都宽裕。 */
    private static final int EVALUATION_WINDOW_TICKS = 60;

    private static final OrchestrationId ESCORT = id("intent/escort_owner");
    private static final OrchestrationId LINGER =
            id("intent/linger_near_owner");

    /** 旧的那一条要走出八格才起步，这里只需要一个"远小于它"的数。 */
    private static final double CONVERSATIONAL_BLOCKS = 3.0D;

    private OwnerCompanyVerification() {
    }

    public static void main(String[] args) throws IOException {
        verifiesAMovingOwnerIsFollowedAtOnce();
        verifiesAParkedOwnerIsNotChased();
        verifiesSheDoesNotJitterAtHisElbow();
        verifiesFollowingSurvivesClosingTheGap();
        verifiesSheStrollsWhenHeSettles();
        verifiesSheDoesNotStrollWhileHeIsLeaving();
        verifiesTheStrollCircleIsOneNumber();
        verifiesTheChaseLineIsAKnownConsequence();
        System.out.println("Owner company verification passed.");
    }

    /**
     * 这一条就是玩家要的那件事：他已经在走了，三格也该跟上。
     *
     * <p>三格远小于旧那条的八格起步线，所以这条断言只可能被"持续移动"那一路满足。
     */
    private static void verifiesAMovingOwnerIsFollowedAtOnce()
            throws IOException {
        Harness harness = escort();
        harness.onTheMove(true);
        harness.distance(CONVERSATIONAL_BLOCKS);
        require(
                harness.fires(),
                "主人已经连着走了三秒、只隔三格，她还站着不动——"
                        + "这正是玩家报的那个响应"
        );
    }

    /** 反过来：他没在赶路，三格就不是理由。否则她会永远贴着他。 */
    private static void verifiesAParkedOwnerIsNotChased() throws IOException {
        Harness harness = escort();
        harness.onTheMove(false);
        harness.distance(CONVERSATIONAL_BLOCKS);
        require(
                !harness.fires(),
                "主人站着没动，她还是放下手上的事跑过去贴着"
        );
    }

    /**
     * 已经在他手边就别再起念头。
     *
     * <p>贴到一格还去"跟随"的结果是她每隔几十 tick 抢一次意图，而每一次抢都会打断
     * 她正在做的事——响应过头和响应不足一样难看。
     */
    private static void verifiesSheDoesNotJitterAtHisElbow()
            throws IOException {
        Harness harness = escort();
        harness.onTheMove(true);
        harness.distance(1.0D);
        require(!harness.fires(), "她已经贴在主人身上，还要再跟一次");
    }

    /**
     * 距离条件是 entry_only，所以走近之后不该把自己取消掉。
     *
     * <p>每 tick 计分的话，她一走进两格分数就塌了，于是停在出发的距离上不动——
     * 这条与旧那一条踩过的是同一个坑。
     */
    private static void verifiesFollowingSurvivesClosingTheGap()
            throws IOException {
        Harness harness = escort();
        harness.onTheMove(true);
        harness.distance(10.0D);
        require(harness.fires(), "夹具没让她起步，这条什么都测不到");
        harness.distance(1.0D);
        require(harness.stillActive(), "她在半路上把自己取消了");
    }

    /** 他停下来了，而她就在旁边——这时候该在他附近晃，不是钉着。 */
    private static void verifiesSheStrollsWhenHeSettles() throws IOException {
        Harness harness = linger();
        harness.onTheMove(false);
        harness.distance(3.0D);
        require(
                harness.fires(),
                "主人停下来之后她一动不动——自由模式没有游走，"
                        + "而这一条正是为此加的"
        );
    }

    /**
     * 他在赶路时不许晃。
     *
     * <p>两条意图分属不同 band（跟紧 30、游走 10），但真正保证它们不打架的是
     * 这个条件互斥：同一个事实，一条要它为真，一条要它为假。
     */
    private static void verifiesSheDoesNotStrollWhileHeIsLeaving()
            throws IOException {
        Harness harness = linger();
        harness.onTheMove(true);
        harness.distance(3.0D);
        require(!harness.fires(), "主人正在走开，她却在原地散步");
    }

    /**
     * 那一圈只能有一个数。
     *
     * <p>半径写在 {@code OwnerLingerPolicy} 里，而"她离主人多远才起念头"写在意图
     * JSON 里——两处说的是同一个圆。抄一遍就是两个会各自漂移的数，而漂移之后
     * 没有任何东西会响：她照样散步，只是散得比策略以为的远。
     */
    private static void verifiesTheStrollCircleIsOneNumber() throws IOException {
        double authored = conditionValue(
                "linger_near_owner", "fact/owner_distance"
        );
        require(
                authored == OwnerLingerPolicy.RADIUS,
                "意图写的是 " + authored + " 格，策略写的是 "
                        + OwnerLingerPolicy.RADIUS + " 格——同一个圆有了两个半径"
        );
    }

    /**
     * 散步会不会被结伴打断——记下来，而不是禁止它。
     *
     * <p>半径取满感知（16），而结伴从 5 格开始考虑出发，两者相交是**明知的取舍**：
     * 她晃出五格之后可能被 band 30 的结伴带回主人身边，于是散步的实际边界由那条
     * 拉力决定，而不是由半径决定。
     *
     * <p>那这条断言存在的意义是什么？它把"明知"变成"钉住的"。今天这个相交是选出来
     * 的；改天有人把结伴的起步线挪到 20 格、或者把游走半径调到 30，相交的性质就
     * 变了，而没有任何东西会提醒他。这里不禁止相交，只要求它仍然是**当初那个形状**：
     * 游走够得着结伴、而结伴够不着牵引绳。
     */
    private static void verifiesTheChaseLineIsAKnownConsequence()
            throws IOException {
        double chaseFrom = conditionValue(
                "keep_company", "fact/owner_distance"
        );
        require(
                OwnerLingerPolicy.RADIUS > chaseFrom,
                "游走半径 " + OwnerLingerPolicy.RADIUS + " 已经够不到结伴的 "
                        + chaseFrom + " 格起步线：这两条的关系变了，"
                        + "而当初取满感知就是为了让她晃得出去"
        );
        require(
                chaseFrom < OwnerFollowPolicy.TELEPORT_DISTANCE,
                "结伴的起步线跑到了牵引绳之外，那条意图永远不会被用到"
        );
    }

    /** 某条意图里某个事实的条件取值。 */
    private static double conditionValue(String intentFile, String fact)
            throws IOException {
        String wanted = "tlm_companionship:" + fact;
        for (JsonElement entry : resource(
                MaidIntentReloadListener.INTENT_PREFIX + "/" + intentFile
                        + ".json"
        ).getAsJsonObject().getAsJsonArray("conditions")) {
            if (wanted.equals(
                    entry.getAsJsonObject().get("fact").getAsString()
            )) {
                return entry.getAsJsonObject().get("value").getAsDouble();
            }
        }
        throw new AssertionError(
                intentFile + ".json has no condition on " + fact
        );
    }

    private static Harness escort() throws IOException {
        return new Harness(ESCORT, "escort_owner", "escort_owner");
    }

    private static Harness linger() throws IOException {
        return new Harness(LINGER, "linger_near_owner", "linger_near_owner");
    }

    private static final class Harness {
        private final Map<OrchestrationId, Double> facts = new HashMap<>();
        private final MaidIntentApi<String> orchestrator;
        private final OrchestrationId intent;
        private long elapsed;

        private Harness(
                OrchestrationId intent,
                String intentFile,
                String planFile
        ) throws IOException {
            this.intent = intent;
            facts.put(CompanionIntentIds.OWNER_VALID, 1.0D);
            facts.put(CompanionIntentIds.OWNER_DISTANCE, 3.0D);
            facts.put(CompanionIntentIds.FOLLOW_MODE, 1.0D);
            facts.put(CompanionIntentIds.CAN_MOVE, 1.0D);
            facts.put(CompanionIntentIds.PASSENGER, 0.0D);
            facts.put(CompanionIntentIds.BEHAVIOR_OCCUPANCY_LEVEL, 0.0D);
            facts.put(CompanionIntentIds.HUNGER, 100.0D);
            facts.put(CompanionIntentIds.FAVORABILITY, 2.0D);

            MutableIntentCatalog catalog = new MutableIntentCatalog();
            orchestrator = new DefaultMaidIntentOrchestrator<>(
                    catalog,
                    (subject, gameTime, requested, output) -> {
                        for (int index = 0; index < requested.size(); index++) {
                            output[index] = facts.getOrDefault(
                                    requested.get(index), 0.0D
                            );
                        }
                    },
                    (subject, action, parameters, gameTime, elapsedTicks) ->
                            ActionResult.RUNNING,
                    String::hashCode,
                    () -> true,
                    () -> 1,
                    () -> 64,
                    () -> true
            );
            catalog.publish(IntentCatalog.compile(
                    1L,
                    List.of(intentFrom(intent, intentFile)),
                    List.of(planFrom(planFile)),
                    CompanionIntentIds.vocabulary()
            ));
        }

        private void distance(double blocks) {
            facts.put(CompanionIntentIds.OWNER_DISTANCE, blocks);
        }

        private void onTheMove(boolean moving) {
            facts.put(OwnerFactIds.ON_THE_MOVE, moving ? 1.0D : 0.0D);
        }

        /** Whether it takes the floor inside one evaluation window. */
        private boolean fires() {
            for (int tick = 0; tick < EVALUATION_WINDOW_TICKS; tick++) {
                orchestrator.tick("maid", elapsed);
                elapsed++;
                if (intent.equals(
                        orchestrator.inspect("maid").activeIntent()
                )) {
                    return true;
                }
            }
            return false;
        }

        /** And whether it is still the one running a window later. */
        private boolean stillActive() {
            for (int tick = 0; tick < EVALUATION_WINDOW_TICKS; tick++) {
                if (!intent.equals(
                        orchestrator.inspect("maid").activeIntent()
                )) {
                    return false;
                }
                orchestrator.tick("maid", elapsed);
                elapsed++;
            }
            return true;
        }
    }

    private static IntentDefinition intentFrom(
            OrchestrationId id,
            String file
    ) throws IOException {
        return IntentDefinitionCodec.parse(
                id,
                resource(MaidIntentReloadListener.INTENT_PREFIX
                        + "/" + file + ".json")
        ).result().orElseThrow(() ->
                new AssertionError(file + ".json does not parse"));
    }

    private static PlanDefinition planFrom(String file) throws IOException {
        return PlanDefinitionCodec.parse(
                id(file),
                resource(MaidIntentReloadListener.PLAN_PREFIX
                        + "/" + file + ".json")
        ).result().orElseThrow(() ->
                new AssertionError(file + ".json does not parse"));
    }

    private static JsonElement resource(String path) throws IOException {
        InputStream stream = OwnerCompanyVerification.class
                .getClassLoader()
                .getResourceAsStream(RESOURCE_ROOT + path);
        if (stream == null) {
            throw new IOException("Missing resource " + RESOURCE_ROOT + path);
        }
        try (InputStreamReader reader = new InputStreamReader(
                stream,
                StandardCharsets.UTF_8
        )) {
            return JsonParser.parseReader(reader);
        }
    }

    private static OrchestrationId id(String path) {
        return new OrchestrationId("tlm_companionship", path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
