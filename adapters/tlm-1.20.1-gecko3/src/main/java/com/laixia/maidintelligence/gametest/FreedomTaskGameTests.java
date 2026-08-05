package com.laixia.maidintelligence.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.FreedomMaidTask;
import com.laixia.maidintelligence.gametest.support.GameTestPositions;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.laixia.maidintelligence.feature.ai.tlm.TlmBehaviorOccupancyClassifier;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancyReason;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancySnapshot;

/**
 * The freedom task, whose whole job is to bring nothing of its own.
 *
 * <p>The checks that matter are the negative ones. That it registers is easy;
 * that it contributes no competing behaviour is the property the companion
 * orchestrator relies on, and the one a later edit could quietly break by
 * adding "just one" brain task.
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class FreedomTaskGameTests {
    private FreedomTaskGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void freedomTaskIsRegistered(GameTestHelper helper) {
        helper.assertTrue(
                TaskManager.findTask(FreedomMaidTask.UID).isPresent(),
                "The freedom task was not registered with TLM"
        );
        helper.succeed();
    }

    /**
     * The entire point. Anything returned here would be a second opinion about
     * where she should go, competing with the one the orchestrator holds.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void freedomContributesNoCompetingBehaviour(
            GameTestHelper helper
    ) {
        EntityMaid maid = maid(helper);
        int contributed = new FreedomMaidTask()
                .createBrainTasks(maid)
                .size();
        helper.assertTrue(contributed == 0,
                "The freedom task added " + contributed
                        + " brain task(s) of its own");
        helper.succeed();
    }

    /**
     * Freedom is not helplessness: taking away danger and food would leave a
     * maid who starves in a fire while deciding what to do about it.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void freedomKeepsSelfPreservation(GameTestHelper helper) {
        EntityMaid maid = maid(helper);
        FreedomMaidTask task = new FreedomMaidTask();

        helper.assertTrue(task.enablePanic(maid),
                "A maid set free could not flee danger");
        helper.assertTrue(task.enableEating(maid),
                "A maid set free could not eat");
        helper.assertTrue(task.enableLookAndRandomWalk(maid),
                "A maid set free stood frozen between decisions");
        helper.assertFalse(task.workPointTask(maid),
                "A task with no work claimed a work point");
        helper.succeed();
    }

    /** Assigning it must actually take, and must be what she reports being on. */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aMaidCanBeSetFree(GameTestHelper helper) {
        EntityMaid maid = maid(helper);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());

        helper.assertTrue(
                FreedomMaidTask.UID.equals(maid.getTask().getUid()),
                "The maid did not take the freedom task"
        );
        helper.succeed();
    }

    /**
     * Home mode used to report hard occupancy for as long as it was switched
     * on, which blocked every companion intent outright — she would stand at
     * home hungry with food at her feet. Being home is a place, not an errand.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void beingHomeIsNotBeingBusy(GameTestHelper helper) {
        EntityMaid maid = maid(helper);
        maid.setHomeModeEnable(true);
        maid.restrictTo(maid.blockPosition(), 8);
        long gameTime = helper.getLevel().getGameTime();

        BehaviorOccupancySnapshot atHome =
                TlmBehaviorOccupancyClassifier.snapshot(maid, gameTime);
        helper.assertTrue(
                atHome.reason() != BehaviorOccupancyReason.HOME_RETURN,
                "Standing inside her own home counted as returning to it"
        );
        helper.succeed();
    }

    /** But actually walking back is an errand, and does occupy her. */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void walkingHomeStillOccupiesHer(GameTestHelper helper) {
        EntityMaid maid = maid(helper);
        maid.setHomeModeEnable(true);
        // A home she is demonstrably not standing in.
        maid.restrictTo(maid.blockPosition().offset(200, 0, 200), 4);

        BehaviorOccupancySnapshot away =
                TlmBehaviorOccupancyClassifier.snapshot(
                        maid,
                        helper.getLevel().getGameTime()
                );
        helper.assertTrue(
                away.reason() == BehaviorOccupancyReason.HOME_RETURN,
                "A maid far from home was not counted as heading back, got "
                        + away.reason()
        );
        helper.succeed();
    }

    private static EntityMaid maid(GameTestHelper helper) {
        for (int x = 0; x <= 3; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, 1, 2, 1));
        maid.setTame(true);
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        return maid;
    }
}
