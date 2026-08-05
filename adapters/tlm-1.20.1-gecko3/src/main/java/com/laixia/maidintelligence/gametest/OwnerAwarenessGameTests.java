package com.laixia.maidintelligence.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.owner.OwnerFacts;
import com.laixia.maidintelligence.feature.behavior.handler.OwnerGazeRecallHandler;
import com.laixia.maidintelligence.feature.orchestration.tlm.context.TlmOwnerFactReader;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import com.laixia.maidintelligence.gametest.support.GameTestPositions;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * What a maid notices about her owner and about what is lying around her.
 *
 * <p>These are the claims that only a running world can settle. That aim finds
 * her through a wall is a statement about real block collision; that her owner
 * is holding food is a statement about a real inventory; that a thrown steak
 * becomes visible to her is a statement about real entities being indexed. The
 * plain unit suites alongside them can check the arithmetic of all three, and
 * none of the three.
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class OwnerAwarenessGameTests {
    private static final double GAZE_RANGE = 16.0D;

    private OwnerAwarenessGameTests() {
    }

    // Aim.

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aimFindsTheMaidLookedAt(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        lookAt(fixture.owner(), fixture.maid());

        helper.assertTrue(
                OwnerGazeRecallHandler.findAimedAtMaid(
                        fixture.owner(),
                        GAZE_RANGE
                ) == fixture.maid(),
                "Aiming straight at the maid did not find her"
        );
        helper.succeed();
    }

    /**
     * Addressing someone is not the same as having line of sight to them, and
     * a maid one room over is still the maid being spoken to.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aimReachesThroughWalls(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        for (int y = 2; y <= 4; y++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
            }
        }
        lookAt(fixture.owner(), fixture.maid());

        helper.assertTrue(
                OwnerGazeRecallHandler.findAimedAtMaid(
                        fixture.owner(),
                        GAZE_RANGE
                ) == fixture.maid(),
                "A stone wall hid the maid from the owner's aim"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aimingElsewhereFindsNobody(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        lookAt(fixture.owner(), fixture.maid());
        // Turn a right angle away from her.
        fixture.owner().setYRot(fixture.owner().getYRot() + 90.0F);
        fixture.owner().setYHeadRot(fixture.owner().getYRot());

        helper.assertTrue(
                OwnerGazeRecallHandler.findAimedAtMaid(
                        fixture.owner(),
                        GAZE_RANGE
                ) == null,
                "Looking away still singled out a maid"
        );
        helper.succeed();
    }

    /**
     * Pointing at one of several is about which one is being pointed at, not
     * which one happens to be closest to the pointer.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aimPrefersTheCentredMaidOverTheNearer(
            GameTestHelper helper
    ) {
        Fixture fixture = fixture(helper);
        EntityMaid nearer = maid(helper, fixture.owner(), 3, 2, 2);
        lookAt(fixture.owner(), fixture.maid());

        EntityMaid aimed = OwnerGazeRecallHandler.findAimedAtMaid(
                fixture.owner(),
                GAZE_RANGE
        );
        helper.assertTrue(aimed == fixture.maid(),
                "Aim picked the nearer maid instead of the one aimed at");
        helper.assertFalse(aimed == nearer,
                "Aim picked a maid off to the side");
        helper.succeed();
    }

    // Owner state.

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void heldFoodIsNoticed(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        Player owner = fixture.owner();
        TlmOwnerFactReader reader = new TlmOwnerFactReader();
        long gameTime = helper.getLevel().getGameTime();

        OwnerFacts empty = reader.read(owner, gameTime);
        helper.assertTrue(empty.handsEmpty() == 1.0D,
                "Empty hands did not read as empty");
        helper.assertTrue(empty.holdingFood() == 0.0D,
                "Empty hands read as holding food");

        owner.setItemInHand(
                InteractionHand.MAIN_HAND,
                new ItemStack(Items.COOKED_BEEF)
        );
        OwnerFacts fed = reader.read(owner, gameTime + 1L);
        helper.assertTrue(fed.holdingFood() == 1.0D,
                "A steak in hand was not noticed");
        helper.assertTrue(fed.heldFoodQuality() > 0.0D,
                "A steak was worth nothing");
        helper.assertTrue(fed.handsEmpty() == 0.0D,
                "Hands holding a steak read as empty");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void ownerVitalsAreRead(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        Player owner = fixture.owner();
        owner.setHealth(owner.getMaxHealth() / 2.0F);
        TlmOwnerFactReader reader = new TlmOwnerFactReader();

        OwnerFacts facts = reader.read(
                owner,
                helper.getLevel().getGameTime()
        );
        helper.assertTrue(
                facts.healthFraction() > 0.4D && facts.healthFraction() < 0.6D,
                "Half health read as " + facts.healthFraction()
        );
        helper.assertTrue(facts.onFire() == 0.0D,
                "An owner who is not alight read as burning");
        helper.assertTrue(!Double.isNaN(facts.foodFraction()),
                "A real player had no hunger reading");
        helper.succeed();
    }

    /** No owner is not the same as an owner with nothing going on. */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void noOwnerReadsAsUnknown(GameTestHelper helper) {
        TlmOwnerFactReader reader = new TlmOwnerFactReader();
        OwnerFacts facts = reader.read(
                null,
                helper.getLevel().getGameTime()
        );
        helper.assertTrue(Double.isNaN(facts.healthFraction()),
                "A missing owner reported a health reading");
        helper.assertTrue(Double.isNaN(facts.holdingFood()),
                "A missing owner reported what they were holding");
        helper.succeed();
    }

    // What is lying about.

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void thrownFoodBecomesVisible(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        EntityMaid maid = fixture.maid();
        ItemEntity steak = drop(helper, Items.COOKED_BEEF, 2, 2, 1);
        TlmAffordancePerceptionService perception =
                new TlmAffordancePerceptionService();

        List<ItemEntity> seen = perception.queryLooseFood(
                maid,
                8,
                helper.getLevel().getGameTime()
        );
        helper.assertTrue(seen.contains(steak),
                "A steak dropped beside her was not noticed, saw "
                        + seen.size() + " item(s)");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void inediblesAreNotAdvertisedAsFood(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ItemEntity cobble = drop(helper, Items.COBBLESTONE, 2, 2, 1);
        TlmAffordancePerceptionService perception =
                new TlmAffordancePerceptionService();

        List<ItemEntity> seen = perception.queryLooseFood(
                fixture.maid(),
                8,
                helper.getLevel().getGameTime()
        );
        helper.assertFalse(seen.contains(cobble),
                "Cobblestone was advertised as something to eat");
        helper.succeed();
    }

    /**
     * Nothing reports an item being picked up, so its advertisement has to
     * lapse on its own. This is the one place the index's expiry is what keeps
     * her from walking to a patch of empty ground.
     */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 200
    )
    public static void takenFoodStopsBeingAdvertised(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        EntityMaid maid = fixture.maid();
        ItemEntity steak = drop(helper, Items.COOKED_BEEF, 2, 2, 1);
        TlmAffordancePerceptionService perception =
                new TlmAffordancePerceptionService();
        long gameTime = helper.getLevel().getGameTime();

        helper.assertTrue(
                perception.queryLooseFood(maid, 8, gameTime).contains(steak),
                "The steak was never noticed to begin with"
        );
        steak.discard();

        // Past the advertisement's life, without it ever being re-observed.
        long later = gameTime + 120L;
        helper.assertTrue(
                perception.queryLooseFood(maid, 8, later).isEmpty(),
                "A vanished steak was still being advertised"
        );
        helper.succeed();
    }

    // Fixtures.

    private static Fixture fixture(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        Player owner = helper.makeMockPlayer();
        owner.setPos(GameTestPositions.center(helper, 1, 2, 1));
        EntityMaid maid = maid(helper, owner, 7, 2, 1);
        return new Fixture(owner, maid);
    }

    private static EntityMaid maid(
            GameTestHelper helper,
            Player owner,
            int x,
            int y,
            int z
    ) {
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

    private static ItemEntity drop(
            GameTestHelper helper,
            net.minecraft.world.item.Item item,
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
        // Freshly spawned drops ignore anyone for a moment; the point here is
        // whether she can see it, not whether she may take it yet.
        entity.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void lookAt(Player owner, EntityMaid maid) {
        Vec3 offset = maid.getEyePosition().subtract(owner.getEyePosition());
        double flat = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        float yaw = (float) (Math.toDegrees(
                Math.atan2(offset.z, offset.x)
        ) - 90.0D);
        float pitch = (float) -Math.toDegrees(Math.atan2(offset.y, flat));
        owner.setYRot(yaw);
        owner.setYHeadRot(yaw);
        owner.setXRot(pitch);
    }

    private record Fixture(Player owner, EntityMaid maid) {
    }
}
