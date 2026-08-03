package com.laixia.maidintelligence.gametest.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancyLevel;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancyReason;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import com.laixia.maidintelligence.feature.ai.tlm.NativeBehaviorArbitrationBridge;
import com.laixia.maidintelligence.feature.ai.tlm.TlmBehaviorOccupancyClassifier;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentActions;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class NativeBehaviorArbitrationGameTests {
    private static final Map<String, String> OWNER_COMMAND_APPROACH = Map.of(
            "speed", "0.6",
            "close_distance", "2",
            "authority", "owner_command"
    );

    private NativeBehaviorArbitrationGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void ownerCommandPreemptsIdleLeisureSeat(
            GameTestHelper helper
    ) {
        OwnedFixture fixture = ownedFixture(helper);
        fixture.maid().setPos(position(helper, 3, 1));
        fixture.owner().setPos(position(helper, 6, 1));
        EntitySit seat = leisureSeat(helper, fixture.maid());
        helper.assertTrue(
                fixture.maid().startRiding(seat),
                "Maid could not sit on the leisure seat"
        );
        helper.assertTrue(
                TlmBehaviorOccupancyClassifier.snapshot(
                        fixture.maid(),
                        helper.getLevel().getGameTime()
                ).level() == BehaviorOccupancyLevel.SOFT,
                "Leisure seat was not classified as soft occupancy"
        );

        ActionResult result = actions().execute(
                fixture.maid(),
                CompanionIntentIds.APPROACH_OWNER,
                OWNER_COMMAND_APPROACH,
                helper.getLevel().getGameTime(),
                0
        );
        helper.assertTrue(
                result == ActionResult.RUNNING
                        || result == ActionResult.SUCCEEDED,
                "Owner command failed to preempt leisure"
        );
        helper.assertTrue(
                !fixture.maid().isPassenger(),
                "Owner command left the maid on the leisure seat"
        );
        helper.assertTrue(
                NativeBehaviorArbitrationBridge.ownerCommandActive(
                        fixture.maid(),
                        helper.getLevel().getGameTime()
                ),
                "Owner command override was not acquired"
        );
        helper.assertTrue(
                looksOrWalksToOwner(fixture),
                "Owner command did not aim at the owner"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void hardPickupBlocksOwnerCommandApproach(
            GameTestHelper helper
    ) {
        OwnedFixture fixture = ownedFixture(helper);
        long gameTime = helper.getLevel().getGameTime();
        BlockPos pickupTarget = fixture.maid().blockPosition().east(2);
        WalkTarget previous = MovementCoordinationBridge.capture(
                fixture.maid(),
                gameTime
        );
        BehaviorUtils.setWalkAndLookTargetMemories(
                fixture.maid(),
                pickupTarget,
                0.5F,
                0
        );
        MovementCoordinationBridge.finishKnownWrite(
                fixture.maid(),
                previous,
                MovementIntentSource.PICKUP,
                true,
                gameTime
        );
        helper.assertTrue(
                TlmBehaviorOccupancyClassifier.snapshot(
                        fixture.maid(),
                        gameTime
                ).reason() == BehaviorOccupancyReason.PICKUP,
                "Pickup lease was not classified as hard occupancy"
        );

        ActionResult result = actions().execute(
                fixture.maid(),
                CompanionIntentIds.APPROACH_OWNER,
                OWNER_COMMAND_APPROACH,
                gameTime,
                0
        );
        helper.assertTrue(
                result == ActionResult.FAILED,
                "Owner command approached through a pickup commitment"
        );
        helper.assertFalse(
                NativeBehaviorArbitrationBridge.ownerCommandActive(
                        fixture.maid(),
                        gameTime
                ),
                "Failed owner command retained its override lease"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void ownerCommandWindowKeepsSoftBehaviorsSuppressed(
            GameTestHelper helper
    ) {
        OwnedFixture fixture = ownedFixture(helper);
        fixture.maid().setPos(position(helper, 3, 1));
        fixture.owner().setPos(position(helper, 5, 1));
        TlmMaidIntentActions intentActions = actions();
        long gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(
                intentActions.execute(
                        fixture.maid(),
                        CompanionIntentIds.APPROACH_OWNER,
                        OWNER_COMMAND_APPROACH,
                        gameTime,
                        0
                ) != ActionResult.FAILED,
                "Owner command approach failed"
        );
        ActionResult window = intentActions.execute(
                fixture.maid(),
                CompanionIntentIds.COMPANION_COMMAND_WINDOW,
                Map.of(
                        "duration_ticks", "60",
                        "speed", "0.6",
                        "close_distance", "2"
                ),
                gameTime + 1L,
                1
        );
        helper.assertTrue(
                window == ActionResult.RUNNING,
                "Command window did not stay active"
        );
        helper.assertTrue(
                NativeBehaviorArbitrationBridge.ownerCommandActive(
                        fixture.maid(),
                        gameTime + 1L
                ),
                "Command window dropped the owner-command override"
        );
        helper.assertTrue(
                fixture.maid().getBrain()
                        .getMemory(MemoryModuleType.LOOK_TARGET)
                        .map(target -> target instanceof EntityTracker tracker
                                && tracker.getEntity() == fixture.owner())
                        .orElse(false),
                "Command window look target was not the owner"
        );
        helper.succeed();
    }

    private static boolean looksOrWalksToOwner(OwnedFixture fixture) {
        boolean looking = fixture.maid().getBrain()
                .getMemory(MemoryModuleType.LOOK_TARGET)
                .map(target -> target instanceof EntityTracker tracker
                        && tracker.getEntity() == fixture.owner())
                .orElse(false);
        boolean walking = fixture.maid().getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(target -> target.getTarget())
                .filter(EntityTracker.class::isInstance)
                .map(EntityTracker.class::cast)
                .map(EntityTracker::getEntity)
                .filter(fixture.owner()::equals)
                .isPresent();
        return looking || walking;
    }

    private static EntitySit leisureSeat(
            GameTestHelper helper,
            EntityMaid maid
    ) {
        EntitySit seat = new EntitySit(
                helper.getLevel(),
                maid.position(),
                "computer",
                maid.blockPosition()
        );
        helper.getLevel().addFreshEntity(seat);
        return seat;
    }

    private static TlmMaidIntentActions actions() {
        return new TlmMaidIntentActions(maid -> {
        });
    }

    private static OwnedFixture ownedFixture(GameTestHelper helper) {
        fillFloor(helper, 0, 7, 0, 3);
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

    private static void fillFloor(
            GameTestHelper helper,
            int minX,
            int maxX,
            int minZ,
            int maxZ
    ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
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
