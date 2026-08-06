package com.laixia.maidintelligence.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
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
        Player owner = helper.makeMockPlayer();
        EntityMaid maid = maid(helper, owner, 1, 2, 1);
        chair(helper, 8, 2, 1);

        helper.assertTrue(
                run(helper, maid, CompanionIntentIds.REST_ON_SEAT)
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
        Player owner = helper.makeMockPlayer();
        EntityMaid maid = maid(helper, owner, 1, 2, 1);
        Entity chair = chair(helper, 2, 2, 1);

        helper.assertTrue(
                run(helper, maid, CompanionIntentIds.REST_ON_SEAT)
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
        Player owner = helper.makeMockPlayer();
        EntityMaid first = maid(helper, owner, 1, 2, 1);
        EntityMaid second = maid(helper, owner, 3, 2, 1);
        chair(helper, 2, 2, 1);
        TlmMaidIntentActions actions = actions();
        long gameTime = helper.getLevel().getGameTime();

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
        Player owner = helper.makeMockPlayer();
        EntityMaid maid = maid(helper, owner, 1, 2, 1);

        helper.assertTrue(
                run(helper, maid, CompanionIntentIds.REST_ON_SEAT)
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
        Player owner = helper.makeMockPlayer();
        EntityMaid first = maid(helper, owner, 1, 2, 1);
        EntityMaid second = maid(helper, owner, 2, 2, 1);

        helper.assertTrue(
                run(helper, first, CompanionIntentIds.REST_ON_SEAT)
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
        Player owner = helper.makeMockPlayer();
        owner.setPos(GameTestPositions.center(helper, 9, 2, 1));
        EntityMaid first = maid(helper, owner, 1, 2, 1);
        EntityMaid second = maid(helper, owner, 1, 2, 2);
        TlmMaidIntentActions actions = actions();
        long gameTime = helper.getLevel().getGameTime();

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
        EntityMaid maid = new EntityMaid(helper.getLevel());
        floor(helper);
        maid.setPos(GameTestPositions.center(helper, 1, 2, 1));
        helper.getLevel().addFreshEntity(maid);

        helper.assertTrue(
                run(helper, maid, CompanionIntentIds.KEEP_COMPANY)
                        == ActionResult.FAILED,
                "An untamed maid went to keep somebody company"
        );
        helper.succeed();
    }

    private static ActionResult run(
            GameTestHelper helper,
            EntityMaid maid,
            OrchestrationId action
    ) {
        return actions().execute(
                maid,
                action,
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

    private static Entity chair(
            GameTestHelper helper,
            int x,
            int y,
            int z
    ) {
        Vec3 position = GameTestPositions.center(helper, x, y, z);
        // The constructor is protected; the registered type is the way in.
        EntityChair chair = EntityChair.TYPE.create(helper.getLevel());
        if (chair == null) {
            throw new AssertionError("A chair could not be created");
        }
        chair.setPos(position.x, position.y, position.z);
        helper.getLevel().addFreshEntity(chair);
        return chair;
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    private static EntityMaid maid(
            GameTestHelper helper,
            Player owner,
            int x,
            int y,
            int z
    ) {
        floor(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(GameTestPositions.center(helper, x, y, z));
        maid.setTame(true);
        maid.setHomeModeEnable(false);
        maid.setOwnerUUID(owner.getUUID());
        helper.getLevel().addFreshEntity(maid);
        return maid;
    }
}
