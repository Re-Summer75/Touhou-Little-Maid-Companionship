package com.laixia.maidintelligence.gametest.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
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
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
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

    @GameTest(batch = "intentorchestration", templateNamespace = "minecraft", template = "empty")
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

        // 断言的是"取消时收回自己写的移动"，不再是"战斗把召回挤下去"。
        //
        // 原先的抢占靠移动协调：战斗以更高权威续租，召回的续租被拒，动作返回
        // FAILED，编排器于是取消它。协调层随本体行为一起删掉了——自由模式里
        // 没有别的写入者可抢——中断改由意图优先级完成，而那需要战斗意图也在
        // 场，这个 runtime 只注册了召回。
        //
        // 留下的这一半才是本模组自己的责任，也是真正会出错的那一半：一个动作
        // 被取消却留着移动目标，她就会继续走向一个没人再负责的地方。
        Zombie zombie = helper.spawn(
                EntityType.ZOMBIE,
                new BlockPos(3, 2, 2)
        );
        zombie.setInvulnerable(true);
        fixture.maid().getBrain().setMemory(
                MemoryModuleType.ATTACK_TARGET,
                zombie
        );
        runtime.intents().forget(fixture.maid());
        helper.assertTrue(
                fixture.maid().getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .isEmpty(),
                "取消之后她还留着自己写的移动目标，会继续走向没人负责的地方"
        );
        helper.succeed();
    }

    @GameTest(batch = "intentorchestration", templateNamespace = "minecraft", template = "empty")
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

    @GameTest(batch = "intentorchestration", templateNamespace = "minecraft", template = "empty")
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
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(GameTestPositions.center(helper, maidX, 2, 1));
        maid.setTame(true);
        maid.setTask(
                TaskManager.findTask(FreedomMaidTask.UID).orElseThrow()
        );
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
