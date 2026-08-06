package com.laixia.maidintelligence.gametest.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentActions;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * A household rather than a bare box: several maids, furniture, a boat, and a
 * maid already occupied.
 *
 * <p>Every other suite puts one maid alone with one thing to react to, which is
 * how a behaviour gets to look correct while being wrong. The seating crash
 * that took down the whole server only appeared because one scenario happened
 * to contain a second maid; these scenarios contain the second maid on purpose,
 * along with everything else a real house has lying about.
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class PopulatedHouseholdGameTests {
    private static final Map<String, String> PARAMETERS =
            Map.of("speed", "0.5", "close_distance", "3");

    private PopulatedHouseholdGameTests() {
    }

    /**
     * Three maids, one chair, one steak. Nobody should end up doing two things,
     * and nobody should end up doing somebody else's.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aCrowdedRoomIsDividedUp(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid first = scene.maid(1, 2, 1);
        EntityMaid second = scene.maid(2, 2, 1);
        EntityMaid third = scene.maid(3, 2, 1);
        scene.chair(1, 2, 2);
        scene.drop(Items.COOKED_BEEF, 3, 2, 2);
        TlmMaidIntentActions actions = scene.actions();
        long gameTime = scene.gameTime();

        int seated = 0;
        for (EntityMaid maid : new EntityMaid[]{first, second, third}) {
            if (actions.execute(
                    maid,
                    CompanionIntentIds.REST_ON_SEAT,
                    PARAMETERS,
                    gameTime,
                    0
            ) == ActionResult.SUCCEEDED) {
                seated++;
            }
        }
        helper.assertTrue(seated <= 1,
                seated + " maids sat on one chair");

        int fed = 0;
        for (EntityMaid maid : new EntityMaid[]{first, second, third}) {
            if (actions.execute(
                    maid,
                    CompanionIntentIds.PICK_UP_LOOSE_FOOD,
                    PARAMETERS,
                    gameTime,
                    0
            ) == ActionResult.SUCCEEDED) {
                fed++;
            }
        }
        helper.assertTrue(fed <= 1,
                fed + " maids ate the same steak");
        helper.succeed();
    }

    /**
     * Furniture only. Asked whether something would take a passenger, vanilla
     * says yes to very nearly anything, and a maid offered the choice between a
     * chair and a dropped steak once sat down on the steak.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void foodIsNotFurniture(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        ItemEntity steak = scene.drop(Items.COOKED_BEEF, 2, 2, 1);

        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.REST_ON_SEAT)
                        == ActionResult.FAILED,
                "A steak on the floor was treated as somewhere to sit"
        );
        helper.assertFalse(maid.isPassenger(),
                "She perched on something that was not furniture");
        helper.assertTrue(steak.isAlive(),
                "The steak was disturbed by being sat on");
        helper.succeed();
    }

    /**
     * A boat is furniture too, and the errand that looks for somewhere to sit
     * has no reason to know the difference.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aBoatCountsAsSomewhereToSit(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        Boat boat = scene.boat(2, 2, 1);

        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.REST_ON_SEAT)
                        == ActionResult.SUCCEEDED,
                "A boat beside her was not sat in"
        );
        helper.assertTrue(boat.equals(maid.getVehicle()),
                "She did not end up in the boat");
        helper.succeed();
    }

    /** And once she is in it, she is a passenger and errands stop. */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aSeatedMaidRunsNoErrands(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        Boat boat = scene.boat(2, 2, 1);
        scene.drop(Items.COOKED_BEEF, 3, 2, 1);
        maid.startRiding(boat);

        helper.assertTrue(maid.isPassenger(),
                "The maid did not board the boat");
        helper.assertTrue(
                run(scene, maid, CompanionIntentIds.PICK_UP_LOOSE_FOOD)
                        == ActionResult.FAILED,
                "A maid sitting in a boat went for food anyway"
        );
        helper.succeed();
    }

    /**
     * Fighting outranks everything a companion errand might want. This is the
     * arbitration the whole design leans on, so a maid holding a target must be
     * seen to refuse every one of them, not merely the one that was checked.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aFightingMaidIgnoresEverythingElse(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        scene.chair(2, 2, 1);
        ItemEntity steak = scene.drop(Items.COOKED_BEEF, 2, 2, 2);
        scene.engageInCombat(maid);

        for (OrchestrationId errand : new OrchestrationId[]{
                CompanionIntentIds.REST_ON_SEAT,
                CompanionIntentIds.PICK_UP_LOOSE_FOOD,
                CompanionIntentIds.KEEP_COMPANY,
                CompanionIntentIds.FOLLOW_OWNER_ANCHOR
        }) {
            helper.assertTrue(
                    run(scene, maid, errand) == ActionResult.FAILED,
                    "A maid in combat still ran " + errand.path()
            );
        }
        helper.assertTrue(steak.isAlive() && !steak.getItem().isEmpty(),
                "The steak went missing during a fight");
        helper.assertFalse(maid.isPassenger(),
                "She sat down mid-fight");
        helper.succeed();
    }

    /** Her sister's fight is not hers, and must not stop her going about. */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void oneMaidsFightDoesNotGroundTheOthers(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid fighter = scene.maid(1, 2, 1);
        EntityMaid bystander = scene.maid(1, 2, 2);
        scene.chair(2, 2, 2);
        scene.engageInCombat(fighter);

        helper.assertTrue(
                run(scene, fighter, CompanionIntentIds.REST_ON_SEAT)
                        == ActionResult.FAILED,
                "The maid who is fighting sat down"
        );
        helper.assertTrue(
                run(scene, bystander, CompanionIntentIds.REST_ON_SEAT)
                        == ActionResult.SUCCEEDED,
                "A maid was grounded by somebody else's fight"
        );
        helper.succeed();
    }

    /**
     * Two maids, two chairs. Contention is meant to be per-chair, so both
     * should sit — a claim taken too coarsely would seat only one.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void twoChairsSeatTwoMaids(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid first = scene.maid(1, 2, 1);
        EntityMaid second = scene.maid(3, 2, 1);
        scene.chair(1, 2, 2);
        scene.chair(3, 2, 2);
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
        helper.assertTrue(
                one == ActionResult.SUCCEEDED && two == ActionResult.SUCCEEDED,
                "Two chairs did not seat two maids (" + one + ", " + two + ")"
        );
        helper.assertFalse(first.getVehicle() == second.getVehicle(),
                "Both maids ended up on the same chair");
        helper.succeed();
    }

    /**
     * A maid nobody owns has no owner to follow, keep company, or go home to,
     * and must say so rather than walking to the world origin.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void anUnownedMaidHasNoErrandsAboutAnOwner(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid stray = scene.strayMaid(3, 2, 1);

        for (OrchestrationId errand : new OrchestrationId[]{
                CompanionIntentIds.FOLLOW_OWNER_ANCHOR,
                CompanionIntentIds.KEEP_COMPANY,
                CompanionIntentIds.RETURN_HOME_ANCHOR
        }) {
            helper.assertTrue(
                    run(scene, stray, errand) == ActionResult.FAILED,
                    "An unowned maid ran " + errand.path()
            );
        }
        helper.assertFalse(
                stray.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "An unowned maid was sent somewhere"
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
