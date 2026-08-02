package com.laixia.maidintelligence.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidPickupEntitiesTask;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.ai.api.MaidMovementCoordinationApi;
import com.laixia.maidintelligence.feature.ai.api.MovementCoordinationMode;
import com.laixia.maidintelligence.feature.ai.api.MovementCoordinationSnapshot;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Objects;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementIntentGameTests {
    private MovementIntentGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void higherPriorityTargetSurvivesLowerWriter(
            GameTestHelper helper
    ) {
        requireConservativeMode(helper);
        EntityMaid maid = spawnMaid(helper);
        BlockPos breathTarget = maid.blockPosition().east();
        BlockPos pickupTarget = maid.blockPosition().south();

        writeKnownTarget(
                maid,
                breathTarget,
                MovementIntentSource.BREATH_AIR
        );
        writeKnownTarget(
                maid,
                pickupTarget,
                MovementIntentSource.PICKUP
        );

        helper.assertTrue(
                currentTarget(maid).equals(breathTarget),
                "Lower-priority pickup replaced the breathing target"
        );
        helper.assertTrue(
                currentLookTarget(maid).equals(pickupTarget),
                "Suppression unexpectedly rolled back LOOK_TARGET"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void pickupTaskMixinClaimsMovementTarget(
            GameTestHelper helper
    ) {
        requireConservativeMode(helper);
        MaidMovementCoordinationApi coordination = coordination();
        coordination.resetMetrics();
        EntityMaid maid = spawnMaid(helper);
        maid.setPickup(true);
        ItemEntity item = new ItemEntity(
                helper.getLevel(),
                maid.getX() + 2.0D,
                maid.getY(),
                maid.getZ(),
                new ItemStack(Items.DIAMOND)
        );
        helper.getLevel().addFreshEntity(item);
        maid.getBrain().setMemory(
                InitEntities.VISIBLE_PICKUP_ENTITIES.get(),
                List.<Entity>of(item)
        );
        long claimsBefore = coordination.snapshot().claims();

        boolean started = new MaidPickupEntitiesTask(
                EntityMaid::isPickup,
                0.6F
        ).tryStart(
                helper.getLevel(),
                maid,
                helper.getLevel().getGameTime()
        );

        helper.assertTrue(started, "Pickup task did not start");
        helper.assertTrue(
                coordination.snapshot().claims() > claimsBefore,
                "Pickup task Mixin did not claim its movement target"
        );
        helper.assertTrue(
                currentTarget(maid).equals(item.blockPosition()),
                "Pickup task did not preserve its selected item target"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void pickupTaskLeavesAutonomousChair(
            GameTestHelper helper
    ) {
        requireConservativeMode(helper);
        EntityMaid maid = spawnMaid(helper);
        maid.setPickup(true);
        EntityChair chair = new EntityChair(
                helper.getLevel(),
                maid.getX(),
                maid.getY(),
                maid.getZ(),
                0.0F
        );
        helper.getLevel().addFreshEntity(chair);
        helper.assertTrue(
                maid.startRiding(chair),
                "Maid could not mount the chair fixture"
        );
        ItemEntity item = spawnPickupItem(helper, maid, Items.IRON_INGOT);

        boolean started = startPickupTask(helper, maid, item);

        helper.assertTrue(started, "Pickup did not resume from a chair");
        helper.assertFalse(
                maid.isPassenger(),
                "Autonomously seated maid stayed trapped on the chair"
        );
        helper.assertFalse(
                maid.isMaidInSittingPose(),
                "Chair dismount was converted into commanded sitting"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void pickupTaskLeavesAutonomousJoySeat(
            GameTestHelper helper
    ) {
        requireConservativeMode(helper);
        EntityMaid maid = spawnMaid(helper);
        maid.setPickup(true);
        EntitySit seat = new EntitySit(
                helper.getLevel(),
                maid.position(),
                "tlm_companionship:seat_autonomy_test",
                maid.blockPosition()
        );
        helper.getLevel().addFreshEntity(seat);
        helper.assertTrue(
                maid.startRiding(seat),
                "Maid could not mount the joy-seat fixture"
        );
        ItemEntity item = spawnPickupItem(helper, maid, Items.GOLD_INGOT);

        boolean started = startPickupTask(helper, maid, item);

        helper.assertTrue(started, "Pickup did not resume from a joy seat");
        helper.assertFalse(
                maid.isPassenger(),
                "Autonomously seated maid stayed trapped on the joy seat"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void pickupTaskPreservesUnrelatedVehicle(
            GameTestHelper helper
    ) {
        requireConservativeMode(helper);
        EntityMaid maid = spawnMaid(helper);
        maid.setPickup(true);
        Boat boat = Objects.requireNonNull(
                EntityType.BOAT.create(helper.getLevel()),
                "Boat fixture was not created"
        );
        boat.setPos(maid.position());
        helper.getLevel().addFreshEntity(boat);
        helper.assertTrue(
                maid.startRiding(boat),
                "Maid could not mount the boat fixture"
        );
        ItemEntity item = spawnPickupItem(helper, maid, Items.COAL);

        boolean started = startPickupTask(helper, maid, item);

        helper.assertFalse(
                started,
                "Pickup bypassed TLM's movement lock on an unrelated vehicle"
        );
        helper.assertTrue(
                maid.getVehicle() == boat,
                "Seat autonomy dismounted an unrelated vehicle"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void pickupTaskPreservesCommandedSitting(
            GameTestHelper helper
    ) {
        requireConservativeMode(helper);
        EntityMaid maid = spawnMaid(helper);
        maid.setPickup(true);
        maid.setInSittingPose(true);
        ItemEntity item = spawnPickupItem(helper, maid, Items.REDSTONE);

        boolean started = startPickupTask(helper, maid, item);

        helper.assertFalse(
                started,
                "Pickup overrode the owner's sitting command"
        );
        helper.assertTrue(
                maid.isMaidInSittingPose() && maid.isOrderedToSit(),
                "Owner-commanded sitting was not preserved"
        );
        helper.assertTrue(
                maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                        .isEmpty(),
                "Commanded sitting accepted a pickup movement target"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void pickupCommitmentDefersNormalFollow(
            GameTestHelper helper
    ) {
        requireConservativeMode(helper);
        EntityMaid maid = spawnMaid(helper);
        maid.setPickup(true);
        ItemEntity item = new ItemEntity(
                helper.getLevel(),
                maid.getX() + 2.0D,
                maid.getY(),
                maid.getZ(),
                new ItemStack(Items.EMERALD)
        );
        helper.getLevel().addFreshEntity(item);
        BlockPos followTarget = maid.blockPosition().north();

        writeKnownEntityTarget(
                maid,
                item,
                MovementIntentSource.PICKUP
        );
        writeKnownTarget(
                maid,
                followTarget,
                MovementIntentSource.FOLLOW_OWNER
        );
        helper.assertTrue(
                currentTarget(maid).equals(item.blockPosition()),
                "Normal following interrupted an active pickup"
        );

        item.discard();
        writeKnownTarget(
                maid,
                followTarget,
                MovementIntentSource.FOLLOW_OWNER
        );
        helper.assertTrue(
                currentTarget(maid).equals(followTarget),
                "Following did not resume after the item became invalid"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void hardMovementStatesRejectKnownWriters(
            GameTestHelper helper
    ) {
        requireConservativeMode(helper);
        EntityMaid maid = spawnMaid(helper);
        BlockPos target = maid.blockPosition().east();

        maid.setInSittingPose(true);
        writeKnownTarget(maid, target, MovementIntentSource.PICKUP);
        helper.assertTrue(
                maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                        .isEmpty(),
                "Sitting maid accepted a known movement target"
        );

        maid.setInSittingPose(false);
        maid.getBrain().setActiveActivityIfPossible(Activity.PANIC);
        helper.assertTrue(
                maid.getBrain().isActive(Activity.PANIC),
                "Panic activity was not available in the test brain"
        );
        writeKnownTarget(maid, target, MovementIntentSource.FOLLOW_OWNER);
        helper.assertTrue(
                maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                        .isEmpty(),
                "Panic activity accepted a normal follow target"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void externalBrainTargetEnablesFailOpen(
            GameTestHelper helper
    ) {
        requireConservativeMode(helper);
        MaidMovementCoordinationApi coordination = coordination();
        coordination.resetMetrics();
        EntityMaid maid = spawnMaid(helper);
        BlockPos managedTarget = maid.blockPosition().east();
        BlockPos externalTarget = maid.blockPosition().south();

        writeKnownTarget(
                maid,
                managedTarget,
                MovementIntentSource.BREATH_AIR
        );
        MovementCoordinationSnapshot before = coordination.snapshot();

        // Represents an addon-owned IMaidTask or IExtraMaidBrain writer: it
        // bypasses every known-source hook and must never be rolled back.
        BehaviorUtils.setWalkAndLookTargetMemories(
                maid,
                externalTarget,
                0.5F,
                0
        );
        MovementCoordinationBridge.reconcile(maid);

        helper.assertTrue(
                currentTarget(maid).equals(externalTarget),
                "External Brain target was rolled back"
        );
        helper.assertTrue(
                coordination.snapshot().failOpenTransitions()
                        > before.failOpenTransitions(),
                "External Brain target did not trigger fail-open"
        );
        helper.succeed();
    }

    private static void writeKnownTarget(
            EntityMaid maid,
            BlockPos target,
            MovementIntentSource source
    ) {
        WalkTarget previous = MovementCoordinationBridge.capture(maid);
        BehaviorUtils.setWalkAndLookTargetMemories(
                maid,
                target,
                0.5F,
                0
        );
        MovementCoordinationBridge.finishKnownWrite(
                maid,
                previous,
                source,
                true
        );
    }

    private static void writeKnownEntityTarget(
            EntityMaid maid,
            Entity target,
            MovementIntentSource source
    ) {
        WalkTarget previous = MovementCoordinationBridge.capture(maid);
        BehaviorUtils.setWalkAndLookTargetMemories(
                maid,
                target,
                0.6F,
                0
        );
        MovementCoordinationBridge.finishKnownWrite(
                maid,
                previous,
                source,
                true
        );
    }

    private static ItemEntity spawnPickupItem(
            GameTestHelper helper,
            EntityMaid maid,
            Item item
    ) {
        ItemEntity itemEntity = new ItemEntity(
                helper.getLevel(),
                maid.getX() + 2.0D,
                maid.getY(),
                maid.getZ(),
                new ItemStack(item)
        );
        helper.getLevel().addFreshEntity(itemEntity);
        return itemEntity;
    }

    private static boolean startPickupTask(
            GameTestHelper helper,
            EntityMaid maid,
            ItemEntity item
    ) {
        maid.getBrain().setMemory(
                InitEntities.VISIBLE_PICKUP_ENTITIES.get(),
                List.<Entity>of(item)
        );
        return new MaidPickupEntitiesTask(
                EntityMaid::isPickup,
                0.6F
        ).tryStart(
                helper.getLevel(),
                maid,
                helper.getLevel().getGameTime()
        );
    }

    private static BlockPos currentTarget(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElseThrow()
                .getTarget()
                .currentBlockPosition();
    }

    private static BlockPos currentLookTarget(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.LOOK_TARGET)
                .orElseThrow()
                .currentBlockPosition();
    }

    private static EntityMaid spawnMaid(GameTestHelper helper) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        EntityMaid maid = helper.spawn(
                InitEntities.MAID.get(),
                new BlockPos(1, 2, 1)
        );
        maid.setTame(true);
        return maid;
    }

    private static void requireConservativeMode(GameTestHelper helper) {
        helper.assertTrue(
                coordination().mode()
                        == MovementCoordinationMode.CONSERVATIVE,
                "Movement coordination must use CONSERVATIVE mode"
        );
    }

    private static MaidMovementCoordinationApi coordination() {
        return AdapterRuntime.require(MaidMovementCoordinationApi.class);
    }
}
