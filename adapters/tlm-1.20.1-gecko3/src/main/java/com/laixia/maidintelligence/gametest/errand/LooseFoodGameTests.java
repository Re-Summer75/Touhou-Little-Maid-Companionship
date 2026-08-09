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
import net.minecraft.world.entity.ai.behavior.EntityTracker;
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

import java.util.Map;

/**
 * Eating something off the floor, which is what makes throwing food at a maid
 * mean anything.
 *
 * <p>None of these wait for her to walk anywhere. Whether pathfinding gets her
 * across a room in some number of ticks is not what any of this is about, and a
 * test that waited on it would fail on a slow run and teach everyone to ignore
 * the result. So the item is either already within reach — making the outcome
 * one call — or deliberately out of reach, in which case what is checked is
 * where she was told to go, not whether she arrived.
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class LooseFoodGameTests {
    private static final Map<String, String> PARAMETERS =
            Map.of("speed", "0.6", "close_distance", "2");

    private LooseFoodGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void foodWithinReachIsEaten(GameTestHelper helper) {
        EntityMaid maid = maid(helper, 2, 2, 1);
        ItemEntity steak = drop(helper, Items.COOKED_BEEF, 3, 2, 1);

        helper.assertTrue(
                execute(helper, maid) == ActionResult.SUCCEEDED,
                "A steak at her feet was not eaten"
        );
        helper.assertFalse(steak.isAlive() && !steak.getItem().isEmpty(),
                "The steak was still lying there after being eaten");
        helper.succeed();
    }

    /**
     * The claim exists for exactly this. Both maids see the same drop and both
     * are told to take it in the same tick; one of them has to lose.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void twoMaidsDoNotShareOneDrop(GameTestHelper helper) {
        EntityMaid first = maid(helper, 2, 2, 1);
        EntityMaid second = maid(helper, 4, 2, 1);
        drop(helper, Items.COOKED_BEEF, 3, 2, 1);
        TlmMaidIntentActions actions = actions(helper);
        long gameTime = helper.getLevel().getGameTime();

        ActionResult one = execute(actions, first, gameTime);
        ActionResult two = execute(actions, second, gameTime);
        int winners = (one == ActionResult.SUCCEEDED ? 1 : 0)
                + (two == ActionResult.SUCCEEDED ? 1 : 0);
        helper.assertTrue(winners == 1,
                "Exactly one maid should have taken the drop, " + winners
                        + " did (" + one + ", " + two + ")");
        helper.succeed();
    }

    /**
     * Out of reach, so what is asserted is the decision — that she was sent to
     * the item — and not the walk, which is the engine's business and takes as
     * long as it takes.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void distantFoodIsWalkedToward(GameTestHelper helper) {
        EntityMaid maid = maid(helper, 1, 2, 1);
        ItemEntity steak = drop(helper, Items.COOKED_BEEF, 8, 2, 1);

        helper.assertTrue(
                execute(helper, maid) == ActionResult.RUNNING,
                "Walking to a distant steak did not report as running"
        );
        helper.assertTrue(
                maid.getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .map(WalkTarget::getTarget)
                        .filter(EntityTracker.class::isInstance)
                        .map(EntityTracker.class::cast)
                        .map(EntityTracker::getEntity)
                        .filter(steak::equals)
                        .isPresent(),
                "She was not sent to the steak"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void inedibleDropsAreIgnored(GameTestHelper helper) {
        EntityMaid maid = maid(helper, 2, 2, 1);
        ItemEntity cobble = drop(helper, Items.COBBLESTONE, 3, 2, 1);

        helper.assertTrue(
                execute(helper, maid) == ActionResult.FAILED,
                "Cobblestone was treated as a meal"
        );
        helper.assertTrue(cobble.isAlive(),
                "Cobblestone was taken anyway");
        helper.succeed();
    }

    /** A maid told to stay put stays put, whatever is lying next to her. */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aSeatedMaidLeavesFoodAlone(GameTestHelper helper) {
        EntityMaid maid = maid(helper, 2, 2, 1);
        maid.setOrderedToSit(true);
        ItemEntity steak = drop(helper, Items.COOKED_BEEF, 3, 2, 1);

        helper.assertTrue(
                execute(helper, maid) == ActionResult.FAILED,
                "A maid ordered to sit went for the food"
        );
        helper.assertTrue(steak.isAlive() && !steak.getItem().isEmpty(),
                "A seated maid took the steak");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void nothingToEatFailsCleanly(GameTestHelper helper) {
        EntityMaid maid = maid(helper, 2, 2, 1);

        helper.assertTrue(
                execute(helper, maid) == ActionResult.FAILED,
                "An empty floor did not fail cleanly"
        );
        helper.assertFalse(
                maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "She was sent somewhere with nothing to go to"
        );
        helper.succeed();
    }

    private static ActionResult execute(
            GameTestHelper helper,
            EntityMaid maid
    ) {
        return execute(
                actions(helper),
                maid,
                helper.getLevel().getGameTime()
        );
    }

    private static ActionResult execute(
            TlmMaidIntentActions actions,
            EntityMaid maid,
            long gameTime
    ) {
        return actions.execute(
                maid,
                CompanionIntentIds.PICK_UP_LOOSE_FOOD,
                PARAMETERS,
                gameTime,
                0
        );
    }

    private static TlmMaidIntentActions actions(GameTestHelper helper) {
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
            int z
    ) {
        for (int floorX = 0; floorX <= 9; floorX++) {
            for (int floorZ = 0; floorZ <= 3; floorZ++) {
                helper.setBlock(new BlockPos(floorX, 1, floorZ), Blocks.STONE);
            }
        }
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, x, y, z));
        maid.setTame(true);
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
        entity.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }
}
