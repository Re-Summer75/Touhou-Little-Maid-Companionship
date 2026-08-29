package com.laixia.maidintelligence.gametest.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentActions;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;
import com.laixia.maidintelligence.gametest.support.GameTestPositions;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 清扫的间隔，量成一个数。
 *
 * <p>"她捡一件停一下"是实机看出来的，而看出来的东西改不动：两轮参数调整（冷却
 * 归零、评估间隔对齐编排器节拍）都没让那一停消失，因为它根本不在参数里。这一组
 * 存在的意义就是把那一停变成一个可以断言的数。
 *
 * <p>两把尺，对应间隔的两种来源：
 *
 * <ul>
 *   <li><b>逻辑间隔</b>——收 N 件要花几个 tick。计划执行器每 tick 调一次动作，
 *       所以"几次调用"就是"几个 tick"。理想是 N，多出来的每一次都是她站着不
 *       动的一 tick。</li>
 *   <li><b>路径间隔</b>——收下一件的那一 tick，她手上还有没有移动目标。松开了，
 *       导航当拍就空转、路径被清掉，下一 tick 重新寻路再起步，实机看就是刹一下。
 *       理想是从不松开。</li>
 * </ul>
 *
 * <p>两把尺都不让她真的走路，也都不经过编排器。走路会把寻路耗时混进读数，而
 * 编排器会让同一个动作有两个驱动源——量到的数就说不清是谁的了。真的走、真的选，
 * 各自有各自的测试（{@code LooseDropGameTests}）。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class LooseDropContinuityGameTests {
    private static final Map<String, String> PARAMETERS =
            Map.of("speed", "0.7", "close_distance", "1");

    /** 脚边那一组摆几件。够看出"每 tick 一件"，又不超过一次查询取回的候选数。 */
    private static final int UNDER_FOOT = 4;

    /** 实景那一组摆几件。 */
    private static final int PILE_SIZE = 20;

    /** 实景那一组观察多少 tick。 */
    private static final int WATCHED_TICKS = 600;

    /**
     * 位移多小算"没动"。
     *
     * <p>平方距离。她走路时每 tick 挪零点一格上下，站着时只有极小的抖动，
     * 所以这个门槛既排得掉抖动，也不会把慢走误判成静止。
     */
    private static final double STIRRED = 0.0004D;

    /** 活动意图的名字，用来把空转按"是谁占着她"分类。 */
    private static final String SWEEP = "loose_drop_pickup";

    private LooseDropContinuityGameTests() {
    }

    /**
     * 脚边四件，四个 tick 收完，一个 tick 都不多。
     *
     * <p>多出来的那一次调用会长成这样：收下一件之后差事收工（SUCCEEDED），编排器
     * 要重新选中它才会有下一次 {@code find}，而那一次调用什么都没收。这条断言就是
     * 冲着它去的——顺带也钉住了"整趟从不松手"，因为每一次的返回都必须是 RUNNING。
     */
    @GameTest(batch = "loosedropcontinuity", templateNamespace = "minecraft", template = "empty")
    public static void fourAtHerFeetTakeFourTicks(GameTestHelper helper) {
        EntityMaid maid = maid(helper, 2, 2, 1);
        List<ItemEntity> pile = new ArrayList<>();
        for (int index = 0; index < UNDER_FOOT; index++) {
            pile.add(drop(helper, Items.COBBLESTONE, 2, 2, 1));
        }
        TlmMaidIntentActions actions = actions();
        long gameTime = helper.getLevel().getGameTime();

        int ticks = 0;
        while (remaining(pile) > 0 && ticks < UNDER_FOOT * 4) {
            ticks++;
            ActionResult result = run(actions, maid, gameTime);
            helper.assertTrue(
                    result == ActionResult.RUNNING,
                    "The sweep let go after " + ticks + " ticks: " + result
            );
        }

        helper.assertTrue(
                remaining(pile) == 0,
                "She never finished the pile: " + remaining(pile) + " left"
        );
        helper.assertTrue(
                ticks == UNDER_FOOT,
                "Four drops took " + ticks + " ticks, not " + UNDER_FOOT
                        + " — that is " + (ticks - UNDER_FOOT)
                        + " ticks of standing about"
        );
        helper.succeed();
    }

    /**
     * 收下一件的那一拍，她没有松开移动目标。
     *
     * <p>三拍，全部确定性——不寻路，而是把她挪到东西跟前，因为要量的是那一拍
     * **动作做了什么**，不是她走多久才到：
     *
     * <ol>
     *   <li>第一拍：最近的一件在四格外，她拿到一个指向它的移动目标。</li>
     *   <li>把她挪到那件东西上，第二拍：收下它。<b>移动目标必须还在。</b>
     *       此前这里是抹掉的——抹在这一 tick、写回要等下一 tick，中间隔着一次
     *       导航更新，那就是实机看到的一停。</li>
     *   <li>第三拍：目标换成剩下的那一件，她接着走。</li>
     * </ol>
     */
    @GameTest(batch = "loosedropcontinuity", templateNamespace = "minecraft", template = "empty")
    public static void collectingOneNeverDropsHerPath(GameTestHelper helper) {
        EntityMaid maid = maid(helper, 1, 2, 1);
        ItemEntity near = drop(helper, Items.COBBLESTONE, 5, 2, 1);
        ItemEntity far = drop(helper, Items.IRON_INGOT, 9, 2, 1);
        TlmMaidIntentActions actions = actions();
        long gameTime = helper.getLevel().getGameTime();

        helper.assertTrue(
                run(actions, maid, gameTime) == ActionResult.RUNNING,
                "She would not set out for the nearer drop"
        );
        WalkTarget first = walkTarget(maid);
        helper.assertTrue(first != null, "No walk target on the first tick");

        maid.setPos(near.position());
        helper.assertTrue(
                run(actions, maid, gameTime) == ActionResult.RUNNING,
                "Collecting one ended the sweep"
        );
        helper.assertFalse(near.isAlive(), "The nearer drop was not taken");
        helper.assertTrue(
                walkTarget(maid) != null,
                "She let go of her walk target on the tick she collected one"
                        + " — that tick is the stutter"
        );

        helper.assertTrue(
                run(actions, maid, gameTime) == ActionResult.RUNNING,
                "She would not move on to the next drop"
        );
        WalkTarget next = walkTarget(maid);
        helper.assertTrue(
                next != null && next != first,
                "She is still heading at the drop she already took"
        );
        helper.assertTrue(far.isAlive(), "The far drop vanished early");
        helper.succeed();
    }

    /**
     * 二十件散落的同种物品，从头到尾不许停。
     *
     * <p>前两条尺量的是这个差事自己，而实机报的"还是一样"说明有别人在影响她。
     * 所以这一条反过来：**放真实场景，然后记下每一 tick 是谁占着她。**
     *
     * <p>场景照实机的样子摆——二十件、同一种、散落，而且**两两间隔足够大，不会
     * 合并**。合并这件事不是细节：同种掉落物离得近会并成一堆，脚边四件其实是一件，
     * 量出来的"每 tick 一件"就不是真的。
     *
     * <p>逐 tick 比位置，只要地上还有东西而她原地未动，就把那一 tick 记下来，
     * 连同当时的活动意图一起。**这条测试存在的意义是把"还是一样"变成一个名字**，
     * 而它确实变成了：最初读数是清扫意图自己占五百三十一 tick、二十件只收到一件。
     *
     * <p>断两条：东西必须全部收完；静止不得超过**每件一 tick**。后者不是拍脑袋的
     * 数，是导航的地板——收下一件之后写新的移动目标会清掉旧路径，原版的移动行为要
     * 到下一 tick 才算出新路径，那一 tick 她站着。
     *
     * <p>这条测试一路记下来的读数，就是三处修改各自值多少：
     *
     * <table><caption>二十件散落，六百 tick</caption>
     *   <tr><th>改动</th><th>静止 tick</th><th>其中清扫自己</th><th>收集</th></tr>
     *   <tr><td>最初</td><td>591</td><td>531</td><td>1/20</td></tr>
     *   <tr><td>+ 到达判据与导航对齐</td><td>267</td><td>10</td><td>20/20</td></tr>
     *   <tr><td>+ 掉落物改直接扫</td><td>6</td><td>2</td><td>20/20</td></tr>
     * </table>
     *
     * <p>中间那一档里剩下的两百多 tick 全部伴随 {@code blocked:
     * loose_drop_available}——她**确实看不见**地上的东西。把这一问从广告板挪到直接
     * 扫实体之后，那一项彻底消失。
     */
    @GameTest(batch = "loosedropcontinuity", templateNamespace = "minecraft", template = "empty",
            timeoutTicks = WATCHED_TICKS + 100)
    public static void twentyScatteredDropsNeverLeaveHerStanding(
            GameTestHelper helper
    ) {
        EntityMaid maid = maid(helper, 6, 2, 6);
        List<ItemEntity> pile = scatter(helper);
        String[] who = new String[WATCHED_TICKS + 1];
        boolean[] moved = new boolean[WATCHED_TICKS + 1];
        int[] left = new int[WATCHED_TICKS + 1];
        Vec3[] previous = new Vec3[]{maid.position()};

        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                Vec3 now = maid.position();
                moved[at] = now.distanceToSqr(previous[0]) > STIRRED;
                previous[0] = now;
                left[at] = remaining(pile);
                var trace = productionIntents().inspect(maid);
                var active = trace.activeIntent();
                who[at] = active != null
                        ? active.path()
                        : "(none)" + sweepStatus(trace);
            });
        }

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            Map<String, Integer> standing = new TreeMap<>();
            int idle = 0;
            for (int tick = 2; tick <= WATCHED_TICKS; tick++) {
                if (left[tick] == 0) {
                    break;
                }
                if (!moved[tick]) {
                    idle++;
                    standing.merge(who[tick], 1, Integer::sum);
                }
            }
            int collected = PILE_SIZE - left[WATCHED_TICKS];
            String reading = " — by intent: " + standing
                    + "; collected " + collected + " of " + PILE_SIZE
                    + "; carrying=" + carried(maid);
            helper.assertTrue(
                    collected == PILE_SIZE,
                    "She left drops on the floor" + reading
            );
            // 只断清扫自己造成的静止。她放弃一件够不着的东西之后确实会没事可做，
            // 那时站着是对的——把那些 tick 也算进来，等于要求她对拿不到的东西也
            // 保持忙碌。
            int sweeping = standing.getOrDefault(SWEEP, 0);
            helper.assertTrue(
                    sweeping <= PILE_SIZE,
                    "The sweep itself left her standing for " + sweeping
                            + " ticks (idle in all " + idle + ")" + reading
            );
            helper.succeed();
        });
    }

    /**
     * 摆二十件，两两至少两格。
     *
     * <p>间隔是硬要求：同种掉落物在 {@code ItemEntity.tick} 里会把周围半格内的
     * 同类并进自己，摆得太近就不是二十件而是几堆——那样量到的连贯性是假的。
     */
    private static List<ItemEntity> scatter(GameTestHelper helper) {
        List<ItemEntity> pile = new ArrayList<>();
        for (int index = 0; index < PILE_SIZE; index++) {
            // 固定的伪随机：散得开，且每次运行摆在同样的地方，读数才可比。
            double jitter = ((index * 7) % 5) * 0.12D;
            double x = 1.0D + (index % 5) * 2.5D + jitter;
            double z = 1.0D + (index / 5) * 2.5D + jitter;
            pile.add(scatterDrop(helper, x, z));
        }
        return pile;
    }

    private static ItemEntity scatterDrop(
            GameTestHelper helper,
            double x,
            double z
    ) {
        Vec3 origin = GameTestPositions.center(helper, 0, 2, 0);
        ItemEntity entity = new ItemEntity(
                helper.getLevel(),
                origin.x + x,
                origin.y,
                origin.z + z,
                new ItemStack(Items.COBBLESTONE)
        );
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static ActionResult run(
            TlmMaidIntentActions actions,
            EntityMaid maid,
            long gameTime
    ) {
        return actions.execute(
                maid,
                CompanionIntentIds.PICK_UP_LOOSE_DROP,
                PARAMETERS,
                gameTime,
                0
        );
    }

    private static WalkTarget walkTarget(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
    }

    /** 清扫这个候选此刻是什么状态——被哪条事实挡住，答案就在里面。 */
    private static String sweepStatus(IntentTrace trace) {
        for (IntentTrace.Candidate candidate : trace.candidates()) {
            if (candidate.intent().path().contains("loose_drop_pickup")) {
                return ":" + candidate.status();
            }
        }
        return ":absent";
    }

    /** 她此刻还能看见几件可捡的——事实为真为假就看这个数。 */
    private static int visible(EntityMaid maid) {
        return new TlmAffordancePerceptionService().queryLooseDrops(
                maid, 8, maid.level().getGameTime()
        ).size();
    }

    /** 她身上装了什么，用来判断是不是塞满了。 */
    private static String carried(EntityMaid maid) {
        StringBuilder held = new StringBuilder();
        var inventory = maid.getAvailableInv(false);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            held.append(stack.isEmpty() ? "_" : stack.getCount() + "x")
                    .append(' ');
        }
        return held.toString().trim();
    }

    private static int remaining(List<ItemEntity> pile) {
        int alive = 0;
        for (ItemEntity item : pile) {
            if (item.isAlive()) {
                alive++;
            }
        }
        return alive;
    }

    @SuppressWarnings("unchecked")
    private static MaidIntentApi<EntityMaid> productionIntents() {
        return (MaidIntentApi<EntityMaid>) AdapterRuntime.require(
                MaidIntentApi.class
        );
    }

    private static TlmMaidIntentActions actions() {
        return new TlmMaidIntentActions(
                ignored -> {
                },
                new MaidSnackCabinetMealSource(
                        new MaidMealAccess(),
                        new TlmAffordancePerceptionService()
                )
        );
    }

    /**
     * 一间围起来的房间，装得下二十件散落的东西。
     *
     * <p>围墙不是装饰：清扫的感知半径十六格，而 GameTest 的结构在同一个世界里
     * 紧挨着排。不围的话，别的测试的女仆会走进来把这里的东西收走，量到的就成了
     * "邻居今天来没来过"。
     */
    private static EntityMaid maid(GameTestHelper helper, int x, int y, int z) {
        for (int floorX = -1; floorX <= 13; floorX++) {
            for (int floorZ = -1; floorZ <= 13; floorZ++) {
                helper.setBlock(new BlockPos(floorX, 1, floorZ), Blocks.STONE);
                boolean edge = floorX == -1 || floorX == 13
                        || floorZ == -1 || floorZ == 13;
                if (edge) {
                    helper.setBlock(
                            new BlockPos(floorX, 2, floorZ), Blocks.STONE);
                    helper.setBlock(
                            new BlockPos(floorX, 3, floorZ), Blocks.STONE);
                }
            }
        }
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, x, y, z));
        maid.setTame(true);
        maid.setPickup(true);
        maid.setTask(
                TaskManager.findTask(FreedomMaidTask.UID).orElseThrow()
        );
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        return maid;
    }

    private static ItemEntity drop(
            GameTestHelper helper,
            Item item,
            int x,
            int y,
            int z
    ) {
        Vec3 position = GameTestPositions.center(helper, x, y, z);
        ItemEntity entity = new ItemEntity(
                helper.getLevel(),
                position.x,
                position.y,
                position.z,
                new ItemStack(item)
        );
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }
}
