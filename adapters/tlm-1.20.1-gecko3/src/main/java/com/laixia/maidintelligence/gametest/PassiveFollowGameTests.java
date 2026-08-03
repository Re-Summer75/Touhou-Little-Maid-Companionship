package com.laixia.maidintelligence.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.tlm.ActivityRadiusAccess;
import com.laixia.maidintelligence.feature.ai.tlm.ActivityRadiusBridge;
import com.laixia.maidintelligence.feature.ai.tlm.ActivityRadiusState;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import com.laixia.maidintelligence.feature.ai.tlm.PassiveFollowBridge;
import com.laixia.maidintelligence.gametest.support.GameTestPositions;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class PassiveFollowGameTests {
    private PassiveFollowGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void stationaryOwnerDefersWorkUntilMovement(
            GameTestHelper helper
    ) {
        prepareFloor(helper);
        Player owner = helper.makeMockPlayer();
        owner.setPos(GameTestPositions.center(helper, 16, 2, 1));
        owner.xo = owner.getX();
        owner.zo = owner.getZ();

        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(GameTestPositions.center(helper, 1, 2, 1));
        maid.setTame(true);
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        confirmStationary(maid, owner);
        helper.assertTrue(
                ActivityRadiusBridge.ownerStationary(maid),
                "Passive-follow owner state did not reach stationary"
        );
        helper.assertTrue(
                ActivityRadiusBridge.adaptiveStateAllowed(maid),
                "Passive-follow maid did not pass adaptive-state gates"
        );

        BlockPos workTarget = maid.blockPosition().south();
        writeWorkTarget(maid, workTarget);
        float effectiveRadius = maid.getRestrictRadius();
        helper.assertTrue(
                PassiveFollowBridge.shouldDefer(maid),
                "Passive-follow policy did not defer at radius "
                        + effectiveRadius
        );
        boolean stationaryStarted = followTask().tryStart(
                helper.getLevel(),
                maid,
                helper.getLevel().getGameTime()
        );
        helper.assertFalse(
                stationaryStarted,
                "Stationary owner interrupted protected maid work"
        );
        helper.assertTrue(
                currentTarget(maid).equals(workTarget),
                "Deferred following replaced the work target"
        );

        activityState(maid).ownerMotion().reset();
        owner.setPos(GameTestPositions.center(helper, 8, 2, 1));
        owner.xo = owner.getX() - 1.0D;
        boolean movingStarted = followTask().tryStart(
                helper.getLevel(),
                maid,
                helper.getLevel().getGameTime() + 1L
        );
        helper.assertTrue(
                movingStarted,
                "Moving owner did not resume normal following"
        );
        helper.assertTrue(
                currentTarget(maid).equals(workTarget),
                "Soft owner follow replaced hard built-in work"
        );
        helper.succeed();
    }

    private static MaidFollowOwnerTask followTask() {
        return new MaidFollowOwnerTask(0.5F, 2);
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

    private static void writeWorkTarget(
            EntityMaid maid,
            BlockPos target
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
                MovementIntentSource.BUILT_IN_WORK,
                true
        );
    }

    private static BlockPos currentTarget(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElseThrow()
                .getTarget()
                .currentBlockPosition();
    }

    private static void prepareFloor(GameTestHelper helper) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }
}
