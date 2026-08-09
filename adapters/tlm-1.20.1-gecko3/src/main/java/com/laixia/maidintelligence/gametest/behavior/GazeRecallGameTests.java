package com.laixia.maidintelligence.gametest.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.behavior.api.MaidGazeRecallApi;
import com.laixia.maidintelligence.feature.behavior.handler.OwnerGazeRecallHandler;
import com.laixia.maidintelligence.feature.behavior.tlm.TlmMaidGazeRecallService;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.tlm.MaidIntentBehavior;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.gametest.support.GameTestPositions;
import com.laixia.maidintelligence.gametest.support.IntentGameTestRuntime;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class GazeRecallGameTests {
    private GazeRecallGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void levelOneGazeRecallApproachesOwner(
            GameTestHelper helper
    ) {
        OwnedFixture fixture = ownedFixture(helper);
        EntityMaid maid = fixture.maid();
        Player owner = fixture.owner();
        maid.setFavorability(64);
        IntentGameTestRuntime.Runtime runtime = runtime();

        boolean recalled = service(runtime).tryRecall(owner, maid);
        runtime.tick(maid, helper.getLevel().getGameTime());

        helper.assertTrue(recalled, "Level-one gaze recall was rejected");
        helper.assertTrue(
                maid.getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .map(target -> target.getTarget())
                        .filter(EntityTracker.class::isInstance)
                        .map(EntityTracker.class::cast)
                        .map(EntityTracker::getEntity)
                        .filter(owner::equals)
                        .isPresent(),
                "Gaze recall did not target the owner"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void gazeRecallRespectsFavorabilityAndCommandedSit(
            GameTestHelper helper
    ) {
        OwnedFixture fixture = ownedFixture(helper);
        EntityMaid maid = fixture.maid();
        Player owner = fixture.owner();
        IntentGameTestRuntime.Runtime runtime = runtime();
        maid.setFavorability(63);
        helper.assertTrue(
                service(runtime).tryRecall(owner, maid),
                "Valid gaze pair did not submit a signal"
        );
        helper.assertFalse(
                runtime.tick(maid, helper.getLevel().getGameTime()),
                "Favorability level zero passed the data guard"
        );

        maid.setFavorability(64);
        maid.setInSittingPose(true);
        service(runtime).tryRecall(owner, maid);
        helper.assertFalse(
                runtime.tick(
                        maid,
                        helper.getLevel().getGameTime() + 1L
                ),
                "Gaze recall overrode commanded sitting"
        );
        helper.assertTrue(
                maid.getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .isEmpty(),
                "Rejected gaze recall wrote a movement target"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void gazeTargetingIgnoresBlockOcclusion(
            GameTestHelper helper
    ) {
        EntityMaid maid = spawnOwnedMaid(helper);
        Player owner = helper.makeMockPlayer();
        owner.setPos(
                maid.getX() + 4.0D,
                maid.getY(),
                maid.getZ()
        );
        maid.setOwnerUUID(owner.getUUID());
        owner.lookAt(
                EntityAnchorArgument.Anchor.EYES,
                maid.getEyePosition()
        );
        Vec3 obstruction = owner.getEyePosition().lerp(
                maid.getEyePosition(),
                0.5D
        );
        helper.getLevel().setBlockAndUpdate(
                BlockPos.containing(obstruction),
                Blocks.STONE.defaultBlockState()
        );

        helper.assertFalse(
                owner.hasLineOfSight(maid),
                "Gaze occlusion fixture did not block normal line of sight"
        );
        helper.assertTrue(
                OwnerGazeRecallHandler.findAimedAtMaid(owner, 8.0D) == maid,
                "Block occlusion prevented gaze targeting"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void intentRuntimeAdvancesOnConsecutiveBrainTicks(
            GameTestHelper helper
    ) {
        OwnedFixture fixture = ownedFixture(helper);
        EntityMaid maid = fixture.maid();
        maid.setFavorability(64);
        IntentGameTestRuntime.Runtime runtime = runtime();
        MaidIntentBehavior behavior = new MaidIntentBehavior(
                runtime.intents()
        );
        long gameTime = helper.getLevel().getGameTime();

        helper.assertTrue(
                behavior.tryStart(helper.getLevel(), maid, gameTime),
                "Intent runtime did not start on the first Brain tick"
        );
        behavior.tickOrStop(helper.getLevel(), maid, gameTime);
        helper.assertTrue(
                behavior.getStatus() == net.minecraft.world.entity.ai.behavior.Behavior.Status.RUNNING,
                "Intent runtime did not remain available for later signals"
        );
        helper.assertTrue(
                service(runtime).tryRecall(fixture.owner(), maid),
                "Gaze signal was rejected between consecutive Brain ticks"
        );
        behavior.tickOrStop(helper.getLevel(), maid, gameTime + 1L);
        helper.assertTrue(
                maid.getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .map(target -> target.getTarget())
                        .filter(EntityTracker.class::isInstance)
                        .map(EntityTracker.class::cast)
                        .map(EntityTracker::getEntity)
                        .filter(fixture.owner()::equals)
                        .isPresent(),
                "Running intent behavior did not process the next-tick signal"
        );
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 40
    )
    public static void registeredGazeSignalRunsThroughMaidBrain(
            GameTestHelper helper
    ) {
        OwnedFixture fixture = ownedFixture(helper);
        for (int x = 6; x <= 8; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), Blocks.STONE);
        }
        fixture.owner().setPos(GameTestPositions.center(helper, 7, 2, 1));
        fixture.maid().setFavorability(64);
        helper.assertTrue(
                productionGazeRecall().tryRecall(
                        fixture.owner(),
                        fixture.maid()
                ),
                "Registered gaze service rejected a valid owner and maid"
        );

        /*
         * Longer than one evaluation interval, not shorter. The orchestrator
         * only looks at a maid every few ticks, so waiting three left it a
         * coin toss whether she had been considered at all — and a test that
         * fails at random teaches everyone to ignore a red run.
         */
        helper.runAfterDelay(15, () -> {
            var trace = productionIntents().inspect(fixture.maid());
            var activeIntent = trace.activeIntent();
            helper.assertTrue(
                    activeIntent != null
                            && "gaze_recall".equals(activeIntent.path()),
                    "Maid Brain did not activate the registered gaze intent: "
                            + trace
                            + "; metrics="
                            + productionIntents().metrics()
            );
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void gazeRecallDefersImportantActivities(
            GameTestHelper helper
    ) {
        OwnedFixture fixture = ownedFixture(helper);
        EntityMaid maid = fixture.maid();
        maid.setFavorability(64);
        long gameTime = helper.getLevel().getGameTime();
        // 本体工作目标那一段断言已删：TARGET_POS 由本体的工作行为写入，而
        // 自由模式不再注册它们，这条路径在自由模式下永远不会发生。留着等于
        // 断言一段不可达的代码。物品使用那一段仍然有效——那是她自己在做的事。
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.setItemInHand(
                InteractionHand.MAIN_HAND,
                new ItemStack(Items.APPLE)
        );
        maid.startUsingItem(InteractionHand.MAIN_HAND);
        helper.assertTrue(
                maid.isUsingItem(),
                "Important item-use fixture did not start"
        );
        helper.assertTrue(
                productionGazeRecall().tryRecall(fixture.owner(), maid),
                "Item-use guard fixture did not submit a gaze signal"
        );
        productionIntents().tick(maid, gameTime + 1L);
        helper.assertTrue(
                productionIntents().inspect(maid).activeIntent() == null,
                "Gaze recall interrupted active item use"
        );
        helper.succeed();
    }

    private static EntityMaid spawnOwnedMaid(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        EntityMaid maid = helper.spawn(
                InitEntities.MAID.get(),
                new BlockPos(1, 2, 1)
        );
        maid.setTame(true);
        maid.setTask(
                TaskManager.findTask(FreedomMaidTask.UID).orElseThrow()
        );
        maid.setHomeModeEnable(false);
        return maid;
    }

    private static OwnedFixture ownedFixture(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        Player owner = helper.makeMockPlayer();
        owner.setPos(GameTestPositions.center(helper, 4, 2, 1));
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(GameTestPositions.center(helper, 1, 2, 1));
        maid.setTame(true);
        maid.setTask(
                TaskManager.findTask(FreedomMaidTask.UID).orElseThrow()
        );
        maid.setHomeModeEnable(false);
        maid.setOwnerUUID(owner.getUUID());
        helper.getLevel().addFreshEntity(maid);
        return new OwnedFixture(owner, maid);
    }

    private static TlmMaidGazeRecallService service(
            IntentGameTestRuntime.Runtime runtime
    ) {
        return new TlmMaidGazeRecallService(runtime.intents());
    }

    private static IntentGameTestRuntime.Runtime runtime() {
        return IntentGameTestRuntime.create(
                status(),
                maid -> {
                },
                IntentGameTestRuntime.gazeIntent()
        );
    }

    @SuppressWarnings("unchecked")
    private static MaidStatusApi<EntityMaid> status() {
        return (MaidStatusApi<EntityMaid>) AdapterRuntime.require(
                MaidStatusApi.class
        );
    }

    @SuppressWarnings("unchecked")
    private static MaidGazeRecallApi<Player, EntityMaid> productionGazeRecall() {
        return (MaidGazeRecallApi<Player, EntityMaid>) AdapterRuntime.require(
                MaidGazeRecallApi.class
        );
    }

    @SuppressWarnings("unchecked")
    private static MaidIntentApi<EntityMaid> productionIntents() {
        return (MaidIntentApi<EntityMaid>) AdapterRuntime.require(
                MaidIntentApi.class
        );
    }

    private record OwnedFixture(Player owner, EntityMaid maid) {
    }
}
