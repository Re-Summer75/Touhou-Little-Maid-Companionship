package com.laixia.maidintelligence.gametest.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentActions;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * Keeping near an anchor: one errand whether the anchor walks or stands still.
 *
 * <p>As elsewhere, nothing here waits for her to arrive. Where she was sent is
 * this action's decision; how long the walk takes is the engine's business, and
 * a test that timed it would fail on a slow run for no useful reason.
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class ProximityErrandGameTests {
    private static final Map<String, String> PARAMETERS =
            Map.of("speed", "0.6", "close_distance", "3");

    private ProximityErrandGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aDistantOwnerIsWalkedToward(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 6, 2)
                .ownerAt(6, 2, 1);
        EntityMaid maid = scene.maid(1, 2, 1);

        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.FOLLOW_OWNER_ANCHOR)
                        == ActionResult.RUNNING,
                "Following a distant owner did not report as running"
        );
        helper.assertTrue(headingFor(maid, scene.owner()),
                "She was not sent toward her owner");
        helper.succeed();
    }

    /**
     * The reason this errand reserves nothing. A claim would have let the first
     * maid to set off hold the only one, stranding every other maid in the
     * household where she stood.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void everyMaidMayFollowTheSameOwner(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 6, 2)
                .ownerAt(6, 2, 1);
        EntityMaid first = scene.maid(1, 2, 1);
        EntityMaid second = scene.maid(1, 2, 2);
        TlmMaidIntentActions actions = scene.actions();
        long gameTime = scene.gameTime();

        ActionResult one = execute(actions, first, gameTime);
        ActionResult two = execute(actions, second, gameTime);
        helper.assertTrue(
                one == ActionResult.RUNNING && two == ActionResult.RUNNING,
                "Two maids could not follow one owner (" + one + ", "
                        + two + ")"
        );
        helper.assertTrue(
                headingFor(first, scene.owner())
                        && headingFor(second, scene.owner()),
                "Only one of the two was actually sent"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void arrivingAtTheOwnerFinishes(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2)
                .ownerAt(2, 2, 1);
        EntityMaid maid = scene.maid(1, 2, 1);

        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.FOLLOW_OWNER_ANCHOR)
                        == ActionResult.SUCCEEDED,
                "Standing beside her owner did not count as having arrived"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aStrayedMaidIsSentHome(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 6, 2);
        EntityMaid maid = scene.maid(6, 2, 1);
        BlockPos home = scene.at(1, 2, 1);
        maid.setHomeModeEnable(true);
        maid.restrictTo(home, 4);

        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.RETURN_HOME_ANCHOR)
                        == ActionResult.RUNNING,
                "A maid far from home was not sent back"
        );
        helper.assertTrue(headingFor(maid, home),
                "She was not sent to her home");
        helper.succeed();
    }

    /** Having no home means nowhere to be sent, not the world origin. */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void withoutAHomeThereIsNowhereToGo(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        maid.setHomeModeEnable(false);

        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.RETURN_HOME_ANCHOR)
                        == ActionResult.FAILED,
                "A maid with no home was sent somewhere anyway"
        );
        helper.assertFalse(
                maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "A maid with no home was given a walk target"
        );
        helper.succeed();
    }

    private static ActionResult run(
            CompanionScene scene,
            EntityMaid maid,
            OrchestrationId action
    ) {
        return scene.actions().execute(
                maid,
                action,
                PARAMETERS,
                scene.gameTime(),
                0
        );
    }

    private static ActionResult execute(
            TlmMaidIntentActions actions,
            EntityMaid maid,
            long gameTime
    ) {
        return actions.execute(
                maid,
                CompanionIntentIds.FOLLOW_OWNER_ANCHOR,
                PARAMETERS,
                gameTime,
                0
        );
    }

    private static boolean headingFor(EntityMaid maid, Player owner) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(WalkTarget::getTarget)
                .filter(EntityTracker.class::isInstance)
                .map(EntityTracker.class::cast)
                .map(EntityTracker::getEntity)
                .filter(owner::equals)
                .isPresent();
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
