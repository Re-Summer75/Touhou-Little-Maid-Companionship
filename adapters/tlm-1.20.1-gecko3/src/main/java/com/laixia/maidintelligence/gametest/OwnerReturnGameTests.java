package com.laixia.maidintelligence.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskAttack;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.behavior.api.BehaviorTuning;
import com.laixia.maidintelligence.feature.behavior.domain.OwnerReturnPolicy;
import com.laixia.maidintelligence.feature.behavior.tlm.TlmMaidOwnerReturnService;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
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
        TlmMaidOwnerReturnService service = service(1.0D);
        long gameTime = helper.getLevel().getGameTime();
        fixture.maid().getBrain().setMemory(
                InitEntities.TARGET_POS.get(),
                new BlockPosTracker(fixture.maid().blockPosition().east(2))
        );
        helper.assertFalse(
                service.tick(fixture.maid(), gameTime),
                "Active work target triggered an early owner return"
        );

        fixture.maid().getBrain().eraseMemory(
                InitEntities.TARGET_POS.get()
        );
        helper.assertFalse(
                service.tick(fixture.maid(), gameTime + 1L),
                "Work release ignored the settle window"
        );
        helper.assertTrue(
                service.tick(
                        fixture.maid(),
                        gameTime + 1L
                                + OwnerReturnPolicy
                                .DEFAULT_POST_TASK_SETTLE_TICKS
                ),
                "Completed work did not trigger one owner return"
        );
        assertWalkTargetOwner(helper, fixture);

        fixture.maid().getBrain().eraseMemory(
                MemoryModuleType.WALK_TARGET
        );
        helper.assertFalse(
                service.tick(
                        fixture.maid(),
                        gameTime + 2L
                                + OwnerReturnPolicy
                                .DEFAULT_POST_TASK_SETTLE_TICKS
                ),
                "One completed task triggered more than one return"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void randomStrollMayChooseOwner(
            GameTestHelper helper
    ) {
        Fixture fixture = fixture(helper);
        BehaviorUtils.setWalkAndLookTargetMemories(
                fixture.maid(),
                fixture.maid().blockPosition().east(3),
                0.3F,
                0
        );

        boolean returned = service(0.0D).tick(
                fixture.maid(),
                helper.getLevel().getGameTime()
        );

        helper.assertTrue(
                returned,
                "Passing random-stroll roll did not choose the owner"
        );
        assertWalkTargetOwner(helper, fixture);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void randomStrollReturnRespectsChanceAndSitting(
            GameTestHelper helper
    ) {
        Fixture failedRoll = fixture(helper);
        BehaviorUtils.setWalkAndLookTargetMemories(
                failedRoll.maid(),
                failedRoll.maid().blockPosition().east(3),
                0.3F,
                0
        );
        helper.assertFalse(
                service(1.0D).tick(
                        failedRoll.maid(),
                        helper.getLevel().getGameTime()
                ),
                "Failed random-stroll roll still chose the owner"
        );

        Fixture sitting = fixture(helper);
        sitting.maid().setInSittingPose(true);
        BehaviorUtils.setWalkAndLookTargetMemories(
                sitting.maid(),
                sitting.maid().blockPosition().east(3),
                0.3F,
                0
        );
        helper.assertFalse(
                service(0.0D).tick(
                        sitting.maid(),
                        helper.getLevel().getGameTime()
                ),
                "Random-stroll return overrode commanded sitting"
        );
        helper.assertTrue(
                sitting.maid().getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .map(target -> target.getTarget())
                        .filter(BlockPosTracker.class::isInstance)
                        .isPresent(),
                "Rejected owner return replaced the random stroll"
        );
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
                "Owner return did not retain the owner as movement target"
        );
    }

    private static Fixture fixture(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        BlockPos maidPosition = helper.absolutePos(
                new BlockPos(1, 2, 1)
        );
        BlockPos ownerPosition = helper.absolutePos(
                new BlockPos(6, 2, 1)
        );
        Player owner = helper.makeMockPlayer();
        owner.setPos(
                ownerPosition.getX() + 0.5D,
                ownerPosition.getY(),
                ownerPosition.getZ() + 0.5D
        );
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
        maid.setPos(
                maidPosition.getX() + 0.5D,
                maidPosition.getY(),
                maidPosition.getZ() + 0.5D
        );
        maid.setTame(true);
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        maid.getBrain().setActiveActivityIfPossible(Activity.WORK);
        return new Fixture(owner, maid);
    }

    private static TlmMaidOwnerReturnService service(double randomSample) {
        return new TlmMaidOwnerReturnService(
                BehaviorTuning::defaults,
                maid -> randomSample
        );
    }

    private record Fixture(Player owner, EntityMaid maid) {
    }
}
