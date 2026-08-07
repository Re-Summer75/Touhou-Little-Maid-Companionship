package com.laixia.maidintelligence.gametest.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidJoyTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.tlm.FreedomMaidTask;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * Going to read, play chess or use the computer, as a ranked decision.
 *
 * <p>The behaviour itself is TLM's and is unchanged. What these pin is that the
 * decision to take it up now belongs to the orchestrator: the native task
 * stands down under the freedom task, and the errand that replaces it reserves
 * the block so two maids cannot settle at one bookshelf.
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class PastimeErrandGameTests {
    private static final Map<String, String> PARAMETERS =
            Map.of("speed", "0.5", "close_distance", "2");

    private PastimeErrandGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aPastimeAcrossTheRoomIsWalkedToward(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 8, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        BlockPos shelf = placeBookshelf(scene, helper, 6, 2, 1);

        helper.assertTrue(
                run(scene, maid) == ActionResult.RUNNING,
                "A free bookshelf did not start the errand"
        );
        helper.assertTrue(
                headingFor(maid, shelf),
                "She was not sent toward the bookshelf"
        );
        helper.succeed();
    }

    /**
     * A joy block seats one maid, so the second to want it must be told no
     * rather than allowed to walk over and find it taken.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void oneBookshelfSeatsOneMaid(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 8, 2);
        EntityMaid first = scene.maid(1, 2, 1);
        EntityMaid second = scene.maid(2, 2, 1);
        placeBookshelf(scene, helper, 6, 2, 1);

        helper.assertTrue(
                run(scene, first) == ActionResult.RUNNING,
                "The first maid was not sent to the only bookshelf"
        );
        helper.assertTrue(
                run(scene, second) == ActionResult.FAILED,
                "A second maid was sent to a bookshelf already claimed"
        );
        helper.assertFalse(
                second.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "The turned-away maid was still given a walk target"
        );
        helper.succeed();
    }

    /** Nothing to do is not the same as somewhere to go. */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void withoutAJoyBlockThereIsNoPastime(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 4, 2);
        EntityMaid maid = scene.maid(1, 2, 1);

        helper.assertTrue(
                run(scene, maid) == ActionResult.FAILED,
                "A maid with nothing to read was sent somewhere anyway"
        );
        helper.assertFalse(
                maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "A maid with nothing to read was given a walk target"
        );
        helper.succeed();
    }

    /**
     * The point of the takeover. Under the freedom task the native behaviour
     * has to decline, or it would take her before the orchestrator is asked and
     * the seat it produces would then block her from going to eat.
     *
     * <p>Checked against a maid on another task in the same room, because an
     * assertion that nothing happened proves nothing on its own — the same
     * result would follow from the bookshelf never having been found.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void theNativeBehaviourStandsDownUnderFreedom(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 8, 2);
        EntityMaid working = scene.maid(1, 2, 1);
        EntityMaid free = scene.maid(2, 2, 1);
        placeBookshelf(scene, helper, 6, 2, 1);
        free.setTask(new FreedomMaidTask());

        helper.assertTrue(
                FreedomMaidTask.UID.equals(free.getTask().getUid()),
                "The freedom task did not take effect"
        );
        helper.assertFalse(
                startNativePastime(helper, free),
                "The native pastime behaviour still decided for a free maid"
        );
        helper.assertTrue(
                startNativePastime(helper, working),
                "The native pastime behaviour stopped working for other tasks"
        );
        helper.succeed();
    }

    /**
     * Runs one attempt of TLM's own behaviour. It walks her closer on the first
     * passes and only reports started once she is beside the block, so the
     * whole check-rate window is stepped through rather than sampled once.
     */
    private static boolean startNativePastime(
            GameTestHelper helper,
            EntityMaid maid
    ) {
        MaidJoyTask task = new MaidJoyTask(0.6F, 2);
        for (int attempt = 0; attempt < 40; attempt++) {
            if (task.tryStart(
                    helper.getLevel(),
                    maid,
                    helper.getLevel().getGameTime() + attempt
            )) {
                return true;
            }
            if (maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos placeBookshelf(
            CompanionScene scene,
            GameTestHelper helper,
            int x,
            int y,
            int z
    ) {
        BlockPos position = scene.at(x, y, z);
        helper.getLevel().setBlockAndUpdate(
                position,
                InitBlocks.BOOKSHELF.get().defaultBlockState()
        );
        return position;
    }

    private static ActionResult run(CompanionScene scene, EntityMaid maid) {
        return scene.actions().execute(
                maid,
                CompanionIntentIds.USE_JOY_BLOCK,
                PARAMETERS,
                scene.gameTime(),
                0
        );
    }

    private static boolean headingFor(EntityMaid maid, BlockPos position) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(WalkTarget::getTarget)
                .filter(BlockPosTracker.class::isInstance)
                .map(BlockPosTracker.class::cast)
                .map(BlockPosTracker::currentBlockPosition)
                .filter(position::equals)
                .isPresent();
    }
}
