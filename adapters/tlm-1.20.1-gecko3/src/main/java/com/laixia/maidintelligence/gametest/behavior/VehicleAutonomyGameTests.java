package com.laixia.maidintelligence.gametest.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskAttack;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.ai.tlm.ActivityRadiusBridge;
import com.laixia.maidintelligence.feature.ai.tlm.MaidSeatAutonomyBridge;
import com.laixia.maidintelligence.feature.behavior.tlm.MaidCommandSeatBridge;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class VehicleAutonomyGameTests {
    private VehicleAutonomyGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void ordinaryBoatReleasesWhenOwnerLeaves(
            GameTestHelper helper
    ) {
        BoatFixture fixture = boatFixture(helper, null, true);
        fixture.owner().stopRiding();

        MaidSeatAutonomyBridge.leaveOrdinaryBoatWhenOwnerLeaves(
                fixture.maid()
        );
        helper.assertFalse(
                fixture.maid().isPassenger(),
                "Ordinary boat retained the maid after the owner left"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void unrelatedVehicleRemainsProtectedForPickup(
            GameTestHelper helper
    ) {
        prepareFloor(helper);
        Player owner = helper.makeMockPlayer();
        owner.setPos(position(helper, 2, 1));
        EntityMaid maid = ordinaryMaid(helper, owner);
        maid.setPos(position(helper, 2, 2));
        maid.setPickup(true);
        Vec3 position = position(helper, 2, 2);
        Minecart minecart = new Minecart(
                helper.getLevel(),
                position.x,
                position.y,
                position.z
        );
        helper.getLevel().addFreshEntity(minecart);
        helper.assertTrue(
                maid.startRiding(minecart),
                "Maid could not mount the unrelated vehicle fixture"
        );
        ItemEntity item = new ItemEntity(
                helper.getLevel(),
                position.x + 1.0D,
                position.y,
                position.z,
                Items.COAL.getDefaultInstance()
        );
        helper.getLevel().addFreshEntity(item);
        maid.getBrain().setMemory(
                InitEntities.VISIBLE_PICKUP_ENTITIES.get(),
                List.of(item)
        );

        MaidSeatAutonomyBridge.leaveSeatForPickup(maid);
        helper.assertTrue(
                maid.getVehicle() == minecart,
                "Boat autonomy leaked into an unrelated vehicle"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void ordinaryBoatReleasesForCombat(
            GameTestHelper helper
    ) {
        TaskAttack attackTask = new TaskAttack();
        BoatFixture fixture = boatFixture(helper, attackTask, true);
        fixture.maid().setItemInHand(
                InteractionHand.MAIN_HAND,
                Items.DIAMOND_SWORD.getDefaultInstance()
        );
        fixture.maid().getBrain().setActiveActivityIfPossible(Activity.WORK);
        Zombie zombie = new Zombie(helper.getLevel()) {
            @Override
            protected boolean shouldDespawnInPeaceful() {
                return false;
            }
        };
        zombie.setPos(
                fixture.maid().getX() + 2.0D,
                fixture.maid().getY(),
                fixture.maid().getZ()
        );
        zombie.setNoAi(true);
        helper.getLevel().addFreshEntity(zombie);
        fixture.maid().getBrain().setMemory(
                MemoryModuleType.ATTACK_TARGET,
                zombie
        );
        helper.assertTrue(
                attackTask.canAttack(fixture.maid(), zombie)
                        && ActivityRadiusBridge.isBuiltInCombatTask(
                        fixture.maid()
                ),
                "Boat combat fixture was not a valid threat"
        );

        MaidSeatAutonomyBridge.leaveSeatForCombat(fixture.maid());
        helper.assertFalse(
                fixture.maid().isPassenger(),
                "Combat danger left the maid trapped in an ordinary boat"
        );
        helper.assertTrue(
                fixture.maid().getBrain()
                        .getMemory(MemoryModuleType.ATTACK_TARGET)
                        .filter(zombie::equals)
                        .isPresent(),
                "Boat dismount discarded the combat target"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void commandBoatRemainsLockedWhenOwnerLeaves(
            GameTestHelper helper
    ) {
        BoatFixture fixture = boatFixture(helper, null, false);
        helper.assertTrue(
                MaidCommandSeatBridge.mirrorOwnerSeat(
                        fixture.maid(),
                        fixture.owner()
                ),
                "Command fixture did not mount the maid"
        );
        fixture.owner().stopRiding();

        MaidSeatAutonomyBridge.leaveOrdinaryBoatWhenOwnerLeaves(
                fixture.maid()
        );
        helper.assertTrue(
                fixture.maid().getVehicle() == fixture.boat()
                        && MaidCommandSeatBridge.isSeatProtected(
                        fixture.maid()
                ),
                "Owner departure released a commanded boat"
        );
        MaidCommandSeatBridge.releaseForDanger(fixture.maid());
        fixture.maid().stopRiding();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void commandUsesNearbyPlayerCompatibleSeat(
            GameTestHelper helper
    ) {
        prepareFloor(helper);
        Player owner = helper.makeMockPlayer();
        owner.setPos(position(helper, 2, 1));
        EntityMaid maid = ordinaryMaid(helper, owner);
        maid.setPos(position(helper, 2, 2));
        PlayerOnlySeat ownerSeat = playerOnlySeat(helper, 2, 1);
        PlayerOnlySeat maidSeat = playerOnlySeat(helper, 2, 3);
        helper.assertTrue(
                owner.startRiding(ownerSeat),
                "Owner could not mount the player-compatible seat"
        );
        helper.assertFalse(
                maid.startRiding(maidSeat),
                "Player-only fixture unexpectedly accepted the maid"
        );

        helper.assertTrue(
                MaidCommandSeatBridge.mirrorOwnerSeat(maid, owner)
                        && maid.getVehicle() == maidSeat
                        && MaidCommandSeatBridge.isSeatProtected(maid),
                "Command did not use a nearby player-compatible seat"
        );
        MaidCommandSeatBridge.releaseForDanger(maid);
        maid.stopRiding();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void commandUsesReleasedPlayerCompatibleSeat(
            GameTestHelper helper
    ) {
        prepareFloor(helper);
        Player owner = helper.makeMockPlayer();
        owner.setPos(position(helper, 2, 1));
        EntityMaid maid = ordinaryMaid(helper, owner);
        maid.setPos(position(helper, 2, 2));
        PlayerOnlySeat seat = playerOnlySeat(helper, 2, 1);
        helper.assertTrue(
                owner.startRiding(seat),
                "Owner could not mount the released-seat fixture"
        );
        helper.assertFalse(
                MaidCommandSeatBridge.mirrorOwnerSeat(maid, owner),
                "Maid occupied a full player-only seat"
        );

        owner.stopRiding();
        helper.assertTrue(
                MaidCommandSeatBridge.mirrorOwnerSeat(maid, owner)
                        && maid.getVehicle() == seat
                        && MaidCommandSeatBridge.isSeatProtected(maid),
                "Command did not occupy the player's released seat"
        );
        MaidCommandSeatBridge.releaseForDanger(maid);
        maid.stopRiding();
        helper.succeed();
    }

    private static BoatFixture boatFixture(
            GameTestHelper helper,
            TaskAttack attackTask,
            boolean boardMaid
    ) {
        prepareFloor(helper);
        Player owner = helper.makeMockPlayer();
        owner.setPos(position(helper, 2, 1));
        EntityMaid maid = attackTask == null
                ? ordinaryMaid(helper, owner)
                : combatMaid(helper, owner, attackTask);
        maid.setPos(position(helper, 2, 2));

        Vec3 boatPosition = position(helper, 2, 1);
        Boat boat = new Boat(
                helper.getLevel(),
                boatPosition.x,
                boatPosition.y,
                boatPosition.z
        );
        helper.getLevel().addFreshEntity(boat);
        helper.assertTrue(
                owner.startRiding(boat, true),
                "Owner could not mount the boat fixture"
        );
        if (boardMaid) {
            helper.assertTrue(
                    maid.startRiding(boat),
                    "Maid could not mount the boat fixture"
            );
        }
        return new BoatFixture(owner, maid, boat);
    }

    private static EntityMaid ordinaryMaid(
            GameTestHelper helper,
            Player owner
    ) {
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        initializeMaid(helper, owner, maid);
        return maid;
    }

    private static EntityMaid combatMaid(
            GameTestHelper helper,
            Player owner,
            TaskAttack attackTask
    ) {
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }

            @Override
            public TaskAttack getTask() {
                return attackTask;
            }
        };
        initializeMaid(helper, owner, maid);
        return maid;
    }

    private static void initializeMaid(
            GameTestHelper helper,
            Player owner,
            EntityMaid maid
    ) {
        maid.setTame(true);
        maid.setHomeModeEnable(false);
        maid.setOwnerUUID(owner.getUUID());
        helper.getLevel().addFreshEntity(maid);
    }

    private static void prepareFloor(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    private static PlayerOnlySeat playerOnlySeat(
            GameTestHelper helper,
            int x,
            int z
    ) {
        Vec3 position = position(helper, x, z);
        PlayerOnlySeat seat = new PlayerOnlySeat(
                helper.getLevel(),
                position
        );
        helper.getLevel().addFreshEntity(seat);
        return seat;
    }

    private static Vec3 position(GameTestHelper helper, int x, int z) {
        BlockPos absolute = helper.absolutePos(new BlockPos(x, 2, z));
        return new Vec3(
                absolute.getX() + 0.5D,
                absolute.getY(),
                absolute.getZ() + 0.5D
        );
    }

    private record BoatFixture(
            Player owner,
            EntityMaid maid,
            Boat boat
    ) {
    }

    private static final class PlayerOnlySeat extends ArmorStand {
        private PlayerOnlySeat(Level level, Vec3 position) {
            super(level, position.x, position.y, position.z);
        }

        @Override
        protected boolean canAddPassenger(Entity passenger) {
            return passenger instanceof Player
                    && super.canAddPassenger(passenger);
        }
    }
}
