package com.laixia.maidintelligence.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentActions;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * Sitting down, and drifting over to her owner for its own sake.
 *
 * <p>Both errands were built on advertisements that already existed and that
 * nothing had ever asked for — a chair beside her was invisible to a maid left
 * to herself, and an owner advertising company was advertising to nobody.
 *
 * <p>The pair also sit on opposite sides of the claim rule, which is the thing
 * most worth pinning: a chair holds one maid, a person does not.
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class SeatAndCompanyGameTests {
    private static final Map<String, String> PARAMETERS =
            Map.of("speed", "0.5", "close_distance", "3");

    private SeatAndCompanyGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aFreeChairIsWalkedToward(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 6, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        scene.chair(6, 2, 1);

        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.REST_ON_SEAT)
                        == ActionResult.RUNNING,
                "A free chair across the room was not walked toward"
        );
        helper.assertTrue(
                maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "She was not sent anywhere"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aChairWithinReachIsSatOn(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        Entity chair = scene.chair(2, 2, 1);

        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.REST_ON_SEAT)
                        == ActionResult.SUCCEEDED,
                "A chair at her feet was not sat on"
        );
        helper.assertTrue(chair.equals(maid.getVehicle()),
                "She did not end up on the chair");
        helper.succeed();
    }

    /** One chair holds one person, so the second maid must be turned away. */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void twoMaidsDoNotShareOneChair(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid first = scene.maid(1, 2, 1);
        EntityMaid second = scene.maid(3, 2, 1);
        scene.chair(2, 2, 1);
        TlmMaidIntentActions actions = scene.actions();
        long gameTime = scene.gameTime();

        ActionResult one = actions.execute(
                first,
                CompanionIntentIds.REST_ON_SEAT,
                PARAMETERS,
                gameTime,
                0
        );
        ActionResult two = actions.execute(
                second,
                CompanionIntentIds.REST_ON_SEAT,
                PARAMETERS,
                gameTime,
                0
        );
        int seated = (one == ActionResult.SUCCEEDED ? 1 : 0)
                + (two == ActionResult.SUCCEEDED ? 1 : 0);
        helper.assertTrue(seated == 1,
                "Exactly one maid should have taken the chair, " + seated
                        + " did (" + one + ", " + two + ")");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void nothingToSitOnFailsCleanly(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);

        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.REST_ON_SEAT)
                        == ActionResult.FAILED,
                "An empty room did not fail cleanly"
        );
        helper.assertFalse(
                maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "She was sent somewhere with nothing to sit on"
        );
        helper.succeed();
    }

    /**
     * Two idle maids standing together must not sit on each other.
     *
     * <p>The seat query answers "would this accept her as a passenger", which
     * a colleague does. Each maid then rode the other, and because a mob's
     * navigation follows the vehicle it is controlling, the two pointed at each
     * other and the server died of a stack overflow rather than misbehaving
     * visibly.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void maidsDoNotSitOnEachOther(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid first = scene.maid(1, 2, 1);
        EntityMaid second = scene.maid(2, 2, 1);

        helper.assertTrue(
                run(scene, first, CompanionIntentIds.REST_ON_SEAT)
                        == ActionResult.FAILED,
                "A maid treated her colleague as somewhere to sit"
        );
        helper.assertFalse(first.isPassenger(),
                "One maid ended up riding the other");
        helper.assertFalse(second.isPassenger(),
                "One maid ended up carrying the other");
        helper.succeed();
    }

    /**
     * The mirror of the chair rule, and the reason keeping company reserves
     * nothing: a person can be kept company by more than one maid.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void severalMaidsMayKeepOneOwnerCompany(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 6, 2)
                .ownerAt(6, 2, 1);
        EntityMaid first = scene.maid(1, 2, 1);
        EntityMaid second = scene.maid(1, 2, 2);
        TlmMaidIntentActions actions = scene.actions();
        long gameTime = scene.gameTime();

        ActionResult one = actions.execute(
                first,
                CompanionIntentIds.KEEP_COMPANY,
                PARAMETERS,
                gameTime,
                0
        );
        ActionResult two = actions.execute(
                second,
                CompanionIntentIds.KEEP_COMPANY,
                PARAMETERS,
                gameTime,
                0
        );
        helper.assertTrue(
                one == ActionResult.RUNNING && two == ActionResult.RUNNING,
                "Two maids could not keep one owner company (" + one + ", "
                        + two + ")"
        );
        helper.succeed();
    }

    /** She goes to her own owner, and has no business with anyone else's. */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void companyRequiresAnOwner(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid stray = scene.strayMaid(1, 2, 1);

        helper.assertTrue(
                run(scene, stray, CompanionIntentIds.KEEP_COMPANY)
                        == ActionResult.FAILED,
                "An untamed maid went to keep somebody company"
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
}
