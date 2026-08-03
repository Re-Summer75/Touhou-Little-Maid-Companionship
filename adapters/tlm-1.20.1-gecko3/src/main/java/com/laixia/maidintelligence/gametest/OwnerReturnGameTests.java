package com.laixia.maidintelligence.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskAttack;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.gametest.support.GameTestPositions;
import com.laixia.maidintelligence.gametest.support.IntentGameTestRuntime;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class OwnerReturnGameTests {
    private OwnerReturnGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void completedWorkReturnsToOwnerOnce(
            GameTestHelper helper
    ) {
        Fixture fixture = fixture(helper);
        IntentGameTestRuntime.Runtime runtime =
                IntentGameTestRuntime.create(
                        status(),
                        maid -> {
                        },
                        IntentGameTestRuntime.postTaskIntent()
                );
        long gameTime = helper.getLevel().getGameTime();
        fixture.maid().getBrain().setMemory(
                InitEntities.TARGET_POS.get(),
                new BlockPosTracker(fixture.maid().blockPosition().east(2))
        );
        helper.assertFalse(
                runtime.tick(fixture.maid(), gameTime),
                "Active work target triggered an early return"
        );

        fixture.maid().getBrain().eraseMemory(
                InitEntities.TARGET_POS.get()
        );
        helper.assertFalse(
                runtime.tick(fixture.maid(), gameTime + 1L),
                "Post-task settle guard was bypassed"
        );
        helper.assertTrue(
                runtime.tick(fixture.maid(), gameTime + 21L),
                "Released work target did not trigger owner return"
        );
        assertWalkTargetOwner(helper, fixture);

        fixture.maid().getBrain().eraseMemory(
                MemoryModuleType.WALK_TARGET
        );
        helper.assertTrue(
                runtime.tick(fixture.maid(), gameTime + 22L),
                "Running return plan stopped after its signal was consumed"
        );
        assertWalkTargetOwner(helper, fixture);

        fixture.owner().setPos(
                fixture.maid().getX() + 1.0D,
                fixture.maid().getY(),
                fixture.maid().getZ()
        );
        helper.assertTrue(
                runtime.tick(fixture.maid(), gameTime + 23L),
                "Owner return did not complete after arriving"
        );
        helper.assertFalse(
                runtime.tick(fixture.maid(), gameTime + 24L),
                "Consumed post-task signal started a second return"
        );
        helper.assertTrue(
                runtime.intents().metrics().activations() == 1L,
                "Post-task signal activated more than once"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void randomStrollSignalMayChooseOwner(
            GameTestHelper helper
    ) {
        Fixture fixture = fixture(helper);
        IntentGameTestRuntime.Runtime runtime =
                IntentGameTestRuntime.create(
                        status(),
                        maid -> {
                        },
                        IntentGameTestRuntime.wanderIntent()
                );
        long gameTime = helper.getLevel().getGameTime();
        WalkTarget previous = MovementCoordinationBridge.capture(
                fixture.maid(),
                gameTime
        );
        BehaviorUtils.setWalkAndLookTargetMemories(
                fixture.maid(),
                fixture.maid().blockPosition().east(3),
                0.3F,
                0
        );
        MovementCoordinationBridge.finishKnownWrite(
                fixture.maid(),
                previous,
                MovementIntentSource.RANDOM_STROLL,
                true,
                gameTime
        );
        helper.assertFalse(
                runtime.tick(fixture.maid(), gameTime),
                "Active random stroll triggered an early owner return"
        );
        fixture.maid().getBrain().eraseMemory(
                MemoryModuleType.WALK_TARGET
        );

        helper.assertTrue(
                runtime.tick(
                        fixture.maid(),
                        gameTime + 1L
                ),
                "Random-stroll edge did not activate owner return"
        );
        assertWalkTargetOwner(helper, fixture);
        helper.succeed();
    }

    private static void assertWalkTargetOwner(
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
                "Owner return did not retain the owner movement target"
        );
    }

    private static Fixture fixture(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        Player owner = helper.makeMockPlayer();
        owner.setPos(GameTestPositions.center(helper, 6, 2, 1));
        TaskAttack task = new TaskAttack();
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }

            @Override
            public TaskAttack getTask() {
                return task;
            }
        };
        maid.setPos(GameTestPositions.center(helper, 1, 2, 1));
        maid.setTame(true);
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        maid.getBrain().setActiveActivityIfPossible(Activity.WORK);
        return new Fixture(owner, maid);
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
