package com.laixia.maidintelligence.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.domain.MovementTargetKind;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationAccess;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.gametest.support.GameTestPositions;
import com.laixia.maidintelligence.gametest.support.IntentGameTestRuntime;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class IntentOrchestrationGameTests {
    private IntentOrchestrationGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void combatCancelsCompanionMovement(
            GameTestHelper helper
    ) {
        Fixture fixture = fixture(helper, 1, 5);
        IntentGameTestRuntime.Runtime runtime = gazeRuntime();
        long gameTime = helper.getLevel().getGameTime();
        runtime.intents().signal(
                fixture.maid(),
                CompanionIntentIds.GAZE_RECALL,
                gameTime,
                20
        );
        runtime.tick(fixture.maid(), gameTime);
        assertTargetsOwner(helper, fixture);

        Zombie zombie = helper.spawn(
                EntityType.ZOMBIE,
                new BlockPos(3, 2, 2)
        );
        zombie.setInvulnerable(true);
        fixture.maid().getBrain().setMemory(
                MemoryModuleType.ATTACK_TARGET,
                zombie
        );
        runtime.tick(fixture.maid(), gameTime + 1L);
        helper.assertTrue(
                fixture.maid().getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .isEmpty(),
                "Combat did not cancel owned companion movement"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void cancellationPreservesReplacementOwnerTarget(
            GameTestHelper helper
    ) {
        Fixture fixture = fixture(helper, 1, 5);
        IntentGameTestRuntime.Runtime runtime = gazeRuntime();
        long gameTime = helper.getLevel().getGameTime();
        runtime.intents().signal(
                fixture.maid(),
                CompanionIntentIds.GAZE_RECALL,
                gameTime,
                20
        );
        runtime.tick(fixture.maid(), gameTime);
        assertTargetsOwner(helper, fixture);

        WalkTarget replacement = new WalkTarget(
                new EntityTracker(fixture.owner(), false),
                0.4F,
                1
        );
        fixture.maid().getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                replacement
        );
        Zombie zombie = helper.spawn(
                EntityType.ZOMBIE,
                new BlockPos(3, 2, 2)
        );
        zombie.setInvulnerable(true);
        fixture.maid().getBrain().setMemory(
                MemoryModuleType.ATTACK_TARGET,
                zombie
        );

        runtime.tick(fixture.maid(), gameTime + 1L);
        helper.assertTrue(
                fixture.maid().getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .orElse(null) == replacement,
                "Intent cancellation erased a replacement owner target"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void pickupLeaseBlocksCompanionIntent(
            GameTestHelper helper
    ) {
        Fixture fixture = fixture(helper, 1, 5);
        IntentGameTestRuntime.Runtime runtime = gazeRuntime();
        long gameTime = helper.getLevel().getGameTime();
        ((MovementCoordinationAccess) fixture.maid())
                .maidIntelligence$movementIntentLease()
                .claim(
                        gameTime,
                        MovementIntentSource.PICKUP,
                        MovementTargetKind.BLOCK,
                        42L,
                        20,
                        true
                );
        runtime.intents().signal(
                fixture.maid(),
                CompanionIntentIds.GAZE_RECALL,
                gameTime,
                20
        );

        helper.assertFalse(
                runtime.tick(fixture.maid(), gameTime),
                "Companion intent preempted a pickup lease"
        );
        helper.assertTrue(
                fixture.maid().getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .isEmpty(),
                "Blocked companion intent wrote movement"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void signalsRemainIsolatedPerMaid(
            GameTestHelper helper
    ) {
        Fixture first = fixture(helper, 1, 5);
        Fixture second = fixture(helper, 1, 6);
        IntentGameTestRuntime.Runtime runtime = gazeRuntime();
        long gameTime = helper.getLevel().getGameTime();
        runtime.intents().signal(
                first.maid(),
                CompanionIntentIds.GAZE_RECALL,
                gameTime,
                20
        );

        runtime.tick(first.maid(), gameTime);
        runtime.tick(second.maid(), gameTime);
        assertTargetsOwner(helper, first);
        helper.assertTrue(
                second.maid().getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .isEmpty(),
                "One maid's signal leaked into another maid's runtime"
        );
        helper.succeed();
    }

    private static IntentGameTestRuntime.Runtime gazeRuntime() {
        return IntentGameTestRuntime.create(
                status(),
                maid -> {
                },
                IntentGameTestRuntime.gazeIntent()
        );
    }

    private static Fixture fixture(
            GameTestHelper helper,
            int maidX,
            int ownerX
    ) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        Player owner = helper.makeMockPlayer();
        owner.setPos(GameTestPositions.center(helper, ownerX, 2, 1));
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(GameTestPositions.center(helper, maidX, 2, 1));
        maid.setTame(true);
        maid.setHomeModeEnable(false);
        maid.setFavorability(64);
        helper.getLevel().addFreshEntity(maid);
        return new Fixture(owner, maid);
    }

    private static void assertTargetsOwner(
            GameTestHelper helper,
            Fixture fixture
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
                "Companion intent did not target its owner"
        );
    }

    @SuppressWarnings("unchecked")
    private static MaidStatusApi<EntityMaid> status() {
        return (MaidStatusApi<EntityMaid>) AdapterRuntime.require(
                MaidStatusApi.class
        );
    }

    private record Fixture(Player owner, EntityMaid maid) {
    }
}
