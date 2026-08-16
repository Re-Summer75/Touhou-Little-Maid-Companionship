package com.laixia.maidintelligence.gametest.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentActions;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;
import com.laixia.maidintelligence.gametest.support.GameTestPositions;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.Map;

/**
 * 打完一场架之后，把地上的东西收起来。
 *
 * <p>本体一直会捡东西——{@code pushEntities} 每 tick 扫她身边半格。缺的是走过去：
 * 战利品散在三五格外，而在此之前没有任何东西会让她挪一步。
 *
 * <p>这里量的是三件事：不是吃的也不是武器的东西现在会被收走（从前那类掉落物连
 * 广告都不会被登记）、拾物关掉时她一步都不迈、以及背包塞不下时她不会白跑一趟。
 *
 * <p>和拾食那组一样不等她走路：要么东西已经在脚边，一次调用见分晓；要么故意放远，
 * 那就只看她**被指去了哪里**，不看她几 tick 走到——后者会在慢机器上变红，然后教
 * 会所有人忽略红灯。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class LooseDropGameTests {
    private static final Map<String, String> PARAMETERS =
            Map.of("speed", "0.7", "close_distance", "1");

    /**
     * 等到她有机会做出决定为止。
     *
     * <p>观察 20 tick 一次、评估 10 tick 一次，两段是串的。留一倍余量，因为
     * 随机变红的测试会教会所有人忽略红灯。
     */
    private static final int SETTLED_TICKS = 60;

    /** 经验球落地后要活过原版那个合并窗口才收得走。留一点余量。 */
    private static final int ORB_SETTLED_TICKS = 5;


    private LooseDropGameTests() {
    }

    /**
     * 一块圆石。既不解饿也不能打，所以从前它连广告都不会被登记——
     * 那正是"她只捡吃的"的全部原因。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void somethingUselessAtHerFeetIsStillCollected(
            GameTestHelper helper
    ) {
        EntityMaid maid = maid(helper, 2, 2, 1, true);
        drop(helper, Items.COBBLESTONE, 3, 2, 1);

        // RUNNING 而不是 SUCCEEDED：收下一件不等于这一趟结束，她接着找下一个。
        // 见 `Errand.sweeps`。
        helper.assertTrue(
                execute(helper, maid) == ActionResult.RUNNING,
                "A drop at her feet was left on the ground"
        );
        helper.assertTrue(
                packHolds(maid, Items.COBBLESTONE),
                "It was taken off the ground but is not in her pack"
        );
        helper.succeed();
    }

    /** 拾物关着时，这个差事连一个目标都找不到。 */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void withPickupOffSheLeavesItWhereItLies(
            GameTestHelper helper
    ) {
        EntityMaid maid = maid(helper, 2, 2, 1, false);
        ItemEntity dropped = drop(helper, Items.COBBLESTONE, 3, 2, 1);

        helper.assertTrue(
                execute(helper, maid) == ActionResult.FAILED,
                "She went for a drop with pickup switched off"
        );
        helper.assertTrue(
                dropped.isAlive(),
                "The drop was taken with pickup switched off"
        );
        helper.succeed();
    }

    /**
     * 远处那件东西会让她动起来。
     *
     * <p>看的是移动目标写没写，不是她走到没走到。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aDropAcrossTheRoomIsWorthWalkingTo(
            GameTestHelper helper
    ) {
        EntityMaid maid = maid(helper, 1, 2, 1, true);
        ItemEntity dropped = drop(helper, Items.COBBLESTONE, 8, 2, 1);

        helper.assertTrue(
                execute(helper, maid) == ActionResult.RUNNING,
                "A drop across the room did not start an errand"
        );
        helper.assertTrue(
                walkingToward(maid, dropped),
                "She was not sent anywhere near the drop"
        );
        helper.succeed();
    }

    /**
     * 塞不下就不去。
     *
     * <p>这一条不是本模组判的——`canPickup` 会**模拟一次插入**，塞不进就不点头，
     * 于是广告根本不存在。价值在于她不会走完全程才发现白跑。
     *
     * <p>"塞不下"要按本体的口径来：它模拟插入的是 {@code getAvailableInv(false)}，
     * 那是**背包加两只手**。只填背包的夹具留着两只空手，圆石会进手里——这条测试
     * 第一次就是这么红的，红得对。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void nowhereToPutItIsNotWorthTheWalk(GameTestHelper helper) {
        EntityMaid maid = maid(helper, 2, 2, 1, true);
        fillEverySlot(maid);
        ItemEntity dropped = drop(helper, Items.COBBLESTONE, 3, 2, 1);

        helper.assertTrue(
                execute(helper, maid) == ActionResult.FAILED,
                "She set out for a drop she has no room for"
        );
        helper.assertTrue(
                dropped.isAlive(),
                "The drop vanished into a full pack"
        );
        helper.succeed();
    }

    /**
     * 头顶够不着的那件，她连去都不去。
     *
     * <p>没有这一条时她会走到它正下方，然后"到了没到"地反复判断——人看着就是在
     * 原地点头，而那件东西她永远拿不到。够不着的判断该在挑目标的时候做。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void whatIsOverHerHeadIsNotWorthNoddingAt(
            GameTestHelper helper
    ) {
        EntityMaid maid = maid(helper, 2, 2, 1, true);
        ItemEntity high = floating(helper, Items.COBBLESTONE, 3, 6, 1);

        helper.assertTrue(
                execute(helper, maid) == ActionResult.FAILED,
                "She set out for something four blocks over her head"
        );
        helper.assertTrue(high.isAlive(), "The unreachable drop vanished");
        helper.succeed();
    }

    /**
     * 但高一级的台子上那件还是要去——跳一下够得着。
     *
     * <p>上界卡得太紧的话，她会放着脚边台阶上的东西不管，那和点头一样是毛病，
     * 只是方向相反。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void oneStepUpIsStillWorthGoingFor(GameTestHelper helper) {
        EntityMaid maid = maid(helper, 1, 2, 1, true);
        ItemEntity step = floating(helper, Items.COBBLESTONE, 6, 3, 1);

        helper.assertTrue(
                execute(helper, maid) == ActionResult.RUNNING,
                "A drop one step up was written off as out of reach"
        );
        helper.assertTrue(
                walkingToward(maid, step),
                "She was not sent anywhere near it"
        );
        helper.succeed();
    }

    /**
     * 正好跳一下够得着的那件，她真的会跳。
     *
     * <p>把跳跃算进"够不够得着"只让她**愿意去**——原版只在寻路要迈台阶时才跳，
     * 而悬在两格高处的东西没有路可走，寻路当场报走不到。第一版就停在这里：判据
     * 放行了，她却站着不动，实机看是"对着明明够得到的东西毫无反应"。
     *
     * <p>断的是她**离了地**。收没收到不算数——那一下由本体的自动拾取在跳跃顶点
     * 完成，掺进来就等于把这条测试押在碰撞箱的零点几格上。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 260)
    public static void sheJumpsForWhatIsJustOutOfArmsReach(
            GameTestHelper helper
    ) {
        // 站远一点。第一版把东西摆在她正上方，于是她一步没走就跳了——而实机里
        // 她要先走到底下，寻路的目标却是半空中那个点。那一段才是真正会坏的地方。
        EntityMaid maid = maid(helper, 2, 2, 1, true);
        double floor = maid.getY();
        floating(helper, Items.COBBLESTONE, 7, 4, 1);
        TlmMaidIntentActions actions = actions();
        boolean[] airborne = new boolean[]{false};

        for (int tick = 1; tick <= JUMP_WATCH; tick++) {
            helper.runAfterDelay(tick, () -> {
                actions.execute(
                        maid,
                        CompanionIntentIds.PICK_UP_LOOSE_DROP,
                        PARAMETERS,
                        helper.getLevel().getGameTime(),
                        0
                );
                if (maid.getY() - floor > OFF_THE_GROUND) {
                    airborne[0] = true;
                }
            });
        }

        helper.runAfterDelay(JUMP_WATCH, () -> {
            helper.assertTrue(
                    airborne[0],
                    "She never left the ground for a drop one jump up"
                            + "; she got to " + maid.blockPosition()
                            + " and the drop is at 7,4,1 (relative)"
            );
            helper.succeed();
        });
    }

    /**
     * 两格高柱子顶上那件东西：她**不该**去。
     *
     * <p>玩家报的正是这个摆法。柱子是实心的，所以她走不到正下方——只能停在旁边，
     * 水平隔着一格。而拾取判定是碰撞箱外扩半格（半宽约 0.8），跳到顶点也够不到
     * 水平一格外的东西。四周又没有一格高的方块可以踩上去。
     *
     * <p>所以这件东西对她是**真的拿不到**，正确的行为是根本不列为目标——去了就是
     * 走到旁边一通蹦，然后五秒后放弃，中间那五秒就是玩家看到的样子。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 260)
    public static void aDropOnATwoHighPillarIsNotWorthTrying(
            GameTestHelper helper
    ) {
        EntityMaid maid = maid(helper, 2, 2, 1, true);
        helper.setBlock(new BlockPos(7, 2, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(7, 3, 1), Blocks.STONE);
        ItemEntity onTop = floating(helper, Items.COBBLESTONE, 7, 4, 1);

        helper.assertTrue(
                execute(helper, maid) == ActionResult.FAILED,
                "She set out for a drop on top of a two-high pillar"
        );
        helper.assertTrue(onTop.isAlive(), "The unreachable drop vanished");
        helper.succeed();
    }

    /** 看多久够她起一次跳。 */
    private static final int JUMP_WATCH = 160;

    /** 抬高多少算离地。跳跃顶点约一格四分之一，这里只要能和站着分开。 */
    private static final double OFF_THE_GROUND = 0.3D;

    /** 悬在空中不掉下来的一件——台子上、栅栏上那种。 */
    private static ItemEntity floating(
            GameTestHelper helper,
            Item item,
            int x,
            int y,
            int z
    ) {
        ItemEntity entity = drop(helper, item, x, y, z);
        entity.setNoGravity(true);
        return entity;
    }

    /**
     * 经验和物品一视同仁：远处的值得走一趟，脚边的当场收走。
     *
     * <p>本体的 `canPickup` 一直就认经验球（`canPickXp()`），少的同样只是走那
     * 一半：从前广告主只扫 `ItemEntity`，于是隔着几格的经验她一步都不迈。
     *
     * <p>两拍都不等她真的走完。第一版是"扔一颗、等两百 tick、看还在不在"，在共享
     * 的测试世界里时红时绿——十六格感知半径够得着隔壁结构，寻路耗时又没有上界。
     * 这里改成量动作本身：远处那颗让她拿到指向它的移动目标，挪到跟前那颗当场消失。
     *
     * <p>先让那颗球活几 tick。**刚落地的经验球是收不走的**——本体的
     * {@code pickupXPOrb} 要求它至少活过两 tick，那是原版留给经验球互相合并的
     * 窗口。这条测试第一次就是这么红的："她站在球上，球还在"。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            timeoutTicks = 100)
    public static void experienceIsWorthTheSameWalk(GameTestHelper helper) {
        EntityMaid maid = maid(helper, 2, 2, 1, true);
        ExperienceOrb orb = orb(helper, 7, 2, 1);

        helper.runAfterDelay(ORB_SETTLED_TICKS, () -> {
            helper.assertTrue(
                    execute(helper, maid) == ActionResult.RUNNING,
                    "A distant orb did not start an errand"
            );
            helper.assertTrue(
                    walkingToward(maid, orb),
                    "She was not sent anywhere near the orb"
            );

            maid.setPos(orb.position());
            helper.assertTrue(
                    execute(helper, maid) == ActionResult.RUNNING,
                    "Standing on the orb ended the sweep"
            );
            helper.assertFalse(orb.isAlive(), "The orb was left lying there");
            helper.succeed();
        });
    }

    private static ExperienceOrb orb(
            GameTestHelper helper,
            int x,
            int y,
            int z
    ) {
        Vec3 where = GameTestPositions.center(helper, x, y, z);
        ExperienceOrb orb = new ExperienceOrb(
                helper.getLevel(), where.x, where.y, where.z, 7
        );
        orb.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(orb);
        return orb;
    }

    // 这里曾经有一条"三件全部收完"的实景测试，删掉了：清扫的感知半径十六格，
    // 而 GameTest 的结构在同一个世界里紧挨着排，别的测试的女仆会走进来把这里的
    // 东西收走——同一份代码一红一绿，量到的是邻居今天来没来过。它要证明的两件事
    // 现在各有一条确定性测试，见 LooseDropContinuityGameTests：收 N 件正好花
    // N 个 tick，以及收下一件的那一拍她没有松开移动目标。

    /**
     * 打起来的时候战利品先放着。
     *
     * <p>条件里那句 {@code attack_target_present == 0} 就是"从战斗切换成平静"的
     * 全部实现——她不是打完之后被谁通知去捡，是打的时候这个意图根本不合格。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void whileSomethingIsOnHerTheLootWaits(
            GameTestHelper helper
    ) {
        EntityMaid maid = maid(helper, 2, 2, 1, true);
        drop(helper, Items.COBBLESTONE, 7, 2, 1);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 1));
        maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, zombie);

        helper.runAfterDelay(SETTLED_TICKS, () -> {
            IntentTrace trace = productionIntents().inspect(maid);
            helper.assertFalse(
                    sweeping(trace),
                    "She went shopping with something attacking her: " + trace
            );
            zombie.discard();
            helper.succeed();
        });
    }

    private static boolean sweeping(IntentTrace trace) {
        return trace.activeIntent() != null
                && "loose_drop_pickup".equals(trace.activeIntent().path());
    }

    @SuppressWarnings("unchecked")
    private static MaidIntentApi<EntityMaid> productionIntents() {
        return (MaidIntentApi<EntityMaid>) AdapterRuntime.require(
                MaidIntentApi.class
        );
    }

    private static boolean walkingToward(EntityMaid maid, Entity target) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(walk -> walk.getTarget()
                        .currentPosition()
                        .closerThan(target.position(), 2.0D))
                .orElse(false);
    }

    private static boolean packHolds(EntityMaid maid, Item item) {
        IItemHandler pack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < pack.getSlots(); slot++) {
            if (pack.getStackInSlot(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 每一格都塞上不可堆叠的东西——包括两只手。
     *
     * <p>填的是 {@code getAvailableInv(false)} 本人，也就是 {@code canPickup}
     * 拿去模拟插入的那一个。填别的容器等于没填。
     */
    private static void fillEverySlot(EntityMaid maid) {
        IItemHandler inventory = maid.getAvailableInv(false);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            inventory.insertItem(
                    slot, new ItemStack(Items.NETHERITE_HELMET), false
            );
        }
    }

    private static ActionResult execute(
            GameTestHelper helper,
            EntityMaid maid
    ) {
        return actions().execute(
                maid,
                CompanionIntentIds.PICK_UP_LOOSE_DROP,
                PARAMETERS,
                helper.getLevel().getGameTime(),
                0
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

    private static EntityMaid maid(
            GameTestHelper helper,
            int x,
            int y,
            int z,
            boolean pickup
    ) {
        // 铺宽一点，并且围起来。宽是因为掉落物有随机初速度，会从窄地板边上滑出
        // 去掉进虚空，那看起来和"她不去捡"一模一样；围墙是因为清扫的感知半径有
        // 十六格，不围的话别的测试的女仆会走进来把这里的东西收走。
        for (int floorX = -1; floorX <= 11; floorX++) {
            for (int floorZ = -1; floorZ <= 4; floorZ++) {
                helper.setBlock(new BlockPos(floorX, 1, floorZ), Blocks.STONE);
                boolean edge = floorX == -1 || floorX == 11
                        || floorZ == -1 || floorZ == 4;
                if (edge) {
                    helper.setBlock(
                            new BlockPos(floorX, 2, floorZ), Blocks.STONE);
                    helper.setBlock(
                            new BlockPos(floorX, 3, floorZ), Blocks.STONE);
                }
            }
        }
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, x, y, z));
        maid.setTame(true);
        maid.setPickup(pickup);
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
        // 摆在哪儿就待在哪儿。`ItemEntity` 的构造函数自带一个随机初速度，
        // 三件东西各滑向一个方向之后，"她收齐了没有"就变成了掷骰子。
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }
}
