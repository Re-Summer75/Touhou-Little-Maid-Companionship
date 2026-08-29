package com.laixia.maidintelligence.gametest.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentActions;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;
import com.laixia.maidintelligence.gametest.support.GameTestPositions;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * 够不够得着：头顶的不去、一步台阶的去、正好一跳的真跳、两格柱顶的不碰。
 *
 * <p>从 {@code LooseDropGameTests} 按族拆出（单文件五百行的布局纪律）。
 * 四条钉的是同一把尺——拾取的可达性判断发生在挑目标的时候，而不是走到跟前
 * 才发现（那就是玩家看到的"对着东西点头"）。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class PickupReachGameTests {
    private static final Map<String, String> PARAMETERS =
            Map.of("speed", "0.7", "close_distance", "1");

    /** 看多久够她起一次跳。 */
    private static final int JUMP_WATCH = 160;

    /** 抬高多少算离地。跳跃顶点约一格四分之一，这里只要能和站着分开。 */
    private static final double OFF_THE_GROUND = 0.3D;

    private PickupReachGameTests() {
    }

    /**
     * 头顶够不着的那件，她连去都不去。
     *
     * <p>没有这一条时她会走到它正下方，然后"到了没到"地反复判断——人看着就是在
     * 原地点头，而那件东西她永远拿不到。够不着的判断该在挑目标的时候做。
     */
    @GameTest(batch = "pickupreach", templateNamespace = "minecraft", template = "empty")
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
    @GameTest(batch = "pickupreach", templateNamespace = "minecraft", template = "empty")
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
    @GameTest(batch = "pickupreach", templateNamespace = "minecraft", template = "empty",
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
     * <p>实测的正是这个摆法。柱子是实心的，所以她走不到正下方——只能停在旁边，
     * 水平隔着一格。而拾取判定是碰撞箱外扩半格（半宽约 0.8），跳到顶点也够不到
     * 水平一格外的东西。四周又没有一格高的方块可以踩上去。
     *
     * <p>所以这件东西对她是**真的拿不到**，正确的行为是根本不列为目标——去了就是
     * 走到旁边一通蹦，然后五秒后放弃，中间那五秒就是玩家看到的样子。
     */
    @GameTest(batch = "pickupreach", templateNamespace = "minecraft", template = "empty",
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

    private static boolean walkingToward(EntityMaid maid, Entity target) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(walk -> walk.getTarget()
                        .currentPosition()
                        .closerThan(target.position(), 2.0D))
                .orElse(false);
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
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
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
        // 摆在哪儿就待在哪儿：构造函数自带的随机初速度会把断言变成掷骰子。
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }
}
