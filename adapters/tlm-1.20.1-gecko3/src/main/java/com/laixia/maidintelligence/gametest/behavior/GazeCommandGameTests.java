package com.laixia.maidintelligence.gametest.behavior;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.tlm.MaidSeatAutonomyBridge;
import com.laixia.maidintelligence.feature.behavior.api.MaidGazeRecallApi;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.tlm.MaidCommandSeatBridge;
import com.laixia.maidintelligence.feature.interaction.handler.MaidInteractionHandler;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentActions;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class GazeCommandGameTests {
    private static final Map<String, String> COMMAND_PARAMETERS = Map.of(
            "duration_ticks", "60",
            "speed", "0.6",
            "close_distance", "2"
    );

    private GazeCommandGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void arrivalOpensDataDrivenCommandWindow(
            GameTestHelper helper
    ) {
        OwnedFixture fixture = ownedFixture(helper);
        fixture.maid().setPos(position(helper, 3, 1));
        fixture.maid().setFavorability(64);
        long gameTime = helper.getLevel().getGameTime();

        helper.assertTrue(
                productionGazeRecall().tryRecall(
                        fixture.owner(),
                        fixture.maid()
                ),
                "Gaze recall signal was rejected"
        );
        productionIntents().tick(fixture.maid(), gameTime);
        helper.assertTrue(
                "command_window".equals(
                        productionIntents().inspect(fixture.maid())
                                .activeState()
                ),
                "Arrival did not enter the command window"
        );

        fixture.owner().setPos(position(helper, 6, 1));
        productionIntents().tick(fixture.maid(), gameTime + 1L);
        assertTargetsOwner(helper, fixture);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void commandWindowFollowsForSixtyTicks(
            GameTestHelper helper
    ) {
        OwnedFixture fixture = ownedFixture(helper);
        TlmMaidIntentActions actions = actions();

        helper.assertTrue(
                execute(actions, fixture, 0) == ActionResult.RUNNING,
                "Command window did not start"
        );
        assertTargetsOwner(helper, fixture);
        fixture.owner().setPos(position(helper, 6, 1));
        helper.assertTrue(
                execute(actions, fixture, 59) == ActionResult.RUNNING,
                "Command window ended before three seconds"
        );
        assertTargetsOwner(helper, fixture);
        helper.assertTrue(
                execute(actions, fixture, 60) == ActionResult.SUCCEEDED,
                "Command window did not end after three seconds"
        );
        helper.assertTrue(
                fixture.maid().getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .isEmpty(),
                "Completed command window retained its movement target"
        );
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 40
    )
    public static void commandSeatPersistsUntilOwnerReleasesIt(
            GameTestHelper helper
    ) {
        OwnedFixture fixture = ownedFixture(helper);
        fixture.maid().setPos(position(helper, 3, 1));
        EntityChair ownerSeat = chair(helper, 4, 1);
        EntitySit maidSeat = new EntitySit(
                helper.getLevel(),
                position(helper, 3, 2),
                "tlm_companionship:command_test",
                helper.absolutePos(new BlockPos(3, 1, 2))
        );
        helper.getLevel().addFreshEntity(maidSeat);
        fixture.owner().startRiding(ownerSeat, true);

        TlmMaidIntentActions actions = actions();
        helper.assertTrue(
                execute(actions, fixture, 0) == ActionResult.RUNNING
                        && fixture.maid().getVehicle() == maidSeat,
                "Maid did not mirror the owner's seat"
        );
        helper.assertTrue(
                execute(actions, fixture, 60) == ActionResult.SUCCEEDED
                        && MaidCommandSeatBridge.isSeatProtected(
                        fixture.maid()
                ),
                "Command seat was not locked after the window"
        );

        fixture.owner().stopRiding();
        fixture.owner().setPos(position(helper, 20, 1));
        MaidSeatAutonomyBridge.leaveSeatForFollow(fixture.maid());
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(
                    fixture.maid().getVehicle() == maidSeat,
                    "Maid automatically left a command seat"
            );
            new MaidInteractionHandler().onNormalInteract(
                    new InteractMaidEvent(
                            fixture.owner(),
                            fixture.maid(),
                            ItemStack.EMPTY
                    )
            );
            helper.assertTrue(
                    !fixture.maid().isPassenger()
                            && !MaidCommandSeatBridge.isSeatProtected(
                            fixture.maid()
                    ),
                    "Owner command did not release the command seat"
            );
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void commandUsesTheOwnersReleasedSeat(
            GameTestHelper helper
    ) {
        OwnedFixture fixture = ownedFixture(helper);
        fixture.maid().setPos(position(helper, 3, 1));
        EntityChair seat = chair(helper, 4, 1);
        fixture.owner().startRiding(seat, true);
        TlmMaidIntentActions actions = actions();

        execute(actions, fixture, 0);
        helper.assertFalse(
                fixture.maid().isPassenger(),
                "Maid found a seat that should not exist"
        );
        fixture.owner().stopRiding();
        execute(actions, fixture, 1);
        helper.assertTrue(
                fixture.maid().getVehicle() == seat,
                "Maid did not occupy the owner's released seat"
        );
        MaidCommandSeatBridge.releaseForDanger(fixture.maid());
        fixture.maid().stopRiding();
        helper.assertFalse(
                MaidCommandSeatBridge.isSeatProtected(fixture.maid()),
                "Danger release retained the command-seat lock"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void commandSharesTheOwnersRideableEntity(
            GameTestHelper helper
    ) {
        OwnedFixture fixture = ownedFixture(helper);
        fixture.maid().setPos(position(helper, 3, 1));
        Vec3 vehiclePosition = position(helper, 4, 1);
        Boat boat = new Boat(
                helper.getLevel(),
                vehiclePosition.x,
                vehiclePosition.y,
                vehiclePosition.z
        );
        helper.getLevel().addFreshEntity(boat);
        fixture.owner().startRiding(boat, true);

        TlmMaidIntentActions actions = actions();
        execute(actions, fixture, 0);
        helper.assertTrue(
                fixture.maid().getVehicle() == boat
                        && boat.hasPassenger(fixture.owner()),
                "Maid did not share the owner's rideable entity"
        );
        fixture.owner().stopRiding();
        execute(actions, fixture, 1);
        helper.assertTrue(
                fixture.maid().getVehicle() == boat
                        && MaidCommandSeatBridge.isSeatProtected(
                        fixture.maid()
                ),
                "Owner dismount automatically released the command vehicle"
        );
        MaidCommandSeatBridge.releaseForDanger(fixture.maid());
        fixture.maid().stopRiding();
        helper.succeed();
    }

    private static ActionResult execute(
            TlmMaidIntentActions actions,
            OwnedFixture fixture,
            int elapsedTicks
    ) {
        return actions.execute(
                fixture.maid(),
                CompanionIntentIds.COMPANION_COMMAND_WINDOW,
                COMMAND_PARAMETERS,
                fixture.maid().level().getGameTime() + elapsedTicks,
                elapsedTicks
        );
    }

    private static void assertTargetsOwner(
            GameTestHelper helper,
            OwnedFixture fixture
    ) {
        helper.assertTrue(
                fixture.maid().getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .map(target -> target.getTarget())
                        .filter(EntityTracker.class::isInstance)
                        .map(EntityTracker.class::cast)
                        .map(EntityTracker::getEntity)
                        .filter(fixture.owner()::equals)
                        .isPresent(),
                "Command window did not keep following the owner"
        );
    }

    private static TlmMaidIntentActions actions() {
        return new TlmMaidIntentActions(maid -> {
        });
    }

    @SuppressWarnings("unchecked")
    private static MaidGazeRecallApi<Player, EntityMaid>
    productionGazeRecall() {
        return (MaidGazeRecallApi<Player, EntityMaid>)
                AdapterRuntime.require(MaidGazeRecallApi.class);
    }

    @SuppressWarnings("unchecked")
    private static MaidIntentApi<EntityMaid> productionIntents() {
        return (MaidIntentApi<EntityMaid>) AdapterRuntime.require(
                MaidIntentApi.class
        );
    }

    private static EntityChair chair(
            GameTestHelper helper,
            int x,
            int z
    ) {
        Vec3 position = position(helper, x, z);
        EntityChair chair = new EntityChair(
                helper.getLevel(),
                position.x,
                position.y,
                position.z,
                0.0F
        );
        helper.getLevel().addFreshEntity(chair);
        return chair;
    }

    private static OwnedFixture ownedFixture(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        Player owner = helper.makeMockPlayer();
        owner.setPos(position(helper, 4, 1));
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(position(helper, 1, 1));
        maid.setTame(true);
        maid.setHomeModeEnable(false);
        maid.setOwnerUUID(owner.getUUID());
        helper.getLevel().addFreshEntity(maid);
        return new OwnedFixture(owner, maid);
    }

    private static Vec3 position(GameTestHelper helper, int x, int z) {
        BlockPos absolute = helper.absolutePos(new BlockPos(x, 2, z));
        return new Vec3(
                absolute.getX() + 0.5D,
                absolute.getY(),
                absolute.getZ() + 0.5D
        );
    }

    private record OwnedFixture(Player owner, EntityMaid maid) {
    }
}
