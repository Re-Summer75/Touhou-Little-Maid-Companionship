package com.laixia.maidintelligence.gametest.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskAttack;
import com.laixia.maidintelligence.feature.ai.tlm.ActivityRadiusAccess;
import com.laixia.maidintelligence.feature.ai.tlm.ActivityRadiusBridge;
import com.laixia.maidintelligence.feature.ai.tlm.ActivityRadiusState;
import com.laixia.maidintelligence.feature.ai.tlm.CombatReactionBridge;
import com.laixia.maidintelligence.gametest.support.GameTestPositions;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class AdaptiveAiGameTests {
    private AdaptiveAiGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void stationaryOwnerExpandsOnlyTransientRadius(
            GameTestHelper helper
    ) {
        prepareFloor(helper, 6);
        Player owner = stationaryOwner(helper);
        EntityMaid maid = idleMaid(helper, owner);
        float baseRadius = maid.getRestrictRadius();

        confirmStationary(maid, owner);
        helper.assertTrue(
                ActivityRadiusBridge.ownerStationary(maid),
                "Owner motion state did not reach stationary"
        );
        helper.assertTrue(
                ActivityRadiusBridge.adaptiveStateAllowed(maid),
                "Idle maid did not pass adaptive-state gates"
        );
        float expandedRadius = maid.getRestrictRadius();
        helper.assertTrue(
                expandedRadius == Math.min(baseRadius + 4.0F, 24.0F),
                "Stationary idle radius was " + expandedRadius
                        + " from base " + baseRadius
        );

        maid.setHomeModeEnable(true);
        helper.assertTrue(
                maid.getRestrictRadius() == baseRadius,
                "Home mode inherited the transient follow radius"
        );
        maid.setHomeModeEnable(false);
        activityState(maid).ownerMotion().reset();
        owner.xo = owner.getX() - 1.0D;
        helper.assertTrue(
                maid.getRestrictRadius() == baseRadius,
                "Moving owner retained the expanded radius"
        );

        owner.xo = owner.getX();
        activityState(maid).ownerMotion().reset();
        confirmStationary(maid, owner);
        maid.setInSittingPose(true);
        helper.assertTrue(
                maid.getRestrictRadius() == baseRadius,
                "Owner-commanded sitting inherited the expanded radius"
        );
        maid.setInSittingPose(false);
        var boat = helper.spawn(
                EntityType.BOAT,
                new BlockPos(2, 2, 1)
        );
        boat.setPos(maid.getX() + 1.0D, maid.getY(), maid.getZ());
        maid.startRiding(boat, true);
        helper.assertTrue(
                maid.getRestrictRadius() == baseRadius,
                "Non-TLM vehicle inherited the expanded radius"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void recentThreatUsesExpandedCombatRange(
            GameTestHelper helper
    ) {
        prepareFloor(helper, 16);
        Player owner = stationaryOwner(helper);
        TaskAttack attackTask = new TaskAttack();
        EntityMaid maid = combatMaid(helper, owner, attackTask);
        Zombie zombie = helper.spawn(
                EntityType.ZOMBIE,
                new BlockPos(13, 2, 1)
        );
        zombie.setPos(
                maid.getX() + 12.0D,
                maid.getY(),
                maid.getZ()
        );
        confirmStationary(maid, owner);
        maid.setLastHurtByMob(zombie);
        helper.assertTrue(
                ActivityRadiusBridge.ownerStationary(maid),
                "Combat owner motion state did not reach stationary"
        );
        helper.assertTrue(
                ActivityRadiusBridge.adaptiveStateAllowed(maid),
                "Combat maid did not pass adaptive-state gates"
        );
        helper.assertTrue(
                attackTask.canAttack(maid, zombie),
                "TLM attack policy rejected the zombie fixture"
        );
        float combatRadius = maid.getRestrictRadius();
        helper.assertTrue(
                combatRadius >= 12.0F,
                "Combat radius remained " + combatRadius
        );

        boolean active = CombatReactionBridge.beginAiStep(maid);

        helper.assertTrue(active, "Built-in combat reaction was not active");
        helper.assertTrue(
                maid.getBrain()
                        .getMemory(MemoryModuleType.ATTACK_TARGET)
                        .filter(zombie::equals)
                        .isPresent(),
                "Recent attacker inside expanded range was not acquired"
        );
        helper.assertTrue(
                maid.getRestrictRadius() > 8.0F,
                "Combat fixture did not receive its transient radius"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void proactiveThreatIsAcquiredWithinTenTicks(
            GameTestHelper helper
    ) {
        prepareFloor(helper, 8);
        Player owner = stationaryOwner(helper);
        TaskAttack attackTask = new TaskAttack();
        EntityMaid maid = combatMaidDueForStationaryScan(
                helper,
                owner,
                attackTask
        );
        Zombie zombie = helper.spawn(
                EntityType.ZOMBIE,
                new BlockPos(5, 2, 1)
        );
        zombie.setPos(
                maid.getX() + 4.0D,
                maid.getY(),
                maid.getZ()
        );
        zombie.setNoAi(true);
        helper.assertTrue(
                attackTask.canAttack(maid, zombie),
                "TLM attack policy rejected the proactive fixture"
        );
        confirmStationary(maid, owner);
        helper.assertTrue(
                maid.hasLineOfSight(zombie),
                "Proactive fixture did not have line of sight"
        );
        helper.assertTrue(
                CombatReactionBridge.beginAiStep(maid),
                "Combat reaction was not active during proactive scan"
        );
        helper.assertTrue(
                maid.getBrain()
                        .getMemory(MemoryModuleType.ATTACK_TARGET)
                        .filter(zombie::equals)
                        .isPresent(),
                "Due proactive scan did not acquire the hostile"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void nearbyHostileReleasesPassiveChair(
            GameTestHelper helper
    ) {
        prepareFloor(helper, 8);
        Player owner = stationaryOwner(helper);
        TaskAttack attackTask = new TaskAttack();
        EntityMaid maid = combatMaidDueForStationaryScan(
                helper,
                owner,
                attackTask
        );
        BlockPos absoluteMaidPos = helper.absolutePos(
                new BlockPos(1, 2, 1)
        );
        maid.setPos(
                absoluteMaidPos.getX() + 0.5D,
                absoluteMaidPos.getY(),
                absoluteMaidPos.getZ() + 0.5D
        );
        owner.setPos(maid.position());
        owner.xo = owner.getX();
        owner.yo = owner.getY();
        owner.zo = owner.getZ();
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
                "Combat maid could not mount the chair fixture"
        );
        Zombie zombie = new Zombie(helper.getLevel()) {
            @Override
            protected boolean shouldDespawnInPeaceful() {
                return false;
            }
        };
        zombie.setPos(
                maid.getX() + 3.0D,
                maid.getY(),
                maid.getZ()
        );
        zombie.setNoAi(true);
        helper.getLevel().addFreshEntity(zombie);
        helper.assertTrue(
                attackTask.canAttack(maid, zombie)
                        && maid.hasLineOfSight(zombie)
                        && maid.isWithinRestriction(
                                zombie.blockPosition()
                        )
                        && ActivityRadiusBridge.isBuiltInCombatTask(maid),
                "Chair combat fixture was not a valid visible threat"
        );

        helper.succeedWhen(() -> {
            helper.assertFalse(
                    maid.isPassenger(),
                    "Nearby hostile left the combat maid seated"
            );
            helper.assertTrue(
                    maid.getBrain()
                            .getMemory(MemoryModuleType.ATTACK_TARGET)
                            .filter(zombie::equals)
                            .isPresent(),
                    "Chair dismount did not retain the combat target"
            );
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void addonCombatTaskRemainsUnmanaged(
            GameTestHelper helper
    ) {
        prepareFloor(helper, 6);
        Player owner = stationaryOwner(helper);
        TaskAttack addonTask = new TaskAttack() {
            @Override
            public ResourceLocation getUid() {
                return ResourceLocation.fromNamespaceAndPath(
                        "adaptive_test",
                        "attack"
                );
            }
        };
        EntityMaid maid = combatMaid(helper, owner, addonTask);
        Zombie zombie = helper.spawn(
                EntityType.ZOMBIE,
                new BlockPos(3, 2, 1)
        );
        zombie.setPos(
                maid.getX() + 2.0D,
                maid.getY(),
                maid.getZ()
        );
        maid.setLastHurtByMob(zombie);

        boolean active = CombatReactionBridge.beginAiStep(maid);

        helper.assertFalse(
                active,
                "Addon-owned combat task entered built-in optimization"
        );
        helper.assertTrue(
                maid.getBrain()
                        .getMemory(MemoryModuleType.ATTACK_TARGET)
                        .isEmpty(),
                "Addon-owned task received a managed combat target"
        );
        helper.succeed();
    }

    private static EntityMaid idleMaid(
            GameTestHelper helper,
            Player owner
    ) {
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        initializeMaid(helper, maid);
        maid.getBrain().setActiveActivityIfPossible(
                net.minecraft.world.entity.schedule.Activity.IDLE
        );
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
        initializeMaid(helper, maid);
        maid.setItemInHand(
                net.minecraft.world.InteractionHand.MAIN_HAND,
                Items.DIAMOND_SWORD.getDefaultInstance()
        );
        return maid;
    }

    private static EntityMaid combatMaidDueForStationaryScan(
            GameTestHelper helper,
            Player owner,
            TaskAttack attackTask
    ) {
        long gameTime = helper.getLevel().getGameTime();
        for (int attempt = 0; attempt < 10; attempt++) {
            EntityMaid maid = combatMaid(helper, owner, attackTask);
            if (Math.floorMod(gameTime + maid.getId(), 10L) == 0L) {
                return maid;
            }
            maid.discard();
        }
        throw new AssertionError("Could not create a staggered scan fixture");
    }

    private static void initializeMaid(
            GameTestHelper helper,
            EntityMaid maid
    ) {
        maid.setPos(GameTestPositions.center(helper, 1, 2, 1));
        maid.setTame(true);
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        maid.getBrain().setActiveActivityIfPossible(
                net.minecraft.world.entity.schedule.Activity.WORK
        );
    }

    private static Player stationaryOwner(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer();
        owner.setPos(GameTestPositions.center(helper, 1, 2, 1));
        owner.xo = owner.getX();
        owner.yo = owner.getY();
        owner.zo = owner.getZ();
        return owner;
    }

    private static void confirmStationary(
            EntityMaid maid,
            Player owner
    ) {
        long gameTime = maid.level().getGameTime();
        long ownerIdentity = owner.getUUID().getMostSignificantBits()
                ^ owner.getUUID().getLeastSignificantBits();
        for (long tick = gameTime - 19L; tick <= gameTime; tick++) {
            activityState(maid).ownerMotion().observe(
                    tick,
                    ownerIdentity,
                    false,
                    20,
                    3
            );
        }
    }

    private static ActivityRadiusState activityState(EntityMaid maid) {
        return ((ActivityRadiusAccess) maid)
                .maidIntelligence$activityRadiusState();
    }

    private static void prepareFloor(
            GameTestHelper helper,
            int maximumX
    ) {
        for (int x = 0; x <= maximumX; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }
}
